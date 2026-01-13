$ErrorActionPreference = "Stop"
$ScriptDir = $PSScriptRoot
$ProjectRoot = Split-Path (Split-Path $ScriptDir)

function Log-Message {
    param([string]$Message)
    $TimeStamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Write-Host "[$TimeStamp] $Message"
}

# 1. Kill any existing instances
Log-Message "Killing existing diffusion_desk_server..."
Get-Process diffusion_desk_server -ErrorAction SilentlyContinue | Stop-Process -Force
Get-Process diffusion_desk_sd_worker -ErrorAction SilentlyContinue | Stop-Process -Force
Get-Process diffusion_desk_llm_worker -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 2

# 2. Start Server
Log-Message "Starting Server on port 5555..."
$ServerExe = Join-Path (Join-Path $ProjectRoot "build") "bin\diffusion_desk_server.exe"
if (!(Test-Path $ServerExe)) { $ServerExe = Join-Path (Join-Path $ProjectRoot "build") "diffusion_desk_server.exe" }
$serverProcess = Start-Process -FilePath $ServerExe -ArgumentList "--listen-port 5555 --verbose --model-dir ""C:\StableDiffusion\Models"" --output-dir ""C:\StableDiffusion\DiffusionDesk\outputs""" -PassThru -NoNewWindow
Start-Sleep -Seconds 5

# 3. Wait for Health
$maxRetries = 30
$retryCount = 0
$serverReady = $false

while ($retryCount -lt $maxRetries) {
    try {
        $response = Invoke-RestMethod -Uri "http://127.0.0.1:5555/health" -Method Get -ErrorAction Stop
        if ($response.status -eq "ok") {
            $serverReady = $true
            break
        }
    } catch {
        Start-Sleep -Seconds 1
    }
    $retryCount++
}

if (-not $serverReady) {
    Log-Message "Server failed to start."
    Stop-Process -Id $serverProcess.Id -Force
    exit 1
}

Log-Message "Server is up."

# 4. Load Model (Warmup)
Log-Message "Loading SD Model..."
$loadBody = @{
    model_id = "stable-diffusion/z_image_turbo-Q8_0.gguf"
    vae = "vae/ae.safetensors"
} | ConvertTo-Json
try {
    Invoke-RestMethod -Uri "http://127.0.0.1:5555/v1/models/load" -Method Post -Body $loadBody -ContentType "application/json" | Out-Null
    Start-Sleep -Seconds 5
} catch {
    Log-Message "Failed to load model: $_"
    Stop-Process -Id $serverProcess.Id -Force
    exit 1
}

# 5. Iteration Loop
$resolutions = @(
    @{w=512; h=512},
    @{w=768; h=768},
    @{w=832; h=1216},
    @{w=1024; h=1024},
    @{w=1152; h=1152},
    @{w=1280; h=1280},
    @{w=1408; h=1408},
    @{w=1536; h=1536}
)

$results = @()

foreach ($res in $resolutions) {
    $w = $res.w
    $h = $res.h
    $mp = ($w * $h) / 1048576.0
    Log-Message "Testing Resolution: ${w}x${h} (${mp} MP)..."

    $genBody = @{
        prompt = "A simple test image"
        width = $w
        height = $h
        sample_steps = 10
        save_image = $false
    } | ConvertTo-Json

    $startTime = Get-Date
    try {
        # Using curl (Invoke-WebRequest) to inspect headers/raw content easier if needed, 
        # but Invoke-RestMethod parses JSON automatically. 
        # We need to capture the server logs to get the Peak VRAM, 
        # OR we modify the server to return it in the response body.
        # Luckily, the current server implementation adds 'vram_peak_gb' to the response JSON (seen in service_controller.cpp)
        
        $response = Invoke-RestMethod -Uri "http://127.0.0.1:5555/v1/images/generations" -Method Post -Body $genBody -ContentType "application/json" -TimeoutSec 300
        
        $peak = 0
        if ($response.vram_peak_gb) { $peak = $response.vram_peak_gb }
        
        Log-Message "Success. Peak VRAM: $peak GB"
        
        $results += [PSCustomObject]@{
            Width = $w
            Height = $h
            MP = $mp
            PeakVRAM = $peak
            Status = "Success"
        }

    } catch {
        Log-Message "Failed: $($_.Exception.Message)"
         $results += [PSCustomObject]@{
            Width = $w
            Height = $h
            MP = $mp
            PeakVRAM = 0
            Status = "Failed"
        }
    }
    
    Start-Sleep -Seconds 2
}

# Test CLIP Reload
Log-Message "Testing CLIP Reload with 512x512..."
$w = 512
$h = 512
$genBody = @{
    prompt = "A simple test image reload"
    width = $w
    height = $h
    sample_steps = 10
    save_image = $false
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "http://127.0.0.1:5555/v1/images/generations" -Method Post -Body $genBody -ContentType "application/json" -TimeoutSec 300
    $peak = 0
    if ($response.vram_peak_gb) { $peak = $response.vram_peak_gb }
    Log-Message "Reload Success. Peak VRAM: $peak GB"
    $results += [PSCustomObject]@{
        Width = $w
        Height = $h
        MP = ($w * $h) / 1048576.0
        PeakVRAM = $peak
        Status = "Reload Check"
    }
} catch {
    Log-Message "Reload Failed: $($_.Exception.Message)"
}

# 6. Output Results
Log-Message "`n--- VRAM CURVE RESULTS ---"
$results | Format-Table -AutoSize

# 7. Cleanup
Log-Message "Test Complete. Stopping processes..."
Stop-Process -Id $serverProcess.Id -Force
Get-Process diffusion_desk_sd_worker -ErrorAction SilentlyContinue | Stop-Process -Force
Get-Process diffusion_desk_llm_worker -ErrorAction SilentlyContinue | Stop-Process -Force
