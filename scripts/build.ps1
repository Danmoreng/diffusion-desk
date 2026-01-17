# DiffusionDesk Build Script (Step-by-Step)
param (
    [switch]$Clean
)

function Import-VSEnv {
    Write-Host "Loading Visual Studio Environment..."
    $vswhere = Join-Path ${Env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
    if (-not (Test-Path $vswhere)) { return }
    $vsroot = & $vswhere -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath 2>$null
    if (-not $vsroot) { return }
    $vcvars = Join-Path $vsroot 'VC\Auxiliary\Build\vcvars64.bat'
    if (-not (Test-Path $vcvars)) { return }
    $envDump = cmd /c "call `"$vcvars`" > nul && set PATH && set INCLUDE && set LIB"
    $envDump | ForEach-Object {
        if ($_ -match '^([^=]+)=(.*)$') {
            Set-Item -Path "Env:$($matches[1])" -Value $matches[2]
        }
    }
}
Import-VSEnv

$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Definition
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$BuildDir = Join-Path $ProjectRoot "build"

if ($Clean -and (Test-Path $BuildDir)) {
    Write-Host "Cleaning build directory..."
    Remove-Item -Path $BuildDir -Recurse -Force
}

# Check for Ninja
if (Get-Command ninja -ErrorAction SilentlyContinue) {
    Write-Host "Using Ninja generator..."
    $Generator = "-G Ninja"
} else {
    $Generator = ""
}

if (!(Test-Path $BuildDir)) {
    New-Item -ItemType Directory -Path $BuildDir
}
Set-Location $BuildDir

# --- WebUI Build ---
$WebRootDir = Join-Path $ProjectRoot "webui"
if (Test-Path $WebRootDir) {
    Push-Location $WebRootDir
    Write-Host "Installing NPM dependencies..."
    cmd /c "npm install"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "npm install failed!" -ForegroundColor Red
        Pop-Location
        exit 1
    }

    Write-Host "Compiling Vue app..."
    cmd /c "npm run build"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "npm run build failed!" -ForegroundColor Red
        Pop-Location
        exit 1
    }
    Pop-Location
} else {
    Write-Host "WebUI directory not found at $WebRootDir, skipping..." -ForegroundColor Yellow
}

# Configure (Restored & C++17)
Write-Host "Configuring CMake..."
cmake $ProjectRoot $Generator `
    -DSD_CUDA=ON `
    -DGGML_CUDA=ON `
    -DCMAKE_CXX_STANDARD=17 `
    -DCMAKE_BUILD_TYPE=Release `
    -DSD_BUILD_EXTERNAL_GGML=ON

Write-Host "Building Llama.cpp..."
cmake --build . --config Release --target llama --parallel > build_llama.log 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "Llama build failed!" -ForegroundColor Red
    Get-Content build_llama.log -Tail 20
    exit 1
}

Write-Host "Building Stable Diffusion..."
cmake --build . --config Release --target stable-diffusion --parallel > build_sd.log 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "Stable Diffusion build failed!" -ForegroundColor Red
    Get-Content build_sd.log -Tail 20
    exit 1
}

Write-Host "Building Main Server (Orchestrator)..."
cmake --build . --config Release --target diffusion_desk_server --parallel > build_server.log 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "Server build failed!" -ForegroundColor Red
    Get-Content build_server.log -Tail 20
    exit 1
}

Write-Host "Building SD Worker..."
cmake --build . --config Release --target diffusion_desk_sd_worker --parallel > build_sd_worker.log 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "SD Worker build failed!" -ForegroundColor Red
    Get-Content build_sd_worker.log -Tail 20
    exit 1
}

Write-Host "Building LLM Worker..."
cmake --build . --config Release --target diffusion_desk_llm_worker --parallel > build_llm_worker.log 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "LLM Worker build failed!" -ForegroundColor Red
    Get-Content build_llm_worker.log -Tail 20
    exit 1
}

Write-Host "All targets built successfully!" -ForegroundColor Green