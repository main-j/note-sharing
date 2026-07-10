# Seed 50 CS demo users with MD/PDF notes for recommendation/training.
# Content via DeepSeek API unless --content-source template.
# API key: DEEPSEEK_API_KEY in scripts/seed/.env or ai_bff/.env
# Prerequisites: MySQL, MinIO, MongoDB running (scripts/backend-deps/start-backend-deps.ps1)

$ErrorActionPreference = "Stop"
$Root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location $Root

Write-Host "Installing seed script dependencies..."
py -3 -m pip install -q -r scripts/seed/requirements.txt

Write-Host "Running seed script..."
py -3 scripts/seed/seed_cs_demo_data.py @args
