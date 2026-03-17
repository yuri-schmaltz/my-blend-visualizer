from __future__ import annotations

import os
import shutil
import subprocess
import uuid
import hmac
import time
from datetime import datetime
from pathlib import Path
from typing import Dict, Optional, Any

from fastapi import FastAPI, File, HTTPException, Request, UploadFile, BackgroundTasks
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

# In-memory status tracking
conversion_tasks: Dict[str, Dict[str, Any]] = {}


class ConversionRequestResponse(BaseModel):
    task_id: str


class ConversionStatusResponse(BaseModel):
    task_id: str
    status: str  # pending, processing, completed, failed
    model_url: Optional[str] = None
    size_bytes: Optional[int] = None
    error: Optional[str] = None
    created_at: str


app = FastAPI(title="Blend Converter API", version="0.1.0")
app.mount("/files", StaticFiles(directory=OUTPUT_DIR), name="files")


@app.get("/health")
def health() -> dict[str, object]:
    blender_path = shutil.which(BLENDER_BIN)
    return {
        "status": "ok",
        "blender_bin": BLENDER_BIN,
        "blender_path": blender_path,
        "blender_available": blender_path is not None,
        "auth_enabled": bool(CONVERTER_API_KEY),
        "active_tasks": len([t for t in conversion_tasks.values() if t["status"] in ("pending", "processing")]),
        "message": (
            "Blender pronto para uso." if blender_path 
            else "Blender nao encontrado! Configure BLENDER_BIN ou instale o blender."
        )
    }


@app.post("/convert", response_model=ConversionRequestResponse)
async def convert(
    request: Request, 
    background_tasks: BackgroundTasks,
    file: UploadFile = File(...)
) -> ConversionRequestResponse:
    require_api_key(request)
    filename = file.filename or ""
    if not filename.lower().endswith(".blend"):
        raise HTTPException(status_code=400, detail="Envie um arquivo com extensao .blend.")

    task_id = uuid.uuid4().hex
    input_path = TMP_DIR / f"{task_id}.blend"
    
    # Pre-save status
    conversion_tasks[task_id] = {
        "status": "pending",
        "created_at": datetime.now().isoformat(),
        "input_path": input_path
    }

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

        # Add background task
        background_tasks.add_task(
            async_conversion_handler, task_id, str(request.base_url)
        )
        
        return ConversionRequestResponse(task_id=task_id)

    except Exception:
        input_path.unlink(missing_ok=True)
        if task_id in conversion_tasks:
            del conversion_tasks[task_id]
        raise


@app.get("/status/{task_id}", response_model=ConversionStatusResponse)
async def get_status(task_id: str) -> ConversionStatusResponse:
    if task_id not in conversion_tasks:
        raise HTTPException(status_code=404, detail="Tarefa nao encontrada.")
    
    task = conversion_tasks[task_id]
    return ConversionStatusResponse(
        task_id=task_id,
        status=task["status"],
        model_url=task.get("model_url"),
        size_bytes=task.get("size_bytes"),
        error=task.get("error"),
        created_at=task["created_at"]
    )


async def async_conversion_handler(task_id: str, base_url: str) -> None:
    task = conversion_tasks[task_id]
    task["status"] = "processing"
    
    input_path = task["input_path"]
    output_name = f"{task_id}.glb"
    output_path = OUTPUT_DIR / output_name

    try:
        await run_in_threadpool(run_blender_conversion, input_path, output_path)

        if not output_path.exists():
            raise RuntimeError("Conversao finalizada sem gerar arquivo .glb.")

        task["status"] = "completed"
        task["model_url"] = f"{base_url.rstrip('/')}/files/{output_name}"
        task["size_bytes"] = output_path.stat().st_size
    except Exception as e:
        task["status"] = "failed"
        task["error"] = str(e)
    finally:
        input_path.unlink(missing_ok=True)


def run_blender_conversion(input_path: Path, output_path: Path) -> None:
    if shutil.which(BLENDER_BIN) is None:
        raise FileNotFoundError(f"Blender nao encontrado em '{BLENDER_BIN}'.")

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
