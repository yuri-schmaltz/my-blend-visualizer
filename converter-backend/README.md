# Blend Converter Backend

API HTTP para converter arquivos `.blend` em `.glb` usando Blender headless.

## Requisitos

- Python 3.11+
- Blender 3.x instalado e acessivel no PATH (ou definir `BLENDER_BIN`)

## Execucao local (Windows PowerShell)

```powershell
cd converter-backend
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
$env:BLENDER_BIN="blender"
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

Opcional (proteger endpoint com API key):

```powershell
$env:CONVERTER_API_KEY="sua-chave-secreta"
```

## Endpoints

- `GET /health`: status da API e disponibilidade do Blender.
- `POST /convert`: recebe multipart com campo `file` (`.blend`) e retorna JSON com `model_url`.
- `GET /files/{arquivo}.glb`: serve o arquivo convertido.

## Variaveis de ambiente

- `BLENDER_BIN` (default: `blender`)
- `MAX_UPLOAD_MB` (default: `200`)
- `CONVERSION_TIMEOUT_SECONDS` (default: `900`)
- `CONVERTER_API_KEY` (default: vazio, autenticacao desativada)

## Testes

```powershell
pip install -r requirements-dev.txt
pytest -q
```
