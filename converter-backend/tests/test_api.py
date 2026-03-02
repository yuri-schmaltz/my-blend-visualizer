from __future__ import annotations

import importlib.util
from pathlib import Path
from urllib.parse import urlparse

from fastapi.testclient import TestClient

PROJECT_ROOT = Path(__file__).resolve().parents[1]
MAIN_PATH = PROJECT_ROOT / "app" / "main.py"

spec = importlib.util.spec_from_file_location("converter_backend_main", MAIN_PATH)
main = importlib.util.module_from_spec(spec)
assert spec is not None and spec.loader is not None
spec.loader.exec_module(main)


def _clear_directory(directory: Path) -> None:
    directory.mkdir(parents=True, exist_ok=True)
    for child in directory.iterdir():
        if child.is_file():
            child.unlink(missing_ok=True)


def _client() -> TestClient:
    return TestClient(main.app)


def test_health_returns_status_and_flags() -> None:
    response = _client().get("/health")

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert "blender_available" in body
    assert "auth_enabled" in body


def test_convert_rejects_non_blend_file() -> None:
    response = _client().post(
        "/convert",
        files={"file": ("notes.txt", b"abc", "text/plain")},
    )

    assert response.status_code == 400
    assert "extensao .blend" in response.json()["detail"]


def test_convert_rejects_empty_file() -> None:
    response = _client().post(
        "/convert",
        files={"file": ("empty.blend", b"", "application/octet-stream")},
    )

    assert response.status_code == 400
    assert "vazio" in response.json()["detail"]


def test_convert_rejects_when_exceeding_upload_limit(monkeypatch) -> None:
    monkeypatch.setattr(main, "MAX_UPLOAD_MB", 0)
    response = _client().post(
        "/convert",
        files={"file": ("scene.blend", b"123", "application/octet-stream")},
    )

    assert response.status_code == 413
    assert "limite" in response.json()["detail"]


def test_convert_success_returns_model_url_and_file(monkeypatch) -> None:
    _clear_directory(main.TMP_DIR)
    _clear_directory(main.OUTPUT_DIR)

    def fake_blender_conversion(input_path: Path, output_path: Path) -> None:
        assert input_path.exists()
        output_path.write_bytes(b"mock-glb-binary")

    monkeypatch.setattr(main, "run_blender_conversion", fake_blender_conversion)

    response = _client().post(
        "/convert",
        files={"file": ("scene.blend", b"blend-bytes", "application/octet-stream")},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["size_bytes"] == len(b"mock-glb-binary")
    assert body["model_url"].startswith("http://testserver/files/")

    url_path = urlparse(body["model_url"]).path
    file_response = _client().get(url_path)
    assert file_response.status_code == 200
    assert file_response.content == b"mock-glb-binary"


def test_convert_requires_api_key_when_enabled(monkeypatch) -> None:
    monkeypatch.setattr(main, "CONVERTER_API_KEY", "segredo-teste")

    response = _client().post(
        "/convert",
        files={"file": ("scene.blend", b"abc", "application/octet-stream")},
    )
    assert response.status_code == 401


def test_convert_accepts_valid_api_key(monkeypatch) -> None:
    _clear_directory(main.TMP_DIR)
    _clear_directory(main.OUTPUT_DIR)
    monkeypatch.setattr(main, "CONVERTER_API_KEY", "segredo-teste")

    def fake_blender_conversion(input_path: Path, output_path: Path) -> None:
        output_path.write_bytes(b"ok")

    monkeypatch.setattr(main, "run_blender_conversion", fake_blender_conversion)

    response = _client().post(
        "/convert",
        headers={"X-API-Key": "segredo-teste"},
        files={"file": ("scene.blend", b"abc", "application/octet-stream")},
    )
    assert response.status_code == 200
