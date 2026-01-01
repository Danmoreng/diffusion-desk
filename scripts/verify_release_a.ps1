# Verification Script for Release A & B0

$ErrorActionPreference = "Stop"
$ScriptDir = $PSScriptRoot
$ProjectRoot = Split-Path $ScriptDir
$RunScript = Join-Path $ScriptDir "run.ps1"

# 1. Start Server
Write-Host "Starting Server..." -ForegroundColor Cyan
$Job = Start-Job -ScriptBlock {
    param($RunScript, $ProjectRoot)
    Set-Location $ProjectRoot
    powershell -ExecutionPolicy Bypass -File $RunScript
} -ArgumentList $RunScript, $ProjectRoot

# 2. Wait for Orchestrator
$OrchestratorUrl = "http://localhost:1234"
$ServerReady = $false
for ($i = 0; $i -lt 30; $i++) {
    try {
        $res = Invoke-RestMethod -Uri "$OrchestratorUrl/health" -Method Get -ErrorAction Stop
        if ($res.status -eq "ok") {
            $ServerReady = $true
            break
        }
    } catch { Start-Sleep -Seconds 1 }
}

if (-not $ServerReady) {
    Write-Host "Server failed to start." -ForegroundColor Red
    Receive-Job $Job
    Stop-Job $Job
    Remove-Job $Job
    exit 1
}

Write-Host "Server is UP." -ForegroundColor Green

# 3. Wait for SD Worker (Model Load)
Write-Host "Waiting for SD Worker (Model Load)..."
$ModelReady = $false
for ($i = 0; $i -lt 120; $i++) {
    try {
        $models = Invoke-RestMethod -Uri "$OrchestratorUrl/v1/models" -Method Get
        $activeModel = $models.data | Where-Object { ($_.type -eq 'stable-diffusion' -or $_.type -eq 'root') -and $_.active -eq $true }
        if ($activeModel) {
            $ModelReady = $true
            Write-Host "Model Loaded: $($activeModel.name)" -ForegroundColor Green
            break
        }
    } catch {}
    Write-Host "." -NoNewline
    Start-Sleep -Seconds 1
}
Write-Host ""

if (-not $ModelReady) {
    Write-Host "SD Worker failed to load model." -ForegroundColor Red
    Stop-Process -Name "mysti_server", "mysti_sd_worker", "mysti_llm_worker" -ErrorAction SilentlyContinue -Force
    Stop-Job $Job
    Remove-Job $Job
    exit 1
}

# 4. Verify Assistant Config (A5)
try {
    $config = Invoke-RestMethod -Uri "$OrchestratorUrl/v1/assistant/config" -Method Get
    if ($config.system_prompt -match "integrated creative assistant") {
        Write-Host "✅ Assistant Config Externalized" -ForegroundColor Green
    } else {
        Write-Host "❌ Assistant Config Mismatch" -ForegroundColor Red
        Write-Host "Got: $($config.system_prompt)"
    }
} catch {
    Write-Host "❌ Failed to fetch Assistant Config" -ForegroundColor Red
    Write-Host $_.Exception.Message
}

# 4. Trigger Generation for Logs (A3)
Write-Host "Triggering generation..."
try {
    $Payload = @{
        prompt = "A test"
        size = "64x64"
        batch_count = 1
    } | ConvertTo-Json
    Invoke-RestMethod -Uri "$OrchestratorUrl/v1/images/generations" -Method Post -Body $Payload -ContentType "application/json" -TimeoutSec 10 2>$null
} catch {
    Write-Host "Generation request completed (status irrelevant for log check)."
}

# 5. Check Logs
Start-Sleep -Seconds 2 # Let logs flush
$LogFile = Join-Path $ProjectRoot "sd_worker.log"
if (Test-Path $LogFile) {
    $Logs = Get-Content $LogFile
    $JsonLogs = $Logs | Where-Object { $_ -match "^{.*}$" }
    
    if ($JsonLogs) {
        Write-Host "✅ Found JSON Logs in SD Worker" -ForegroundColor Green
    } else {
        Write-Host "❌ No JSON Logs found in SD Worker" -ForegroundColor Red
    }

    $ReqIdLogs = $Logs | Where-Object { $_ -match "request_id" }
    if ($ReqIdLogs) {
        Write-Host "✅ Found Request ID in Logs" -ForegroundColor Green
    } else {
        Write-Host "⚠️ No Request ID found (Generation might have failed early)" -ForegroundColor Yellow
    }
} else {
    Write-Host "❌ Log file not found: $LogFile" -ForegroundColor Red
}

# Cleanup
Write-Host "Stopping Server..."
Stop-Process -Name "mysti_server", "mysti_sd_worker", "mysti_llm_worker" -ErrorAction SilentlyContinue -Force
Stop-Job $Job
Remove-Job $Job
