# Test Happy Path: Image Generation
# Automates the verification of the full generation pipeline.

$ErrorActionPreference = "Stop"
$ScriptDir = $PSScriptRoot
$ProjectRoot = Split-Path $ScriptDir
$RunScript = Join-Path $ScriptDir "run.ps1"

Write-Host "Starting Server..." -ForegroundColor Cyan
$Job = Start-Job -ScriptBlock {
    param($RunScript, $ProjectRoot)
    Set-Location $ProjectRoot
    powershell -ExecutionPolicy Bypass -File $RunScript
} -ArgumentList $RunScript, $ProjectRoot

# Wait for Orchestrator
$MaxRetries = 60 # 60 seconds
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
    Write-Host "Orchestrator failed to start." -ForegroundColor Red
    Receive-Job $Job
    Stop-Job $Job
    Remove-Job $Job
    exit 1
}

# Wait for SD Worker to be ready (it starts listening after model load)
# Since the Orchestrator proxies, we can just ping an SD endpoint.
Write-Host "Waiting for SD Worker (Model Load)..."
$ModelReady = $false
for ($i = 0; $i -lt 120; $i++) { # Give it 2 mins for model load
    try {
        $models = Invoke-RestMethod -Uri "$OrchestratorUrl/v1/models" -Method Get -ErrorAction Stop
        # Check if any model is active
        $activeModel = $models.data | Where-Object { $_.active -eq $true }
        if ($activeModel) {
            $ModelReady = $true
            Write-Host "Model Loaded: $($activeModel.name)" -ForegroundColor Green
            break
        } else {
             # If worker is up but no model active yet (unlikely given code, but possible if empty ctx)
             # But if worker is up, it responds.
             Write-Host "Worker responding, waiting for active model..."
        }
    } catch {
        # Worker might not be listening yet
        Write-Host "." -NoNewline
    }
    Start-Sleep -Seconds 1
}
Write-Host ""

if (-not $ModelReady) {
    Write-Host "SD Worker failed to load model." -ForegroundColor Red
    Receive-Job $Job
    Stop-Job $Job
    Remove-Job $Job
    exit 1
}

# Generate Image
Write-Host "Testing Image Generation..." -ForegroundColor Cyan
$Payload = @{
    prompt = "A simple red cube"
    n = 1
    size = "256x256" # Small for speed
    batch_count = 1
} | ConvertTo-Json

try {
    $Response = Invoke-RestMethod -Uri "$OrchestratorUrl/v1/images/generations" -Method Post -Body $Payload -ContentType "application/json" -TimeoutSec 300
    
    if ($Response.data -and $Response.data.Count -eq 1) {
        Write-Host "✅ Image Generation Success!" -ForegroundColor Green
        Write-Host "See output in outputs/ directory."
    } else {
        Write-Host "❌ Image Generation Failed (Invalid Response)." -ForegroundColor Red
        Write-Host ($Response | ConvertTo-Json -Depth 5)
    }
} catch {
    Write-Host "❌ Image Generation Request Failed." -ForegroundColor Red
    Write-Host $_.Exception.Message
}

# Cleanup
Write-Host "Stopping Server..."
Stop-Process -Name "mysti_server", "mysti_sd_worker", "mysti_llm_worker" -ErrorAction SilentlyContinue -Force
Stop-Job $Job
Remove-Job $Job
