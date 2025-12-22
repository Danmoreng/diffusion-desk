# MystiCanvas Launch Script

$ServerExe = "../build/bin/mysti_server.exe"
if (!(Test-Path $ServerExe)) {
    $ServerExe = "../build/mysti_server.exe"
}

if (!(Test-Path $ServerExe)) {
    Write-Host "Error: mysti_server.exe not found. Please run scripts/build.ps1 first." -ForegroundColor Red
    exit 1
}

# Configuration
$ModelDir = "C:/StableDiffusion/models"
$DefaultLLM = "llm/Qwen3-1.7B-Q8_0.gguf"
$IdleTimeout = 600  # 10 minutes

Write-Host "Starting MystiCanvas Server..." -ForegroundColor Cyan
Write-Host "Model Directory: $ModelDir"
Write-Host "Default LLM: $DefaultLLM (Auto-loaded on CPU)"
Write-Host "LLM Idle Timeout: $IdleTimeout seconds"
Write-Host "-------------------------------------------"

# Run from project root
Push-Location ..
& "scripts/$ServerExe" `
    --model-dir $ModelDir `
    --default-llm $DefaultLLM `
    --llm-idle-timeout $IdleTimeout `
    --listen-port 1234 `
    --verbose
Pop-Location
