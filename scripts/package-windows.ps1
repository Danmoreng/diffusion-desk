param(
    [switch]$BuildMsi,
    [switch]$RequireMsi,
    [switch]$SkipNativeBuild,
    [switch]$Clean,
    [int]$GradleRetries = 3
)

$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot
$ComposeAppDir = Join-Path $RepoRoot "composeApp"
$GradleWrapper = Join-Path $RepoRoot "gradlew.bat"
$PackageName = "diffusion-desk"
$PackageVersion = if ([string]::IsNullOrWhiteSpace($env:APP_VERSION)) { "1.0.0" } else { $env:APP_VERSION }
$RequiredJavaMajor = 25

if ($RequireMsi) {
    $BuildMsi = $true
}

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

function Test-JavaHome([string]$JavaHomePath, [switch]$RequireJPackage) {
    if ([string]::IsNullOrWhiteSpace($JavaHomePath)) { return $false }

    $required = @("bin\java.exe")
    if ($RequireJPackage) {
        $required += "bin\jpackage.exe"
    }

    foreach ($relativePath in $required) {
        if (-not (Test-Path (Join-Path $JavaHomePath $relativePath))) {
            return $false
        }
    }

    $major = Get-JavaMajorVersion $JavaHomePath
    return $major -and $major -ge $RequiredJavaMajor
}

function Resolve-JavaHome([switch]$RequireJPackage) {
    if (Test-JavaHome $env:JAVA_HOME -RequireJPackage:$RequireJPackage) {
        return $env:JAVA_HOME
    }

    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCmd -and $javaCmd.Path) {
        $javaHome = Split-Path -Parent (Split-Path -Parent $javaCmd.Path)
        if (Test-JavaHome $javaHome -RequireJPackage:$RequireJPackage) {
            return $javaHome
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
        if (Test-JavaHome $candidate -RequireJPackage:$RequireJPackage) {
            return $candidate
        }
    }

    return $null
}

