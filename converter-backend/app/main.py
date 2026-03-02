from __future__ import annotations

import os
import shutil
import subprocess
import uuid
import hmac
from pathlib import Path

from fastapi import FastAPI, File, HTTPException, Request, UploadFile
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel
from starlette.concurrency import run_in_threadpool

PROJECT_ROOT = Path(__file__).resolve().parents[1]
SCRIPT_PATH = Path(__file__).resolve().parent / "export_glb.py"
TMP_DIR = PROJECT_ROOT / "tmp"
OUTPUT_DIR = PROJECT_ROOT / "storage"

BLENDER_BIN = os.getenv("BLENDER_BIN", "blender")
MAX_UPLOAD_MB = int(os.getenv("MAX_UPLOAD_MB", "200"))
CONVERSION_TIMEOUT_SECONDS = int(os.getenv("CONVERSION_TIMEOUT_SECONDS", "900"))
CONVERTER_API_KEY = os.getenv("CONVERTER_API_KEY", "").strip()

TMP_DIR.mkdir(parents=True, exist_ok=True)
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)


class ConversionResponse(BaseModel):
    model_url: str
    size_bytes: int


app = FastAPI(title="Blend Converter API", version="0.1.0")
app.mount("/files", StaticFiles(directory=OUTPUT_DIR), name="files")


@app.get("/health")
def health() -> dict[str, object]:
    return {
        "status": "ok",
        "blender_bin": BLENDER_BIN,
        "blender_available": shutil.which(BLENDER_BIN) is not None,
        "auth_enabled": bool(CONVERTER_API_KEY),
    }


@app.post("/convert", response_model=ConversionResponse)
async def convert(request: Request, file: UploadFile = File(...)) -> ConversionResponse:
    require_api_key(request)
    filename = file.filename or ""
    if not filename.lower().endswith(".blend"):
        raise HTTPException(status_code=400, detail="Envie um arquivo com extensao .blend.")

    run_id = uuid.uuid4().hex
    input_path = TMP_DIR / f"{run_id}.blend"
    output_name = f"{run_id}.glb"
    output_path = OUTPUT_DIR / output_name

    max_upload_bytes = MAX_UPLOAD_MB * 1024 * 1024
    bytes_written = 0

    try:
        with input_path.open("wb") as buffer:
            while True:
                chunk = await file.read(1024 * 1024)
                if not chunk:
                    break
                bytes_written += len(chunk)
                if bytes_written > max_upload_bytes:
                    raise HTTPException(
                        status_code=413,
                        detail=f"Arquivo excede o limite de {MAX_UPLOAD_MB} MB.",
                    )
                buffer.write(chunk)

        if bytes_written == 0:
            raise HTTPException(status_code=400, detail="Arquivo vazio.")

        await run_in_threadpool(run_blender_conversion, input_path, output_path)

        if not output_path.exists():
            raise HTTPException(
                status_code=500,
                detail="Conversao finalizada sem gerar arquivo .glb.",
            )

        model_url = f"{str(request.base_url).rstrip('/')}/files/{output_name}"
        return ConversionResponse(
            model_url=model_url,
            size_bytes=output_path.stat().st_size,
        )
    except HTTPException:
        output_path.unlink(missing_ok=True)
        raise
    except FileNotFoundError:
        output_path.unlink(missing_ok=True)
        raise HTTPException(
            status_code=500,
            detail=f"Blender nao encontrado em '{BLENDER_BIN}'.",
        )
    except subprocess.TimeoutExpired:
        output_path.unlink(missing_ok=True)
        raise HTTPException(
            status_code=504,
            detail=f"Conversao excedeu {CONVERSION_TIMEOUT_SECONDS} segundos.",
        )
    except Exception as exception:
        output_path.unlink(missing_ok=True)
        detail = str(exception).strip() or "Erro inesperado durante a conversao."
        raise HTTPException(status_code=500, detail=detail[:1000])
    finally:
        await file.close()
        input_path.unlink(missing_ok=True)


def run_blender_conversion(input_path: Path, output_path: Path) -> None:
    command = [
        BLENDER_BIN,
        "-b",
        str(input_path),
        "--python",
        str(SCRIPT_PATH),
        "--",
        "--output",
        str(output_path),
    ]
    process = subprocess.run(
        command,
        capture_output=True,
        text=True,
        timeout=CONVERSION_TIMEOUT_SECONDS,
        check=False,
    )
    if process.returncode != 0:
        stderr = (process.stderr or process.stdout or "").strip()
        raise RuntimeError(f"Falha no Blender: {stderr[-800:]}")


def require_api_key(request: Request) -> None:
    if not CONVERTER_API_KEY:
        return
    api_key = request.headers.get("X-API-Key", "")
    if not hmac.compare_digest(api_key, CONVERTER_API_KEY):
        raise HTTPException(status_code=401, detail="API key invalida.")
