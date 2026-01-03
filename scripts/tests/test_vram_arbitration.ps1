# Test Script for VRAM Arbitration Logic
# Uses the paths defined in scripts/run.ps1 logic

$ErrorActionPreference = "Stop"
$ScriptDir = $PSScriptRoot
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $ScriptDir)
$BuildDir = Join-Path $ProjectRoot "build"

# Locate Executable
$PotentialPaths = @(
    "$BuildDir\bin\mysti_server.exe",
    "$BuildDir\mysti_server.exe",
    "$BuildDir\bin\Debug\mysti_server.exe",
    "$BuildDir\Debug\mysti_server.exe",
    "$BuildDir\bin\Release\mysti_server.exe",
    "$BuildDir\Release\mysti_server.exe"
)

$ServerExe = $null
foreach ($path in $PotentialPaths) {
    if (Test-Path $path) {
        $ServerExe = $path
        break
    }
}

if ($null -eq $ServerExe) {
    Write-Host "Error: mysti_server.exe not found." -ForegroundColor Red
    exit 1
}

# Configuration - Matches run.ps1 default paths
$ModelBase = "C:\StableDiffusion\Models"
$LLMPath = "llm\Qwen3-1.7B-Q8_0.gguf" # Relative to ModelBase/llm usually, but run.ps1 had absolute. 
# run.ps1 had: $LLMPath = "$ModelBase\llm\Qwen3-1.7B-Q8_0.gguf"
# The server expects relative paths usually if inside model_dir, OR absolute paths.
# Let's use the absolute paths from run.ps1 for safety.
$AbsLLMPath = "$ModelBase\llm\Qwen3-1.7B-Q8_0.gguf"
$AbsSDPath = "$ModelBase\stable-diffusion\z_image_turbo-Q8_0.gguf"

$Port = 5555
$Url = "http://127.0.0.1:$Port"
$LogOut = "$ScriptDir\test_vram_server.out.log"
$LogErr = "$ScriptDir\test_vram_server.err.log"

# Cleanup previous run
function Kill-Processes {
    $Names = @("mysti_server", "mysti_sd_worker", "mysti_llm_worker")
    foreach ($Name in $Names) {
        $Existing = Get-Process -Name $Name -ErrorAction SilentlyContinue
        if ($Existing) {
            Write-Host "Killing existing $Name..."
            Stop-Process -InputObject $Existing -Force
        }
    }
}

Kill-Processes
Start-Sleep -Seconds 2

if (Test-Path $LogOut) { Remove-Item $LogOut -ErrorAction SilentlyContinue }
if (Test-Path $LogErr) { Remove-Item $LogErr -ErrorAction SilentlyContinue }
if (Test-Path "$ProjectRoot\sd_worker.log") { Remove-Item "$ProjectRoot\sd_worker.log" -ErrorAction SilentlyContinue }
if (Test-Path "$ProjectRoot\llm_worker.log") { Remove-Item "$ProjectRoot\llm_worker.log" -ErrorAction SilentlyContinue }

# Start Server
Write-Host "Starting Server on port $Port..."
$ProcArgs = @(
    "--model-dir", "$ModelBase",
    "--diffusion-model", "$AbsSDPath",
    "--llm-model", "$AbsLLMPath",
    "--listen-port", "$Port",
    "--verbose"
)

$Process = Start-Process -FilePath $ServerExe -ArgumentList $ProcArgs -RedirectStandardOutput $LogOut -RedirectStandardError $LogErr -PassThru -NoNewWindow

# Wait for startup
Write-Host "Waiting for server health..."
$Retries = 30
$Healthy = $false
while ($Retries -gt 0) {
    try {
        $resp = Invoke-RestMethod -Uri "$Url/health" -Method Get -ErrorAction Stop
        if ($resp.status -eq "ok") {
            $Healthy = $true
            break
        }
    } catch {
        Start-Sleep -Seconds 1
        $Retries--
    }
}

if (-not $Healthy) {
    Write-Host "Server failed to start." -ForegroundColor Red
    if (Test-Path $LogOut) { Get-Content $LogOut -Tail 20 }
    if (Test-Path $LogErr) { Get-Content $LogErr -Tail 20 }
    if ($Process -and -not $Process.HasExited) { Stop-Process -InputObject $Process -Force }
    exit 1
}

Write-Host "Server is up."

# Helper to check logs
function Check-LogFor ($Pattern, $Name) {
    $Found = $false
    
    # Force flush/read by getting content freshly
    if (Test-Path $LogOut) {
        $Content = Get-Content $LogOut -ReadCount 0
        if ($Content -match $Pattern) { $Found = $true }
    }
    if (-not $Found -and (Test-Path $LogErr)) {
        $ContentErr = Get-Content $LogErr -ReadCount 0
        if ($ContentErr -match $Pattern) { $Found = $true }
    }
    
    if ($Found) {
        Write-Host "[PASS] $Name detected." -ForegroundColor Green
        return $true
    }
    Write-Host "[FAIL] $Name NOT detected." -ForegroundColor Red
    return $false
}

function Generate-Image ($Width, $Height, $Name) {
    Write-Host "Requesting Generation: $Name (${Width}x${Height})..."
    $Body = @{
        prompt = "test image"
        width = $Width
        height = $Height
        sample_steps = 1 # Fast generation
        n = 1
        no_base64 = $true # Use our new feature to keep response clean
    } | ConvertTo-Json

    try {
        # Using long timeout because large gens take time
        $resp = Invoke-RestMethod -Uri "$Url/v1/images/generations" -Method Post -Body $Body -ContentType "application/json" -TimeoutSec 300
        Write-Host "Generation '$Name' completed successfully." -ForegroundColor Green
        return $true
    } catch {
        Write-Host "Generation failed: $_" -ForegroundColor Red
        return $false
    }
}

# 0. Wakeup / Warmup
Write-Host "Warming up worker connection..."
$WarmupRetries = 10
while ($WarmupRetries -gt 0) {
    if (Generate-Image 64 64 "Warmup") {
        break
    }
    Write-Host "Worker not ready, retrying..."
    Start-Sleep -Seconds 2
    $WarmupRetries--
}

# 1. Baseline: Small Generation
Generate-Image 512 512 "Baseline (Small)"
Start-Sleep -Seconds 2

# 2. Large Generation
Write-Host "Checking Medium/Large Load..."
Generate-Image 1280 1280 "Large (Potential LLM Unload)" # ~1.6MP
Start-Sleep -Seconds 5

# 3. Very Large Generation -> Should Trigger CLIP Offload / VAE Tiling / LLM Unload
Write-Host "Checking Huge Load..."
Generate-Image 1408 1408 "Huge (Trigger Offload/Tiling/Unload)" 
Start-Sleep -Seconds 5

# Final Checks
Write-Host "`n--- Final Verification ---"
Check-LogFor "Requesting LLM unload" "LLM Unload (At any stage)"
Check-LogFor "Recommending CLIP offload" "CLIP Offload"
Check-LogFor "Recommending VAE tiling" "VAE Tiling"

# Cleanup
Write-Host "Test Complete. Stopping processes..."
Kill-Processes

# Cleanup
Write-Host "Test Complete. Stopping processes..."
Kill-Processes