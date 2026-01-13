# Test LLM Worker: Chat Completion
# Automates the verification of the LLM generation pipeline on Windows.

$ErrorActionPreference = "Stop"
$ScriptDir = $PSScriptRoot
$ProjectRoot = Split-Path (Split-Path $ScriptDir)

# Define paths
$ServerExe = Join-Path (Join-Path $ProjectRoot "build") "bin\diffusion_desk_server.exe"
if (!(Test-Path $ServerExe)) {
    $ServerExe = Join-Path (Join-Path $ProjectRoot "build") "diffusion_desk_server.exe" 
}
if (!(Test-Path $ServerExe)) {
    Write-Host "Server executable not found at $ServerExe" -ForegroundColor Red
    exit 1
}

$ModelBase = "C:\Development\models" # Adjust as needed for Windows env, or use env var
# Try to find a valid model path if hardcoded one doesn't exist, similar to run.ps1 logic
if (!(Test-Path $ModelBase)) {
    $ModelBase = Join-Path $ProjectRoot "models"
}

$LLMPath = Join-Path $ModelBase "llm\Ministral-3-3B-Instruct-2512-Q8_0.gguf"

Write-Host "Starting Server (LLM Only)..." -ForegroundColor Cyan
Write-Host "Exe: $ServerExe"
Write-Host "Model: $LLMPath"

$ServerLog = Join-Path $ProjectRoot "server_test.log"

$Job = Start-Job -ScriptBlock {
    param($ServerExe, $ModelBase, $LLMPath, $ServerLog)
    # Redirect output to file
    & $ServerExe --model-dir "$ModelBase" --llm-model "$LLMPath" --listen-port 1234 --verbose > $ServerLog 2>&1
} -ArgumentList $ServerExe, $ModelBase, $LLMPath, $ServerLog

# Wait for Orchestrator
$MaxRetries = 60
$OrchestratorUrl = "http://localhost:1234"
$ServerReady = $false

Write-Host "Waiting for Orchestrator on $OrchestratorUrl..."
for ($i = 0; $i -lt $MaxRetries; $i++) {
    try {
        $health = Invoke-RestMethod -Uri "$OrchestratorUrl/health" -Method Get -ErrorAction Stop
        if ($health.status -eq "ok") {
            $ServerReady = $true
            Write-Host "Orchestrator is UP." -ForegroundColor Green
            break
        }
    } catch {
        Start-Sleep -Seconds 1
    }
}

if (-not $ServerReady) {
    Write-Host "Orchestrator failed to start. Check $ServerLog" -ForegroundColor Red
    Get-Content $ServerLog -Tail 20
    Stop-Job $Job
    Remove-Job $Job
    exit 1
}

# Wait for LLM Worker (Model Load)
Write-Host "Waiting for LLM Worker (Model Load)..."
$ModelReady = $false
for ($i = 0; $i -lt 120; $i++) {
    try {
        $models = Invoke-RestMethod -Uri "$OrchestratorUrl/v1/models" -Method Get -ErrorAction Stop
        # Check for Ministral/LLM active
        $activeModel = $models.data | Where-Object { $_.active -eq $true -and $_.id -match "Ministral" }
        if ($activeModel) {
            $ModelReady = $true
            Write-Host "LLM Model Loaded: $($activeModel.id)" -ForegroundColor Green
            break
        }
    } catch {
        Write-Host "." -NoNewline
    }
    Start-Sleep -Seconds 1
}
Write-Host ""

if (-not $ModelReady) {
    Write-Host "LLM Worker failed to load model." -ForegroundColor Red
    Get-Content $ServerLog -Tail 20
    Stop-Job $Job
    Remove-Job $Job
    exit 1
}

# Generate Text
Write-Host "Testing Chat Completion..." -ForegroundColor Cyan
$Payload = @{
    model = "gpt-3.5-turbo"
    messages = @(
        @{ role = "user"; content = "What is 2+2? Answer briefly." }
    )
    temperature = 0.7
} | ConvertTo-Json -Depth 5

try {
    $Response = Invoke-RestMethod -Uri "$OrchestratorUrl/v1/chat/completions" -Method Post -Body $Payload -ContentType "application/json" -TimeoutSec 300
    
    if ($Response.choices) {
        Write-Host "✅ Chat Completion Success!" -ForegroundColor Green
        Write-Host ($Response | ConvertTo-Json -Depth 5)
    } else {
        Write-Host "❌ Chat Completion Failed (Invalid Response)." -ForegroundColor Red
        Write-Host ($Response | ConvertTo-Json -Depth 5)
        exit 1
    }
} catch {
    Write-Host "❌ Chat Completion Request Failed." -ForegroundColor Red
    Write-Host $_.Exception.Message
    exit 1
} finally {
    Write-Host "Stopping Server..."
    Stop-Job $Job
    Remove-Job $Job
    Stop-Process -Name "diffusion_desk_server", "diffusion_desk_sd_worker", "diffusion_desk_llm_worker" -ErrorAction SilentlyContinue -Force
}
