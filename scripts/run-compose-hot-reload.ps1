# DiffusionDesk Compose Desktop Hot Reload Launch Script
param (
    [string]$JavaHome = "C:\Program Files\JetBrains\WebStorm 2025.3.2\jbr",
    [switch]$ManualReload,
    [switch]$DryRun
)

$ScriptDir = $PSScriptRoot
$ProjectRoot = Split-Path -Parent $ScriptDir
$GradleWrapper = Join-Path $ProjectRoot "gradlew.bat"

if (-not (Test-Path $GradleWrapper)) {
    Write-Host "Error: gradlew.bat not found at $GradleWrapper" -ForegroundColor Red
    exit 1
}

if (-not (Test-Path $JavaHome)) {
    if ($env:JAVA_HOME -and (Test-Path $env:JAVA_HOME)) {
        $JavaHome = $env:JAVA_HOME
    } else {
        Write-Host "Error: Java runtime not found." -ForegroundColor Red
        Write-Host "Expected: $JavaHome"
        Write-Host "Pass a JBR/JDK path with: .\scripts\run-compose-hot-reload.ps1 -JavaHome `"C:\Path\To\jbr`"" -ForegroundColor Yellow
        exit 1
    }
}

$JavaBin = Join-Path $JavaHome "bin"
if (-not (Test-Path (Join-Path $JavaBin "java.exe"))) {
    Write-Host "Error: java.exe not found under $JavaBin" -ForegroundColor Red
    exit 1
}

$env:JAVA_HOME = $JavaHome
$env:PATH = "$JavaBin;$env:PATH"

Write-Host "Starting DiffusionDesk Compose Desktop with Hot Reload..." -ForegroundColor Cyan
Write-Host "Project: $ProjectRoot"
Write-Host "Java: $JavaHome"
Write-Host "-------------------------------------------"

Set-Location $ProjectRoot
$GradleArgs = @(":composeApp:hotRunDesktop")
if (-not $ManualReload) {
    $GradleArgs += "--auto"
}

if ($DryRun) {
    Write-Host "Dry run: would execute $GradleWrapper $($GradleArgs -join ' ')"
    exit 0
}

& $GradleWrapper @GradleArgs
