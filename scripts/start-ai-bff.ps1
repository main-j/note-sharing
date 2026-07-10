param(
    [int]$Port = 8003,
    [switch]$InstallDeps
)

$ErrorActionPreference = "Stop"
$Root = Split-Path $PSScriptRoot -Parent
$BffDir = Join-Path $Root "ai_bff"
$EnvFile = Join-Path $BffDir ".env"
$EnvExample = Join-Path $BffDir ".env.example"

if (-not (Test-Path $EnvFile)) {
    if (Test-Path $EnvExample) {
        Copy-Item $EnvExample $EnvFile
        Write-Warning "Created ai_bff/.env from .env.example — set AI_MODEL_API_KEY and AI_JWT_SECRET before use."
    } else {
        throw "Missing ai_bff/.env"
    }
}

Push-Location $BffDir
try {
    if ($InstallDeps -or -not (Test-Path ".venv")) {
        Write-Host "Creating venv and installing dependencies..."
        py -3 -m venv .venv
        & ".venv\Scripts\python.exe" -m pip install -r requirements.txt
    }

    $Python = Join-Path $BffDir ".venv\Scripts\python.exe"
    if (-not (Test-Path $Python)) {
        $Python = "py"
        $PythonArgs = @("-3", "-m", "uvicorn", "app.main:app", "--reload", "--host", "0.0.0.0", "--port", $Port)
    } else {
        $PythonArgs = @("-m", "uvicorn", "app.main:app", "--reload", "--host", "0.0.0.0", "--port", $Port)
    }

    Write-Host "Starting FOLIO AI BFF on http://localhost:$Port"
    Write-Host "Health: http://localhost:$Port/health"
    Write-Host "Shell:  http://localhost:$Port/shell"
    & $Python @PythonArgs
} finally {
    Pop-Location
}
