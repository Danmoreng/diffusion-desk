# DiffusionDesk Compose Desktop Hot Reload Launch Script
param (
    [string]$JavaHome = "",
    [switch]$ManualReload,
    [switch]$DryRun
)

$ScriptDir = $PSScriptRoot
$ProjectRoot = Split-Path -Parent $ScriptDir
$GradleWrapper = Join-Path $ProjectRoot "gradlew.bat"
$RequiredJavaMajor = 25

function Get-JavaMajorVersion([string]$JavaHomePath) {
    if ([string]::IsNullOrWhiteSpace($JavaHomePath)) { return $null }

    $releaseFile = Join-Path $JavaHomePath "release"
    if (Test-Path $releaseFile) {
        $versionLine = Get-Content $releaseFile | Where-Object { $_ -match '^JAVA_VERSION="([0-9]+)' } | Select-Object -First 1
        if ($versionLine -and $matches[1]) {
            return [int]$matches[1]
        }
    }

    $javaExe = Join-Path $JavaHomePath "bin\java.exe"
    if (Test-Path $javaExe) {
        $versionOutput = & $javaExe -version 2>&1 | Select-Object -First 1
        if ($versionOutput -match 'version "([0-9]+)') {
            return [int]$matches[1]
        }
    }

    return $null
}

function Test-JavaHome([string]$JavaHomePath) {
    if ([string]::IsNullOrWhiteSpace($JavaHomePath)) { return $false }
    if (-not (Test-Path (Join-Path $JavaHomePath "bin\java.exe"))) { return $false }
    $major = Get-JavaMajorVersion $JavaHomePath
    return $major -and $major -ge $RequiredJavaMajor
}

function Resolve-JavaHome([string]$RequestedJavaHome) {
    if (Test-JavaHome $RequestedJavaHome) {
        return $RequestedJavaHome
    }

    if (Test-JavaHome $env:JAVA_HOME) {
        return $env:JAVA_HOME
    }

    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCmd -and $javaCmd.Path) {
        $javaHomeFromPath = Split-Path -Parent (Split-Path -Parent $javaCmd.Path)
        if (Test-JavaHome $javaHomeFromPath) {
            return $javaHomeFromPath
        }
    }

    $candidates = @()
    $localAppData = [Environment]::GetFolderPath("LocalApplicationData")
    $programFiles = ${env:ProgramFiles}
    $userProfile = $env:USERPROFILE

    if ($userProfile) {
        $gradleJdks = Join-Path $userProfile ".gradle\jdks"
        if (Test-Path $gradleJdks) {
            Get-ChildItem $gradleJdks -Directory -ErrorAction SilentlyContinue |
                Sort-Object LastWriteTime -Descending |
                ForEach-Object { $candidates += $_.FullName }
        }
    }
    if ($localAppData) {
        $candidates += (Join-Path $localAppData "Programs\Android Studio\jbr")
        $candidates += (Join-Path $localAppData "JetBrains\Toolbox\apps\AndroidStudio\ch-0")
    }
    if ($programFiles) {
        $candidates += (Join-Path $programFiles "Android\Android Studio\jbr")
    }

    foreach ($candidate in $candidates) {
        if (Test-JavaHome $candidate) {
            return $candidate
        }
    }

    return $null
}

if (-not (Test-Path $GradleWrapper)) {
    Write-Host "Error: gradlew.bat not found at $GradleWrapper" -ForegroundColor Red
    exit 1
}

$JavaHome = Resolve-JavaHome $JavaHome

if (-not $JavaHome) {
    Write-Host "Error: Java runtime not found." -ForegroundColor Red
    Write-Host "Diffusion Desk currently requires Java $RequiredJavaMajor or newer for Compose desktop." -ForegroundColor Yellow
    Write-Host "Set JAVA_HOME or pass a JBR/JDK path with: .\scripts\run-compose-hot-reload.ps1 -JavaHome `"C:\Path\To\jbr`"" -ForegroundColor Yellow
    exit 1
}

$JavaBin = Join-Path $JavaHome "bin"
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
