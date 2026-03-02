# My Blend Visualizer

App Android (Kotlin + Compose) para selecionar arquivos `.blend`, enviar para conversao e receber URL de `.glb`.

## O que ja esta implementado

- Seletor de arquivo `.blend` (Storage Access Framework).
- Validacao de extensao e exibicao de metadados.
- Upload multipart do arquivo para backend.
- Backend FastAPI que chama Blender headless e exporta para `.glb`.
- Retorno de `model_url` para download/consumo posterior.
- Visualizacao 3D nativa no app via `SceneView` (Filament).

## Arquitetura

1. Android seleciona um `.blend`.
2. `HttpBlendConversionGateway` faz upload para `POST /convert`.
3. Backend roda Blender em modo background e exporta GLB.
4. Backend devolve URL em `/files/{id}.glb`.

## Como rodar o backend

Detalhes em [converter-backend/README.md](converter-backend/README.md).

Resumo (PowerShell):

```powershell
cd converter-backend
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
$env:BLENDER_BIN="blender"
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

## Como configurar o app Android

- URL default no debug: `http://10.0.2.2:8000` (emulador Android -> host local).
- O app usa retry automatico para falhas de rede e erros 5xx.
- Para trocar URL e API key:

```powershell
.\gradlew.bat assembleDebug -PconverterBaseUrl=http://SEU_HOST:8000 -PconverterApiKey=SUA_CHAVE
```

## Limites atuais

- O backend nao faz fila/retentativa; conversoes concorrentes ainda sao basicas.
- Falta observabilidade de producao (logs estruturados e metricas).

## Validacao automatizada

- Backend: `converter-backend/tests/test_api.py` (executado com `pytest`, 7 testes).
- Android: testes unitarios adicionados em `app/src/test/java/com/myblendvisualizer/ui/BlendViewerViewModelTest.kt`.
- Checklist operacional: `CHECKLIST_VALIDACAO.md`.
