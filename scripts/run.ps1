# MystiCanvas Launch Script

# Robustly resolve paths relative to the script location
$ScriptDir = $PSScriptRoot
$ProjectRoot = Split-Path -Parent $ScriptDir
$BuildDir = Join-Path $ProjectRoot "build"

# Define possible executable locations
$PotentialPaths = @(
    "$BuildDir\bin\mysti_server.exe",
    "$BuildDir\mysti_server.exe",
    "$BuildDir\bin\Debug\mysti_server.exe",
    "$BuildDir\Debug\mysti_server.exe"
)

$ServerExe = $null
foreach ($path in $PotentialPaths) {
    if (Test-Path $path) {
        $ServerExe = $path
        break
    }
}

if ($null -eq $ServerExe) {
    Write-Host "Error: mysti_server.exe not found in build directories." -ForegroundColor Red
    Write-Host "Searched locations:"
    $PotentialPaths | ForEach-Object { Write-Host " - $_" }
    Write-Host "Please run scripts/build.ps1 first." -ForegroundColor Yellow
    exit 1
}

# Configuration
$ModelBase = "C:\StableDiffusion\Models"
# Optional: Hardcode these if you want to override the last used preset on startup
$LLMPath = ""
$SDPath = "" 

$IdleTimeout = 600  # 10 minutes

Write-Host "Starting MystiCanvas Server..." -ForegroundColor Cyan
Write-Host "Executable: $ServerExe"
Write-Host "Model Directory: $ModelBase"
if ($SDPath) { Write-Host "SD Model Override: $SDPath" }
else { Write-Host "SD Model: (Restoring Last Preset)" }
if ($LLMPath) { Write-Host "LLM Model Override: $LLMPath" }
else { Write-Host "LLM Model: (Restoring Last Preset)" }
Write-Host "-------------------------------------------"

# Execute
# We don't need Push-Location if we use absolute path to exe, 
# BUT the server might expect CWD to be project root for assets (./public).
Set-Location $ProjectRoot
$Args = @("--model-dir", "$ModelBase", "--llm-idle-timeout", $IdleTimeout, "--listen-port", 1234, "--verbose")
if ($SDPath) { $Args += "--diffusion-model"; $Args += "$SDPath" }
if ($LLMPath) { $Args += "--llm-model"; $Args += "$LLMPath" }

& $ServerExe $Args
