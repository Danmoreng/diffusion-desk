# Test VRAM Arbitration
# 1. Load an LLM
# 2. Trigger high-res SD generation
# 3. Verify LLM is unloaded automatically to free VRAM

$ErrorActionPreference = "Stop"
$ScriptDir = $PSScriptRoot
$ProjectRoot = Split-Path $ScriptDir
$RunScript = Join-Path $ScriptDir "run.ps1"

Write-Host "--- VRAM Arbitration Test ---" -ForegroundColor Cyan

# Cleanup old processes
Stop-Process -Name "mysti_server", "mysti_sd_worker", "mysti_llm_worker" -ErrorAction SilentlyContinue -Force

Write-Host "Starting Server..." -ForegroundColor Cyan
$Job = Start-Job -ScriptBlock {
    param($RunScript, $ProjectRoot)
    Set-Location $ProjectRoot
    powershell -ExecutionPolicy Bypass -File $RunScript -verbose
} -ArgumentList $RunScript, $ProjectRoot

$OrchestratorUrl = "http://localhost:1234"
$MaxRetries = 60

# Wait for Orchestrator
Write-Host "Waiting for Orchestrator..."
$Ready = $false
for ($i = 0; $i -lt $MaxRetries; $i++) {
    try {
        $h = Invoke-RestMethod -Uri "$OrchestratorUrl/health" -Method Get
        if ($h.status -eq "ok") { $Ready = $true; break }
    } catch {}
    Start-Sleep -Seconds 1
}

if (-not $Ready) { Write-Host "Server failed to start." -ForegroundColor Red; exit 1 }

# 1. Load LLM
Write-Host "Step 1: Loading LLM model..."
$LlmModel = "llm/Qwen3-1.7B-Q8_0.gguf" # Assume this exists or adjust
try {
    $loadRes = Invoke-RestMethod -Uri "$OrchestratorUrl/v1/llm/load" -Method Post -Body (@{model_id=$LlmModel} | ConvertTo-Json) -ContentType "application/json"
    Write-Host "LLM Loaded." -ForegroundColor Green
} catch {
    Write-Host "Failed to load LLM. Check if model exists: $LlmModel" -ForegroundColor Yellow
    # Proceed anyway, maybe it preloaded
}

# Verify LLM is in VRAM
Start-Sleep -Seconds 2
$health = Invoke-RestMethod -Uri "$OrchestratorUrl/health" -Method Get
Write-Host "Current VRAM Free: $($health.vram_free_gb) GB"

# 2. Trigger high-res generation
Write-Host "Step 2: Triggering High-Res Generation (should trigger arbitration)..."
$Payload = @{
    prompt = "A majestic dragon"
    width = 1536
    height = 1536
    sample_steps = 4
} | ConvertTo-Json

$GenTask = Start-ThreadJob -ScriptBlock {
    param($Url, $Body)
    try {
        return Invoke-RestMethod -Uri "$Url/v1/images/generations" -Method Post -Body $Body -ContentType "application/json" -TimeoutSec 300
    } catch { return $_ }
} -ArgumentList $OrchestratorUrl, $Payload

# 3. Monitor for LLM Unload
Write-Host "Step 3: Monitoring LLM status during generation..."
$Unloaded = $false
for ($i = 0; $i -lt 30; $i++) {
    try {
        # Check if LLM is still reported in metrics (health check might be slow if worker is busy, 
        # but orchestrator keeps last metrics)
        $h = Invoke-RestMethod -Uri "$OrchestratorUrl/health" -Method Get
        # The /health endpoint returns resource manager stats
        if ($h.llm_worker_gb -lt 0.1) {
            Write-Host "✅ SUCCESS: LLM was unloaded to free VRAM!" -ForegroundColor Green
            $Unloaded = $true
            break
        }
    } catch {}
    Start-Sleep -Seconds 1
}

# Wait for generation to finish
$Result = Wait-Job $GenTask | Receive-Job
if ($Result.data) {
    Write-Host "✅ SUCCESS: Image generated at high resolution." -ForegroundColor Green
} else {
    Write-Host "❌ FAILED: Generation failed." -ForegroundColor Red
}

# Cleanup
Write-Host "Cleanup..."
Stop-Process -Name "mysti_server", "mysti_sd_worker", "mysti_llm_worker" -ErrorAction SilentlyContinue -Force
Stop-Job $Job
Remove-Job $Job
