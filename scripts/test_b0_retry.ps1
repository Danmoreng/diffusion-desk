# B0 Verification Script: Conservative Retry
# Verifies that the system detects failure and retries with conservative settings.

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

# 2. Wait for SD Worker (Model Load)
$OrchestratorUrl = "http://localhost:1234"
Write-Host "Waiting for SD Worker (Model Load)..."
$ModelReady = $false
for ($i = 0; $i -lt 120; $i++) {
    try {
        $models = Invoke-RestMethod -Uri "$OrchestratorUrl/v1/models" -Method Get
        $activeModel = $models.data | Where-Object { ($_.type -eq 'stable-diffusion' -or $_.type -eq 'root') -and $_.active -eq $true }
        if ($activeModel -and $activeModel.loaded -eq $true) {
            $ModelReady = $true
            Write-Host "Model Fully Loaded: $($activeModel.name)" -ForegroundColor Green
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

# 3. Trigger Mock Failure Generation
Write-Host "Triggering generation with mock failure..." -ForegroundColor Cyan
$Payload = @{
    prompt = "A red cube"
    size = "256x256"
    batch_count = 1
    __test_mock_fail = $true
} | ConvertTo-Json

try {
    $Response = Invoke-RestMethod -Uri "$OrchestratorUrl/v1/images/generations" -Method Post -Body $Payload -ContentType "application/json" -TimeoutSec 300
    
    if ($Response.data -and $Response.data.Count -eq 1) {
        Write-Host "✅ Request returned SUCCESS despite first-pass failure (Retry worked!)" -ForegroundColor Green
    } else {
        Write-Host "❌ Request failed." -ForegroundColor Red
    }
} catch {
    Write-Host "❌ Request crashed." -ForegroundColor Red
    Write-Host $_.Exception.Message
}

# 4. Verify Logs
Start-Sleep -Seconds 2
$LogFile = Join-Path $ProjectRoot "sd_worker.log"
if (Test-Path $LogFile) {
    $Logs = Get-Content $LogFile -Tail 200
    
    $MockFound = $false
    $RetryFound = $false

    foreach ($line in $Logs) {
        if ($line -match "^{.*}$") {
            try {
                $j = $line | ConvertFrom-Json
                if ($j.message -like "*Mocking first pass failure*") { $MockFound = $true }
                if ($j.message -like "*Retrying with VAE tiling*") { $RetryFound = $true }
            } catch {}
        }
    }

    if ($MockFound) {
        Write-Host "✅ Verified: First pass was mocked to fail (JSON Log)." -ForegroundColor Green
    } else {
        Write-Host "❌ Logs do not show mock failure message." -ForegroundColor Red
    }

    if ($RetryFound) {
        Write-Host "✅ Verified: System triggered retry with VAE tiling (JSON Log)." -ForegroundColor Green
    } else {
        Write-Host "❌ Logs do not show retry message." -ForegroundColor Red
    }
}

# Cleanup
Write-Host "Stopping Server..."
Stop-Process -Name "mysti_server", "mysti_sd_worker", "mysti_llm_worker" -ErrorAction SilentlyContinue -Force
Stop-Job $Job
Remove-Job $Job
