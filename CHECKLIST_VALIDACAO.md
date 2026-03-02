# Checklist de Validacao

## 1) Pre-requisitos locais

- [ ] JDK 17 instalado.
- [ ] `JAVA_HOME` configurado.
- [ ] Android Studio instalado com SDK/Emulator Android API 34.
- [ ] Python 3.11+ instalado.
- [ ] Blender 3.x instalado e acessivel no PATH (`blender --version`).

## 2) Backend

No PowerShell:

```powershell
cd converter-backend
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements-dev.txt
pytest -q
$env:BLENDER_BIN="blender"
# Opcional:
# $env:CONVERTER_API_KEY="sua-chave-secreta"
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

Validacoes esperadas:

- [ ] `pytest -q` com todos os testes passando.
- [ ] `GET http://127.0.0.1:8000/health` retornando `status: ok`.
- [ ] `blender_available: true` no `/health`.

## 3) App Android (build)

No PowerShell na raiz do repo:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug -PconverterBaseUrl=http://10.0.2.2:8000
```

Se usar API key no backend:

```powershell
.\gradlew.bat assembleDebug -PconverterBaseUrl=http://10.0.2.2:8000 -PconverterApiKey=SUA_CHAVE
```

Validacoes esperadas:

- [ ] Testes unitarios Android passando.
- [ ] APK debug gerado sem erros.

## 4) Teste ponta a ponta no emulador

- [ ] Abrir app no emulador.
- [ ] Selecionar um arquivo `.blend` valido.
- [ ] Converter com sucesso (status mostrando URL de preview).
- [ ] Abrir visualizacao no app (`SceneView`) e confirmar interacao 3D.
- [ ] Testar botao "Abrir externo" como fallback.

## 5) Testes negativos

- [ ] Enviar arquivo nao `.blend` e validar erro amigavel.
- [ ] Simular backend offline e validar mensagem de erro de rede.
- [ ] Com API key ativa, testar chave invalida e confirmar erro 401.

## 6) Liberacao

- [ ] Revisar logs do backend durante conversao.
- [ ] Confirmar que nenhum arquivo de cache (`__pycache__`, `.pyc`) esta versionado.
- [ ] Tag/versao definida para o release.