function Invoke-Gradle([string[]]$Tasks, [int]$Retries = 1) {
    $attempt = 1
    while ($attempt -le [Math]::Max($Retries, 1)) {
        Write-Host "Running Gradle (attempt $attempt/$Retries): $($Tasks -join ' ')" -ForegroundColor DarkCyan
        & $GradleWrapper `
            "-Dorg.gradle.internal.http.connectionTimeout=120000" `
            "-Dorg.gradle.internal.http.socketTimeout=180000" `
            @Tasks

        if ($LASTEXITCODE -eq 0) {
            return
        }

        if ($attempt -lt $Retries) {
            Start-Sleep -Seconds (5 * $attempt)
        }
        $attempt++
    }

    throw "Gradle failed (exit code $LASTEXITCODE): $($Tasks -join ' ')"
}

function Add-WixToolsToPath {
    $candidateDirs = @(
        (Join-Path $RepoRoot "build\wix311"),
        (Join-Path $ComposeAppDir "build\wix311")
    )

    foreach ($dir in $candidateDirs) {
        if ((Test-Path (Join-Path $dir "wix.exe")) -or
            ((Test-Path (Join-Path $dir "candle.exe")) -and (Test-Path (Join-Path $dir "light.exe")))) {
            $env:Path = $dir + ";" + $env:Path
            Write-Host "Using WiX tools: $dir" -ForegroundColor DarkCyan
            return $true
        }
    }

    $wixTool = Get-Command wix.exe -ErrorAction SilentlyContinue
    if ($wixTool) { return $true }

    $candleTool = Get-Command candle.exe -ErrorAction SilentlyContinue
    $lightTool = Get-Command light.exe -ErrorAction SilentlyContinue
    return [bool]($candleTool -and $lightTool)
}

if (-not (Test-Path $GradleWrapper)) {
    throw "gradlew.bat not found at $GradleWrapper"
}

$resolvedJavaHome = Resolve-JavaHome -RequireJPackage
if (-not $resolvedJavaHome) {
    throw @"
No Java $RequiredJavaMajor+ runtime with jpackage found for Gradle packaging.
Set JAVA_HOME manually to a full Java $RequiredJavaMajor+ JDK/JBR, or install one and retry.
"@
}

$env:JAVA_HOME = $resolvedJavaHome
$env:Path = (Join-Path $resolvedJavaHome "bin") + ";" + $env:Path
Write-Host "Using JAVA_HOME: $resolvedJavaHome" -ForegroundColor DarkCyan

if (-not $SkipNativeBuild) {
    Write-Host "Step 1/4: Build native backend and Web UI..." -ForegroundColor Cyan
    $buildArgs = @()
    if ($Clean) {
        $buildArgs += "-Clean"
    }
    & (Join-Path $PSScriptRoot "build.ps1") @buildArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Native build failed with exit code $LASTEXITCODE"
    }
} else {
    Write-Host "Step 1/4: Skipping native build." -ForegroundColor Yellow
}

$backendBin = Join-Path $RepoRoot "build\bin"
if (-not (Test-Path (Join-Path $backendBin "diffusion_desk_sd_worker.exe"))) {
    throw "Missing SD worker executable under $backendBin. Run scripts\build.ps1 first or omit -SkipNativeBuild."
}
if (-not (Test-Path (Join-Path $backendBin "diffusion_desk_llm_worker.exe"))) {
    throw "Missing LLM worker executable under $backendBin. Run scripts\build.ps1 first or omit -SkipNativeBuild."
}

Write-Host "Step 2/4: Build Compose portable app image..." -ForegroundColor Cyan
Invoke-Gradle @(":composeApp:createDistributable") -Retries $GradleRetries

$appRoot = Join-Path $ComposeAppDir "build\compose\binaries\main\app\$PackageName"
if (-not (Test-Path $appRoot)) {
    throw "Could not find packaged app root: $appRoot"
}

Write-Host "Step 3/4: Copy backend runtime into packaged app..." -ForegroundColor Cyan
$targetBin = Join-Path $appRoot "build\bin"
New-Item -ItemType Directory -Path $targetBin -Force | Out-Null

Get-ChildItem -Path (Join-Path $backendBin "*") -File -Include "*.exe", "*.dll" | ForEach-Object {
    Copy-Item $_.FullName $targetBin -Force
}

foreach ($dir in @("models", "outputs")) {
    New-Item -ItemType Directory -Path (Join-Path $appRoot $dir) -Force | Out-Null
}

$configDefault = Join-Path $RepoRoot "config.default.json"
if (Test-Path $configDefault) {
    Copy-Item $configDefault (Join-Path $appRoot "config.default.json") -Force
}

Write-Host "Step 4/4: Create portable zip..." -ForegroundColor Cyan
$zipDir = Join-Path $ComposeAppDir "build\compose\binaries\main\portable"
New-Item -ItemType Directory -Path $zipDir -Force | Out-Null
$zipPath = Join-Path $zipDir "$PackageName-windows-portable.zip"
if (Test-Path $zipPath) {
    Remove-Item $zipPath -Force
}
Compress-Archive -Path $appRoot -DestinationPath $zipPath -Force

if ($BuildMsi) {
    Write-Host "Building MSI installer from packaged app image..." -ForegroundColor Cyan
    if (-not (Add-WixToolsToPath)) {
        throw "WiX tools were not found. Run Gradle packaging once to download WiX or install WiX and retry."
    }

    $msiDir = Join-Path $ComposeAppDir "build\compose\binaries\main\msi"
    New-Item -ItemType Directory -Path $msiDir -Force | Out-Null
    Get-ChildItem -Path $msiDir -Filter "*.msi" -File -ErrorAction SilentlyContinue | Remove-Item -Force

    try {
        & (Join-Path $resolvedJavaHome "bin\jpackage.exe") `
            "--type" "msi" `
            "--name" $PackageName `
            "--app-image" $appRoot `
            "--dest" $msiDir `
            "--app-version" $PackageVersion `
            "--win-menu" `
            "--win-shortcut"

        if ($LASTEXITCODE -ne 0) {
            throw "jpackage failed with exit code $LASTEXITCODE"
        }

        $msi = Get-ChildItem -Path $msiDir -Filter "*.msi" -File -ErrorAction SilentlyContinue | Select-Object -First 1
        if (-not $msi) {
            throw "jpackage completed but no MSI was written to $msiDir"
        }
    } catch {
        if ($RequireMsi) {
            throw
        } else {
            Write-Warning "MSI packaging failed. Portable app is still usable."
            Write-Warning $_
        }
    }
}

Write-Host "Packaging complete." -ForegroundColor Green
Write-Host "Portable app folder: $appRoot" -ForegroundColor Green
Write-Host "Portable zip: $zipPath" -ForegroundColor Green
if ($BuildMsi) {
    Write-Host "MSI output is under: composeApp\build\compose\binaries\main\msi" -ForegroundColor Green
}
