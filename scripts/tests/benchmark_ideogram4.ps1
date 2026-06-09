#requires -Version 7.0

<#
.SYNOPSIS
Benchmarks Ideogram 4 through diffusion_desk_sd_worker, sd-cli, and ComfyUI.

.DESCRIPTION
Runs identical prompt, seed, sampler, dimensions, and step count across the
selected backends. Results include wall time, backend-reported generation time,
model-load time where available, and sampled NVIDIA GPU metrics.

ComfyUI uses the bundled GGUF API workflow by default. Common prompt, model,
latent, scheduler, sampler, guider, and SaveImage nodes are updated
automatically. Use -ComfyWorkflowPath and -ComfyOverridesPath for custom nodes.

.EXAMPLE
pwsh -File .\scripts\tests\benchmark_ideogram4.ps1 -Backends worker,cli -ModelVariant Q4 -Runs 3 -WarmupRuns 1

.EXAMPLE
pwsh -File .\scripts\tests\benchmark_ideogram4.ps1 -Backends cli -ModelVariant Q8 -Placement Stream12 -Runs 3

.EXAMPLE
pwsh -File .\scripts\tests\benchmark_ideogram4.ps1 -Backends comfyui -ModelVariant Q4 -StartComfyUI -Runs 3 -WarmupRuns 1
#>

[CmdletBinding()]
param(
    [string[]]$Backends = @("worker", "cli"),

    [ValidateSet("Q4", "Q8")]
    [string]$ModelVariant = "Q4",

    [ValidateSet("Preset", "WorkerEffective", "StreamAuto2", "Stream12", "Gpu")]
    [string]$Placement = "Preset",

    [int]$Runs = 1,
    [int]$WarmupRuns = 0,
    [int]$Width = 864,
    [int]$Height = 1152,
    [int]$Steps = 20,
    [double]$CfgScale = 7.0,
    [long]$Seed = 23805,
    [ValidateSet("euler", "euler_a", "heun", "dpm2", "dpm++2s_a", "dpm++2m", "dpm++2mv2", "ipndm", "ipndm_v", "lcm", "ddim_trailing", "tcd", "res_multistep", "res_2s", "er_sde", "euler_cfg_pp", "euler_a_cfg_pp")]
    [string]$Sampler = "euler",
    [string]$Scheduler = "",
    [string]$Prompt = "",
    [string]$PromptFile = "",
    [string]$NegativePrompt = "",

    [string]$ModelRoot = "C:\StableDiffusion\models",
    [string]$DiffusionModel = "",
    [string]$UncondDiffusionModel = "",
    [string]$VaeModel = "vae\flux2dev_ae.safetensors",
    [string]$LlmModel = "",

    [string]$WorkerExe = "",
    [int]$WorkerPort = 5568,
    [string]$SdCliExe = "C:\StableDiffusion\sdcpp-ideogram-build\bin\sd-cli.exe",

    [string]$ComfyUrl = "http://127.0.0.1:8188",
    [string]$ComfyWorkflowPath = "",
    [string]$ComfyOverridesPath = "",
    [string]$ComfyPromptNodeId = "",
    [string]$ComfyLatentNodeId = "",
    [string]$ComfySamplerNodeId = "",
    [string]$ComfySaveNodeId = "",
    [switch]$StartComfyUI,
    [string]$ComfyRoot = "C:\StableDiffusion\ComfyUI_windows_portable",
    [switch]$ComfyFastFp16Accumulation,

    [int]$TimeoutSeconds = 900,
    [int]$GpuSampleMilliseconds = 250,
    [string]$OutputRoot = "",
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$Backends = @($Backends | ForEach-Object { $_ -split "," } | ForEach-Object { $_.Trim().ToLowerInvariant() } | Where-Object { $_ })
$InvalidBackends = @($Backends | Where-Object { $_ -notin @("worker", "cli", "comfyui") })
if ($InvalidBackends.Count -gt 0) { throw "Unsupported backend(s): $($InvalidBackends -join ', ')" }
if ($Backends.Count -eq 0) { throw "Select at least one backend." }

$ScriptDir = $PSScriptRoot
$ProjectRoot = Split-Path (Split-Path $ScriptDir)

if ([string]::IsNullOrWhiteSpace($ComfyWorkflowPath)) {
    $ComfyWorkflowPath = Join-Path $ScriptDir "workflows\ideogram4-gguf-api.json"
}

if ([string]::IsNullOrWhiteSpace($WorkerExe)) {
    $WorkerExe = Join-Path $ProjectRoot "build\bin\diffusion_desk_sd_worker.exe"
}
if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path $ProjectRoot "temp\ideogram4-benchmarks"
}

$DefaultPrompt = @'
{"high_level_description":"A cinematic still-life photograph of fresh, plump blueberries arranged artistically, featuring a bold text overlay that reads 'EAT ME' to evoke a sense of temptation and sensory appeal.","style_description":{"aesthetics":"cinematic, moody, tempting, high-contrast","lighting":"dramatic chiaroscuro lighting with soft highlights on the berry skins","photo":"photograph","medium":"photograph","color_palette":["#0A1128","#1B3055","#4B68A5","#FFFFFF","#000000"]},"compositional_deconstruction":{"background":"A dark, moody, out-of-focus background creating deep shadows and depth.","elements":[{"type":"obj","bbox":[180,260,580,860],"desc":"A cluster of fresh, ripe blueberries with visible waxy bloom and tiny droplets of water.","color_palette":["#1B3055","#4B68A5","#0A1128"]},{"type":"text","bbox":[780,390,850,690],"text":"EAT ME","desc":"Bold, clean, high-contrast typography centered over the blueberries.","color_palette":["#FFFFFF"]}]}}
'@

if (-not [string]::IsNullOrWhiteSpace($PromptFile)) {
    $Prompt = Get-Content -LiteralPath $PromptFile -Raw
} elseif ([string]::IsNullOrWhiteSpace($Prompt)) {
    $Prompt = $DefaultPrompt.Trim()
}

if ($Runs -lt 1) { throw "Runs must be at least 1." }
if ($WarmupRuns -lt 0) { throw "WarmupRuns cannot be negative." }
if ($Width -le 0 -or $Height -le 0 -or $Steps -le 0) { throw "Width, Height, and Steps must be positive." }

$ModelDefaults = @{
    Q4 = @{
        Diffusion = "stable-diffusion\ideogram4-Q4_0.gguf"
        Uncond = "stable-diffusion\ideogram4_uncond-Q4_0.gguf"
        Llm = "llm\Qwen3-VL-8B-Instruct-Q4_K_M.gguf"
    }
    Q8 = @{
        Diffusion = "stable-diffusion\ideogram4-Q8_0.gguf"
        Uncond = "stable-diffusion\ideogram4_unconditional-Q8_0.gguf"
        Llm = "llm\Qwen3-VL-8B-Instruct-Q8_0.gguf"
    }
}

if ([string]::IsNullOrWhiteSpace($DiffusionModel)) { $DiffusionModel = $ModelDefaults[$ModelVariant].Diffusion }
if ([string]::IsNullOrWhiteSpace($UncondDiffusionModel)) { $UncondDiffusionModel = $ModelDefaults[$ModelVariant].Uncond }
if ([string]::IsNullOrWhiteSpace($LlmModel)) { $LlmModel = $ModelDefaults[$ModelVariant].Llm }

function Resolve-ModelPath([string]$Path) {
    if ([System.IO.Path]::IsPathRooted($Path)) { return $Path }
    return Join-Path $ModelRoot $Path
}

$ResolvedModels = @{
    Diffusion = Resolve-ModelPath $DiffusionModel
    Uncond = Resolve-ModelPath $UncondDiffusionModel
    Vae = Resolve-ModelPath $VaeModel
    Llm = Resolve-ModelPath $LlmModel
}

function Get-PlacementSettings([string]$Name) {
    switch ($Name) {
        "Preset" {
            return @{ Offload = $true; VaeOnCpu = $false; FlashAttention = $true; MaxVram = 0.0; StreamLayers = $true }
        }
        "WorkerEffective" {
            return @{ Offload = $true; VaeOnCpu = $true; FlashAttention = $false; MaxVram = 0.0; StreamLayers = $false }
        }
        "Stream12" {
            return @{ Offload = $true; VaeOnCpu = $false; FlashAttention = $true; MaxVram = 12.0; StreamLayers = $true }
        }
        "StreamAuto2" {
            return @{ Offload = $true; VaeOnCpu = $false; FlashAttention = $true; MaxVram = -2.0; StreamLayers = $true }
        }
        "Gpu" {
            return @{ Offload = $false; VaeOnCpu = $false; FlashAttention = $true; MaxVram = 0.0; StreamLayers = $false }
        }
    }
}

$PlacementSettings = Get-PlacementSettings $Placement
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$RunDir = Join-Path $OutputRoot "$Timestamp-$($ModelVariant.ToLowerInvariant())-$($Placement.ToLowerInvariant())"

function Get-FileSha256([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return "" }
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash
}

function Get-CMakeCacheValue([string]$CachePath, [string]$Name) {
    if (-not (Test-Path -LiteralPath $CachePath -PathType Leaf)) { return "" }
    $Match = Select-String -LiteralPath $CachePath -Pattern "^$([regex]::Escape($Name)):[^=]+=(.*)$" | Select-Object -First 1
    if ($null -eq $Match) { return "" }
    return $Match.Matches[0].Groups[1].Value
}

$GpuInfo = try {
    $Line = & nvidia-smi --query-gpu=name,driver_version,memory.total,power.limit --format=csv,noheader,nounits 2>$null | Select-Object -First 1
    $Parts = $Line -split "," | ForEach-Object { $_.Trim() }
    @{ Name = $Parts[0]; Driver = $Parts[1]; MemoryMiB = $Parts[2]; PowerLimitW = $Parts[3] }
} catch { @{} }
$CpuInfo = try { (Get-CimInstance Win32_Processor | Select-Object -First 1 -ExpandProperty Name).Trim() } catch { "" }
$RamGiB = try { [math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1GB, 2) } catch { $null }
$StableDiffusionCommit = try { (& git -C (Join-Path $ProjectRoot "libs\stable-diffusion.cpp") rev-parse HEAD 2>$null).Trim() } catch { "" }
$CliBuildRoot = Split-Path (Split-Path $SdCliExe)

$Config = [ordered]@{
    timestamp = (Get-Date).ToString("o")
    backends = $Backends
    model_variant = $ModelVariant
    placement = $Placement
    placement_settings = $PlacementSettings
    runs = $Runs
    warmup_runs = $WarmupRuns
    width = $Width
    height = $Height
    steps = $Steps
    cfg_scale = $CfgScale
    seed = $Seed
    sampler = $Sampler
    scheduler = $Scheduler
    prompt = $Prompt
    negative_prompt = $NegativePrompt
    models = $ResolvedModels
    worker_exe = $WorkerExe
    sd_cli_exe = $SdCliExe
    comfy_url = $ComfyUrl
    comfy_workflow = $ComfyWorkflowPath
    comfy_overrides = $ComfyOverridesPath
    output_directory = $RunDir
    hardware = @{
        gpu = $GpuInfo
        cpu = $CpuInfo
        ram_gib = $RamGiB
    }
    build = @{
        stable_diffusion_commit = $StableDiffusionCommit
        worker_sha256 = Get-FileSha256 $WorkerExe
        cli_sha256 = Get-FileSha256 $SdCliExe
        worker_cuda_graphs = Get-CMakeCacheValue (Join-Path $ProjectRoot "build\CMakeCache.txt") "GGML_CUDA_GRAPHS"
        cli_cuda_graphs = Get-CMakeCacheValue (Join-Path $CliBuildRoot "CMakeCache.txt") "GGML_CUDA_GRAPHS"
    }
}

if ($DryRun) {
    $Config | ConvertTo-Json -Depth 10
    exit 0
}

foreach ($Path in $ResolvedModels.Values) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "Model file not found: $Path" }
}
if ($Backends -contains "worker" -and -not (Test-Path -LiteralPath $WorkerExe -PathType Leaf)) {
    throw "Worker executable not found: $WorkerExe"
}
if ($Backends -contains "cli" -and -not (Test-Path -LiteralPath $SdCliExe -PathType Leaf)) {
    throw "sd-cli executable not found: $SdCliExe"
}
if ($Backends -contains "comfyui" -and -not (Test-Path -LiteralPath $ComfyWorkflowPath -PathType Leaf)) {
    throw "ComfyUI needs an API-format workflow. File not found: $ComfyWorkflowPath"
}

New-Item -ItemType Directory -Force -Path $RunDir | Out-Null
$Config | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $RunDir "config.json") -Encoding utf8
$Prompt | Set-Content -LiteralPath (Join-Path $RunDir "prompt.txt") -Encoding utf8

$HttpClient = [System.Net.Http.HttpClient]::new()
$HttpClient.Timeout = [TimeSpan]::FromSeconds($TimeoutSeconds)
$Results = [System.Collections.Generic.List[object]]::new()
$OwnedWorker = $null
$OwnedComfy = $null

function Start-CapturedProcess {
    param([string]$FilePath, [string[]]$Arguments, [string]$WorkingDirectory)

    $Info = [System.Diagnostics.ProcessStartInfo]::new()
    $Info.FileName = $FilePath
    $Info.WorkingDirectory = $WorkingDirectory
    $Info.UseShellExecute = $false
    $Info.CreateNoWindow = $true
    $Info.RedirectStandardOutput = $true
    $Info.RedirectStandardError = $true
    foreach ($Argument in $Arguments) { [void]$Info.ArgumentList.Add($Argument) }

    $Process = [System.Diagnostics.Process]::new()
    $Process.StartInfo = $Info
    if (-not $Process.Start()) { throw "Failed to start $FilePath" }

    return @{
        Process = $Process
        Stdout = $Process.StandardOutput.ReadToEndAsync()
        Stderr = $Process.StandardError.ReadToEndAsync()
    }
}

function Stop-CapturedProcess {
    param($Capture, [string]$StdoutPath, [string]$StderrPath)
    if ($null -eq $Capture) { return }
    $Process = $Capture.Process
    if (-not $Process.HasExited) {
        try { $Process.Kill($true) } catch { try { $Process.Kill() } catch {} }
        try { $Process.WaitForExit(10000) | Out-Null } catch {}
    }
    try { $Capture.Stdout.GetAwaiter().GetResult() | Set-Content -LiteralPath $StdoutPath -Encoding utf8 } catch {}
    try { $Capture.Stderr.GetAwaiter().GetResult() | Set-Content -LiteralPath $StderrPath -Encoding utf8 } catch {}
    $Process.Dispose()
}

function Get-GpuSample {
    try {
        $Line = & nvidia-smi --query-gpu=memory.used,utilization.gpu,power.draw,temperature.gpu --format=csv,noheader,nounits 2>$null | Select-Object -First 1
        if ([string]::IsNullOrWhiteSpace($Line)) { return $null }
        $Parts = $Line -split "," | ForEach-Object { $_.Trim() }
        return [pscustomobject]@{
            MemoryMiB = [double]$Parts[0]
            UtilizationPct = [double]$Parts[1]
            PowerW = [double]$Parts[2]
            TemperatureC = [double]$Parts[3]
        }
    } catch {
        return $null
    }
}

function Get-GpuMetrics($Samples, $Baseline) {
    $Valid = @($Samples | Where-Object { $null -ne $_ })
    if ($Valid.Count -eq 0) {
        return @{ BaselineMiB = $null; PeakMiB = $null; PeakDeltaMiB = $null; PeakUtilizationPct = $null; PeakPowerW = $null; PeakTemperatureC = $null }
    }
    $BaselineMiB = if ($null -ne $Baseline) { $Baseline.MemoryMiB } else { $Valid[0].MemoryMiB }
    $PeakMiB = ($Valid | Measure-Object MemoryMiB -Maximum).Maximum
    return @{
        BaselineMiB = $BaselineMiB
        PeakMiB = $PeakMiB
        PeakDeltaMiB = $PeakMiB - $BaselineMiB
        PeakUtilizationPct = ($Valid | Measure-Object UtilizationPct -Maximum).Maximum
        PeakPowerW = ($Valid | Measure-Object PowerW -Maximum).Maximum
        PeakTemperatureC = ($Valid | Measure-Object TemperatureC -Maximum).Maximum
    }
}

function Invoke-MonitoredJsonRequest {
    param([ValidateSet("GET", "POST")][string]$Method, [string]$Uri, $Body = $null)

    $Baseline = Get-GpuSample
    $Samples = [System.Collections.Generic.List[object]]::new()
    $Watch = [System.Diagnostics.Stopwatch]::StartNew()
    if ($Method -eq "POST") {
        $Json = $Body | ConvertTo-Json -Depth 100 -Compress
        $Content = [System.Net.Http.StringContent]::new($Json, [System.Text.Encoding]::UTF8, "application/json")
        $Task = $HttpClient.PostAsync($Uri, $Content)
    } else {
        $Task = $HttpClient.GetAsync($Uri)
    }
    while (-not $Task.IsCompleted) {
        $Samples.Add((Get-GpuSample))
        Start-Sleep -Milliseconds $GpuSampleMilliseconds
    }
    $Response = $Task.GetAwaiter().GetResult()
    $Text = $Response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    $Watch.Stop()
    if (-not $Response.IsSuccessStatusCode) {
        throw "HTTP $([int]$Response.StatusCode) from $Uri`: $Text"
    }
    $Parsed = if ([string]::IsNullOrWhiteSpace($Text)) { $null } else { $Text | ConvertFrom-Json -AsHashtable }
    return @{ Body = $Parsed; Raw = $Text; WallSeconds = $Watch.Elapsed.TotalSeconds; Gpu = Get-GpuMetrics $Samples $Baseline }
}

function Wait-HttpReady([string]$Uri, [int]$Seconds) {
    $Deadline = (Get-Date).AddSeconds($Seconds)
    do {
        try {
            $Response = $HttpClient.GetAsync($Uri).GetAwaiter().GetResult()
            if ($Response.IsSuccessStatusCode) { return }
        } catch {}
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $Deadline)
    throw "Service did not become ready: $Uri"
}

function Test-HttpReady([string]$Uri) {
    try {
        $Response = $HttpClient.GetAsync($Uri).GetAwaiter().GetResult()
        return $Response.IsSuccessStatusCode
    } catch { return $false }
}

function Add-Result {
    param(
        [string]$Backend,
        [int]$Run,
        [bool]$Warmup,
        [bool]$Success,
        [double]$WallSeconds,
        $Gpu,
        $BackendSeconds = $null,
        $LoadSeconds = $null,
        $ConditioningSeconds = $null,
        $SamplingSeconds = $null,
        $DecodeSeconds = $null,
        [string]$OutputFile = "",
        [string]$LogFile = "",
        [string]$Notes = ""
    )
    $Results.Add([pscustomobject][ordered]@{
        timestamp = (Get-Date).ToString("o")
        backend = $Backend
        model_variant = $ModelVariant
        placement = $Placement
        run = $Run
        warmup = $Warmup
        success = $Success
        wall_seconds = if ($null -ne $WallSeconds) { [math]::Round($WallSeconds, 3) } else { $null }
        backend_generation_seconds = if ($null -ne $BackendSeconds) { [math]::Round([double]$BackendSeconds, 3) } else { $null }
        model_load_seconds = if ($null -ne $LoadSeconds) { [math]::Round([double]$LoadSeconds, 3) } else { $null }
        conditioning_seconds = if ($null -ne $ConditioningSeconds) { [math]::Round([double]$ConditioningSeconds, 3) } else { $null }
        sampling_seconds = if ($null -ne $SamplingSeconds) { [math]::Round([double]$SamplingSeconds, 3) } else { $null }
        vae_decode_seconds = if ($null -ne $DecodeSeconds) { [math]::Round([double]$DecodeSeconds, 3) } else { $null }
        overhead_seconds = $null
        baseline_vram_mib = $Gpu.BaselineMiB
        peak_vram_mib = $Gpu.PeakMiB
        peak_vram_delta_mib = $Gpu.PeakDeltaMiB
        peak_gpu_utilization_pct = $Gpu.PeakUtilizationPct
        peak_power_w = $Gpu.PeakPowerW
        peak_temperature_c = $Gpu.PeakTemperatureC
        output_file = $OutputFile
        log_file = $LogFile
        notes = $Notes
    })
}

function Get-RegexNumber([string]$Text, [string]$Pattern) {
    $Match = [regex]::Match($Text, $Pattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
    if (-not $Match.Success) { return $null }
    return [double]::Parse($Match.Groups[1].Value, [System.Globalization.CultureInfo]::InvariantCulture)
}

function Get-RegexNumbers([string]$Text, [string]$Pattern, $Options = [System.Text.RegularExpressions.RegexOptions]::IgnoreCase) {
    return @([regex]::Matches($Text, $Pattern, $Options) | ForEach-Object {
        [double]::Parse($_.Groups[1].Value, [System.Globalization.CultureInfo]::InvariantCulture)
    })
}

function Update-ResultOverhead($Result) {
    $Known = 0.0
    foreach ($Name in @("model_load_seconds", "conditioning_seconds", "sampling_seconds", "vae_decode_seconds")) {
        if ($null -ne $Result.$Name) { $Known += [double]$Result.$Name }
    }
    $Result.overhead_seconds = [math]::Round([math]::Max(0.0, [double]$Result.wall_seconds - $Known), 3)
}

function Convert-TqdmElapsedSeconds([string]$Value) {
    $Parts = @($Value -split ':' | ForEach-Object { [int]$_ })
    if ($Parts.Count -eq 2) { return ($Parts[0] * 60) + $Parts[1] }
    if ($Parts.Count -eq 3) { return ($Parts[0] * 3600) + ($Parts[1] * 60) + $Parts[2] }
    return $null
}

function Invoke-WorkerBenchmarks {
    $BackendDir = Join-Path $RunDir "worker"
    New-Item -ItemType Directory -Force -Path $BackendDir | Out-Null
    $BaseUrl = "http://127.0.0.1:$WorkerPort"
    if (Test-HttpReady "$BaseUrl/internal/health") { throw "Worker port $WorkerPort is already in use." }

    $Args = @(
        "--listen-ip", "127.0.0.1",
        "--listen-port", "$WorkerPort",
        "--model-dir", $ModelRoot,
        "--output-dir", $BackendDir,
        "--sd-idle-timeout", "0",
        "--verbose"
    )
    $script:OwnedWorker = Start-CapturedProcess $WorkerExe $Args (Split-Path $WorkerExe)
    Wait-HttpReady "$BaseUrl/internal/health" 30

    $LoadBody = [ordered]@{
        model_id = $DiffusionModel
        uncond_diffusion_model = $UncondDiffusionModel
        vae = $VaeModel
        llm = $LlmModel
        clip_on_cpu = $false
        vae_on_cpu = $PlacementSettings.VaeOnCpu
        offload_params_to_cpu = $PlacementSettings.Offload
        flash_attn = $PlacementSettings.FlashAttention
        max_vram_gb = $PlacementSettings.MaxVram
        stream_layers = $PlacementSettings.StreamLayers
    }
    Write-Host "[worker] Loading $ModelVariant model..."
    $Load = Invoke-MonitoredJsonRequest POST "$BaseUrl/v1/models/load" $LoadBody
    $Load.Raw | Set-Content -LiteralPath (Join-Path $BackendDir "model-load-response.json") -Encoding utf8

    $Total = $WarmupRuns + $Runs
    for ($Index = 1; $Index -le $Total; $Index++) {
        $IsWarmup = $Index -le $WarmupRuns
        $MeasuredRun = if ($IsWarmup) { $Index } else { $Index - $WarmupRuns }
        $Label = if ($IsWarmup) { "warmup-$MeasuredRun" } else { "run-$MeasuredRun" }
        Write-Host "[worker] $Label"
        $Body = [ordered]@{
            prompt = $Prompt
            negative_prompt = $NegativePrompt
            width = $Width
            height = $Height
            size = "${Width}x${Height}"
            sample_steps = $Steps
            cfg_scale = $CfgScale
            sampling_method = $Sampler
            seed = $Seed
            n = 1
            batch_count = 1
            no_base64 = $true
            save_image = $true
        }
        if (-not [string]::IsNullOrWhiteSpace($Scheduler)) { $Body.scheduler = $Scheduler }
        try {
            $Response = Invoke-MonitoredJsonRequest POST "$BaseUrl/v1/images/generations" $Body
            $ResponsePath = Join-Path $BackendDir "$Label-response.json"
            $Response.Raw | Set-Content -LiteralPath $ResponsePath -Encoding utf8
            $BackendSeconds = $Response.Body.generation_time
            $OutputFile = ""
            if ($Response.Body.data.Count -gt 0 -and $Response.Body.data[0].name) {
                $Candidate = Join-Path $BackendDir ([string]$Response.Body.data[0].name)
                if (Test-Path -LiteralPath $Candidate) { $OutputFile = $Candidate }
            }
            Add-Result -Backend "worker" -Run $MeasuredRun -Warmup $IsWarmup -Success $true -WallSeconds $Response.WallSeconds -Gpu $Response.Gpu -BackendSeconds $BackendSeconds -OutputFile $OutputFile -LogFile $ResponsePath -Notes "Worker initial load=$([math]::Round($Load.WallSeconds, 3))s; max_vram=$($Load.Body.max_vram_gb), stream_layers=$($Load.Body.stream_layers), offload=$($Load.Body.offload_params_to_cpu)"
        } catch {
            Add-Result -Backend "worker" -Run $MeasuredRun -Warmup $IsWarmup -Success $false -WallSeconds 0 -Gpu (Get-GpuMetrics @() $null) -Notes $_.Exception.Message
            throw
        }
    }

    $WorkerStdout = Join-Path $BackendDir "worker.stdout.log"
    $WorkerStderr = Join-Path $BackendDir "worker.stderr.log"
    Stop-CapturedProcess $OwnedWorker $WorkerStdout $WorkerStderr
    $script:OwnedWorker = $null

    $WorkerLog = "$(Get-Content -LiteralPath $WorkerStdout -Raw)`n$(Get-Content -LiteralPath $WorkerStderr -Raw)"
    $Singleline = [System.Text.RegularExpressions.RegexOptions]::IgnoreCase -bor [System.Text.RegularExpressions.RegexOptions]::Singleline
    $ReloadSeconds = Get-RegexNumbers $WorkerLog 'Reloading Ideogram4 context.*?loading tensors completed, taking\s+([0-9.]+)s' $Singleline
    $ConditioningSeconds = Get-RegexNumbers $WorkerLog 'get_learned_condition completed, taking\s+([0-9.]+)s'
    $SamplingSeconds = Get-RegexNumbers $WorkerLog 'sampling completed, taking\s+([0-9.]+)s'
    $DecodeSeconds = Get-RegexNumbers $WorkerLog 'decode_first_stage completed, taking\s+([0-9.]+)s'
    $WorkerRows = @($Results | Where-Object { $_.backend -eq "worker" })
    for ($Index = 0; $Index -lt $WorkerRows.Count; $Index++) {
        if ($Index -lt $ReloadSeconds.Count) { $WorkerRows[$Index].model_load_seconds = [math]::Round($ReloadSeconds[$Index], 3) }
        if ($Index -lt $ConditioningSeconds.Count) { $WorkerRows[$Index].conditioning_seconds = [math]::Round($ConditioningSeconds[$Index], 3) }
        if ($Index -lt $SamplingSeconds.Count) { $WorkerRows[$Index].sampling_seconds = [math]::Round($SamplingSeconds[$Index], 3) }
        if ($Index -lt $DecodeSeconds.Count) { $WorkerRows[$Index].vae_decode_seconds = [math]::Round($DecodeSeconds[$Index], 3) }
        Update-ResultOverhead $WorkerRows[$Index]
    }
}

function Get-CliPlacementArguments {
    $Args = [System.Collections.Generic.List[string]]::new()
    [void]$Args.Add("--mmap")
    if ($PlacementSettings.Offload) { [void]$Args.Add("--offload-to-cpu") }
    if ($PlacementSettings.VaeOnCpu) { [void]$Args.Add("--vae-on-cpu") }
    if ($PlacementSettings.FlashAttention) { [void]$Args.Add("--diffusion-fa") }
    if ($PlacementSettings.MaxVram -ne 0.0) {
        [void]$Args.Add("--max-vram")
        [void]$Args.Add(([string]::Format([System.Globalization.CultureInfo]::InvariantCulture, "{0}", $PlacementSettings.MaxVram)))
    }
    if ($PlacementSettings.StreamLayers) { [void]$Args.Add("--stream-layers") }
    return $Args.ToArray()
}

function Invoke-CliBenchmarks {
    $BackendDir = Join-Path $RunDir "cli"
    New-Item -ItemType Directory -Force -Path $BackendDir | Out-Null
    $Total = $WarmupRuns + $Runs
    for ($Index = 1; $Index -le $Total; $Index++) {
        $IsWarmup = $Index -le $WarmupRuns
        $MeasuredRun = if ($IsWarmup) { $Index } else { $Index - $WarmupRuns }
        $Label = if ($IsWarmup) { "warmup-$MeasuredRun" } else { "run-$MeasuredRun" }
        $OutputFile = Join-Path $BackendDir "$Label.png"
        $StdoutPath = Join-Path $BackendDir "$Label.stdout.log"
        $StderrPath = Join-Path $BackendDir "$Label.stderr.log"
        Write-Host "[cli] $Label"

        $Args = [System.Collections.Generic.List[string]]::new()
        foreach ($Argument in @(
            "--diffusion-model", $ResolvedModels.Diffusion,
            "--uncond-diffusion-model", $ResolvedModels.Uncond,
            "--vae", $ResolvedModels.Vae,
            "--llm", $ResolvedModels.Llm,
            "--prompt", $Prompt,
            "--negative-prompt", $NegativePrompt,
            "--width", "$Width",
            "--height", "$Height",
            "--steps", "$Steps",
            "--cfg-scale", ([string]::Format([System.Globalization.CultureInfo]::InvariantCulture, "{0}", $CfgScale)),
            "--sampling-method", $Sampler,
            "--seed", "$Seed",
            "--output", $OutputFile,
            "--verbose"
        )) { [void]$Args.Add($Argument) }
        if (-not [string]::IsNullOrWhiteSpace($Scheduler)) {
            [void]$Args.Add("--scheduler")
            [void]$Args.Add($Scheduler)
        }
        foreach ($Argument in (Get-CliPlacementArguments)) { [void]$Args.Add($Argument) }

        $Baseline = Get-GpuSample
        $Samples = [System.Collections.Generic.List[object]]::new()
        $Watch = [System.Diagnostics.Stopwatch]::StartNew()
        $Capture = Start-CapturedProcess $SdCliExe $Args.ToArray() (Split-Path $SdCliExe)
        while (-not $Capture.Process.HasExited) {
            $Samples.Add((Get-GpuSample))
            Start-Sleep -Milliseconds $GpuSampleMilliseconds
        }
        $Capture.Process.WaitForExit()
        $Watch.Stop()
        $Stdout = $Capture.Stdout.GetAwaiter().GetResult()
        $Stderr = $Capture.Stderr.GetAwaiter().GetResult()
        $ExitCode = $Capture.Process.ExitCode
        $Capture.Process.Dispose()
        $Stdout | Set-Content -LiteralPath $StdoutPath -Encoding utf8
        $Stderr | Set-Content -LiteralPath $StderrPath -Encoding utf8
        $Combined = "$Stdout`n$Stderr"
        $Gpu = Get-GpuMetrics $Samples $Baseline
        $LoadSeconds = Get-RegexNumber $Combined 'loading tensors completed, taking\s+([0-9.]+)s'
        $ConditioningSeconds = Get-RegexNumber $Combined 'get_learned_condition completed, taking\s+([0-9.]+)s'
        $SamplingSeconds = Get-RegexNumber $Combined 'sampling completed, taking\s+([0-9.]+)s'
        $DecodeSeconds = Get-RegexNumber $Combined 'decode_first_stage completed, taking\s+([0-9.]+)s'
        $BackendSeconds = Get-RegexNumber $Combined 'generate_image completed in\s+([0-9.]+)s'
        $Success = $ExitCode -eq 0 -and (Test-Path -LiteralPath $OutputFile)
        Add-Result -Backend "cli" -Run $MeasuredRun -Warmup $IsWarmup -Success $Success -WallSeconds $Watch.Elapsed.TotalSeconds -Gpu $Gpu -BackendSeconds $BackendSeconds -LoadSeconds $LoadSeconds -ConditioningSeconds $ConditioningSeconds -SamplingSeconds $SamplingSeconds -DecodeSeconds $DecodeSeconds -OutputFile $OutputFile -LogFile $StdoutPath -Notes "exit_code=$ExitCode"
        Update-ResultOverhead $Results[$Results.Count - 1]
        if (-not $Success) { throw "sd-cli failed for $Label. See $StdoutPath and $StderrPath" }
    }
}

function Find-ComfyNodeId($Workflow, [string]$ClassPattern) {
    foreach ($Entry in $Workflow.GetEnumerator()) {
        if ([string]$Entry.Value.class_type -match $ClassPattern) { return [string]$Entry.Key }
    }
    return ""
}

function ConvertTo-ComfySamplerName([string]$Name) {
    $Names = @{
        euler_a = "euler_ancestral"
        euler_a_cfg_pp = "euler_ancestral_cfg_pp"
        "dpm++2s_a" = "dpmpp_2s_ancestral"
        "dpm++2m" = "dpmpp_2m"
    }
    if ($Names.ContainsKey($Name)) { return $Names[$Name] }
    return $Name
}

function Resolve-TemplateValue($Value) {
    if ($Value -isnot [string]) { return $Value }
    switch ($Value) {
        "{{prompt}}" { return $Prompt }
        "{{negative_prompt}}" { return $NegativePrompt }
        "{{width}}" { return $Width }
        "{{height}}" { return $Height }
        "{{steps}}" { return $Steps }
        "{{cfg_scale}}" { return $CfgScale }
        "{{seed}}" { return $Seed }
        "{{sampler}}" { return $Sampler }
        "{{scheduler}}" { return $Scheduler }
        "{{diffusion_model}}" { return [System.IO.Path]::GetFileName($ResolvedModels.Diffusion) }
        "{{uncond_diffusion_model}}" { return [System.IO.Path]::GetFileName($ResolvedModels.Uncond) }
        "{{llm_model}}" { return [System.IO.Path]::GetFileName($ResolvedModels.Llm) }
        "{{vae_model}}" { return [System.IO.Path]::GetFileName($ResolvedModels.Vae) }
        "{{model_variant}}" { return $ModelVariant }
        default {
            return $Value.Replace("{{prompt}}", $Prompt).Replace("{{negative_prompt}}", $NegativePrompt).Replace("{{model_variant}}", $ModelVariant)
        }
    }
}

function New-ComfyWorkflow([string]$Label) {
    $Workflow = Get-Content -LiteralPath $ComfyWorkflowPath -Raw | ConvertFrom-Json -AsHashtable
    $PromptNode = if ($ComfyPromptNodeId) { $ComfyPromptNodeId } else { Find-ComfyNodeId $Workflow '^CLIPTextEncode' }
    $LatentNode = if ($ComfyLatentNodeId) { $ComfyLatentNodeId } else { Find-ComfyNodeId $Workflow '^Empty.*LatentImage$' }
    $SamplerNode = if ($ComfySamplerNodeId) { $ComfySamplerNodeId } else { Find-ComfyNodeId $Workflow '^KSampler' }
    $SaveNode = if ($ComfySaveNodeId) { $ComfySaveNodeId } else { Find-ComfyNodeId $Workflow '^SaveImage$' }

    if ($PromptNode -and $Workflow.ContainsKey($PromptNode) -and $Workflow[$PromptNode].inputs.ContainsKey("text")) { $Workflow[$PromptNode].inputs.text = $Prompt }
    if ($LatentNode -and $Workflow.ContainsKey($LatentNode)) {
        if ($Workflow[$LatentNode].inputs.ContainsKey("width")) { $Workflow[$LatentNode].inputs.width = $Width }
        if ($Workflow[$LatentNode].inputs.ContainsKey("height")) { $Workflow[$LatentNode].inputs.height = $Height }
        if ($Workflow[$LatentNode].inputs.ContainsKey("batch_size")) { $Workflow[$LatentNode].inputs.batch_size = 1 }
    }
    if ($SamplerNode -and $Workflow.ContainsKey($SamplerNode)) {
        $Inputs = $Workflow[$SamplerNode].inputs
        if ($Inputs.ContainsKey("seed")) { $Inputs.seed = $Seed }
        if ($Inputs.ContainsKey("noise_seed")) { $Inputs.noise_seed = $Seed }
        if ($Inputs.ContainsKey("steps")) { $Inputs.steps = $Steps }
        if ($Inputs.ContainsKey("cfg")) { $Inputs.cfg = $CfgScale }
        if ($Inputs.ContainsKey("sampler_name")) { $Inputs.sampler_name = $Sampler }
        if ($Scheduler -and $Inputs.ContainsKey("scheduler")) { $Inputs.scheduler = $Scheduler }
    }
    if ($SaveNode -and $Workflow.ContainsKey($SaveNode) -and $Workflow[$SaveNode].inputs.ContainsKey("filename_prefix")) {
        $Workflow[$SaveNode].inputs.filename_prefix = "ideogram4-benchmark/$Timestamp/$Label"
    }

    $ComfySampler = ConvertTo-ComfySamplerName $Sampler
    foreach ($Entry in $Workflow.GetEnumerator()) {
        $Inputs = $Entry.Value.inputs
        switch -Regex ([string]$Entry.Value.class_type) {
            '^RandomNoise$' {
                if ($Inputs.ContainsKey("noise_seed")) { $Inputs.noise_seed = $Seed }
            }
            '^Ideogram4Scheduler$' {
                if ($Inputs.ContainsKey("steps")) { $Inputs.steps = $Steps }
                if ($Inputs.ContainsKey("width")) { $Inputs.width = $Width }
                if ($Inputs.ContainsKey("height")) { $Inputs.height = $Height }
            }
            '^DualModelGuider$' {
                if ($Inputs.ContainsKey("cfg")) { $Inputs.cfg = $CfgScale }
            }
            '^KSamplerSelect$' {
                if ($Inputs.ContainsKey("sampler_name")) { $Inputs.sampler_name = $ComfySampler }
            }
            '^UnetLoaderGGUF' {
                if ($Inputs.ContainsKey("unet_name")) {
                    if ([string]$Inputs.unet_name -match 'uncond') {
                        $Inputs.unet_name = [System.IO.Path]::GetFileName($ResolvedModels.Uncond)
                    } else {
                        $Inputs.unet_name = [System.IO.Path]::GetFileName($ResolvedModels.Diffusion)
                    }
                }
            }
            '^CLIPLoaderGGUF$' {
                if ($Inputs.ContainsKey("clip_name")) { $Inputs.clip_name = [System.IO.Path]::GetFileName($ResolvedModels.Llm) }
                if ($Inputs.ContainsKey("type")) { $Inputs.type = "ideogram4" }
            }
            '^VAELoader$' {
                if ($Inputs.ContainsKey("vae_name")) { $Inputs.vae_name = [System.IO.Path]::GetFileName($ResolvedModels.Vae) }
            }
        }
    }

    if (-not [string]::IsNullOrWhiteSpace($ComfyOverridesPath)) {
        $Overrides = Get-Content -LiteralPath $ComfyOverridesPath -Raw | ConvertFrom-Json -AsHashtable
        foreach ($NodeEntry in $Overrides.GetEnumerator()) {
            if (-not $Workflow.ContainsKey([string]$NodeEntry.Key)) { throw "Comfy override node not found: $($NodeEntry.Key)" }
            foreach ($InputEntry in $NodeEntry.Value.GetEnumerator()) {
                $Workflow[[string]$NodeEntry.Key].inputs[[string]$InputEntry.Key] = Resolve-TemplateValue $InputEntry.Value
            }
        }
    }
    return $Workflow
}

function Invoke-ComfyBenchmarks {
    $BackendDir = Join-Path $RunDir "comfyui"
    New-Item -ItemType Directory -Force -Path $BackendDir | Out-Null
    $HealthUri = "$ComfyUrl/system_stats"

    if (-not (Test-HttpReady $HealthUri)) {
        if (-not $StartComfyUI) { throw "ComfyUI is not reachable at $ComfyUrl. Start it or pass -StartComfyUI." }
        $Python = Join-Path $ComfyRoot "python_embeded\python.exe"
        $Main = Join-Path $ComfyRoot "ComfyUI\main.py"
        if (-not (Test-Path -LiteralPath $Python) -or -not (Test-Path -LiteralPath $Main)) { throw "Invalid ComfyUI root: $ComfyRoot" }
        $ComfyUri = [Uri]$ComfyUrl
        $Args = @("-s", "ComfyUI\main.py", "--windows-standalone-build", "--listen", $ComfyUri.Host, "--port", "$($ComfyUri.Port)", "--cache-none")
        if ($ComfyFastFp16Accumulation) { $Args += @("--fast", "fp16_accumulation") }
        $script:OwnedComfy = Start-CapturedProcess $Python $Args $ComfyRoot
        Wait-HttpReady $HealthUri 120
    }

    $ObjectInfo = Invoke-RestMethod "$ComfyUrl/object_info"
    $RequiredNodes = @("DualModelGuider", "Ideogram4Scheduler", "UnetLoaderGGUF", "CLIPLoaderGGUF")
    $MissingNodes = @($RequiredNodes | Where-Object { $ObjectInfo.PSObject.Properties.Name -notcontains $_ })
    if ($MissingNodes.Count -gt 0) {
        throw "ComfyUI is missing required Ideogram GGUF nodes: $($MissingNodes -join ', '). Use ComfyUI >= 0.24.0 and ComfyUI-GGUF."
    }

    $Total = $WarmupRuns + $Runs
    for ($Index = 1; $Index -le $Total; $Index++) {
        $IsWarmup = $Index -le $WarmupRuns
        $MeasuredRun = if ($IsWarmup) { $Index } else { $Index - $WarmupRuns }
        $Label = if ($IsWarmup) { "warmup-$MeasuredRun" } else { "run-$MeasuredRun" }
        Write-Host "[comfyui] $Label"
        $Workflow = New-ComfyWorkflow $Label
        $WorkflowPath = Join-Path $BackendDir "$Label-workflow.json"
        $Workflow | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $WorkflowPath -Encoding utf8
        $ClientId = [guid]::NewGuid().ToString("N")
        $Submit = Invoke-MonitoredJsonRequest POST "$ComfyUrl/prompt" @{ prompt = $Workflow; client_id = $ClientId }
        $PromptId = [string]$Submit.Body.prompt_id
        if ([string]::IsNullOrWhiteSpace($PromptId)) { throw "ComfyUI did not return prompt_id." }

        $Baseline = Get-GpuSample
        $Samples = [System.Collections.Generic.List[object]]::new()
        $Watch = [System.Diagnostics.Stopwatch]::StartNew()
        $Deadline = (Get-Date).AddSeconds($TimeoutSeconds)
        $HistoryEntry = $null
        do {
            $Samples.Add((Get-GpuSample))
            Start-Sleep -Milliseconds $GpuSampleMilliseconds
            try {
                $HistoryText = $HttpClient.GetStringAsync("$ComfyUrl/history/$PromptId").GetAwaiter().GetResult()
                $History = $HistoryText | ConvertFrom-Json -AsHashtable
                if ($History.ContainsKey($PromptId)) { $HistoryEntry = $History[$PromptId] }
            } catch {}
        } while ($null -eq $HistoryEntry -and (Get-Date) -lt $Deadline)
        $Watch.Stop()
        if ($null -eq $HistoryEntry) { throw "Timed out waiting for ComfyUI prompt $PromptId" }

        $HistoryPath = Join-Path $BackendDir "$Label-history.json"
        $HistoryEntry | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $HistoryPath -Encoding utf8
        $OutputFile = ""
        foreach ($NodeOutput in $HistoryEntry.outputs.Values) {
            if ($NodeOutput.ContainsKey("images") -and $NodeOutput.images.Count -gt 0) {
                $Image = $NodeOutput.images[0]
                $Query = "filename=$([Uri]::EscapeDataString([string]$Image.filename))&subfolder=$([Uri]::EscapeDataString([string]$Image.subfolder))&type=$([Uri]::EscapeDataString([string]$Image.type))"
                $Bytes = $HttpClient.GetByteArrayAsync("$ComfyUrl/view?$Query").GetAwaiter().GetResult()
                $OutputFile = Join-Path $BackendDir "$Label.png"
                [System.IO.File]::WriteAllBytes($OutputFile, $Bytes)
                break
            }
        }
        $Status = [string]$HistoryEntry.status.status_str
        $Success = $Status -eq "success" -or -not [string]::IsNullOrWhiteSpace($OutputFile)
        $ExecutionStartMs = $null
        $ExecutionSuccessMs = $null
        $CachedNodeIds = @()
        foreach ($Message in $HistoryEntry.status.messages) {
            if ($Message[0] -eq "execution_start") { $ExecutionStartMs = [double]$Message[1].timestamp }
            if ($Message[0] -eq "execution_success") { $ExecutionSuccessMs = [double]$Message[1].timestamp }
            if ($Message[0] -eq "execution_cached") { $CachedNodeIds += @($Message[1].nodes) }
        }
        $CachedSampler = @($CachedNodeIds | Where-Object {
            $Workflow.ContainsKey([string]$_) -and [string]$Workflow[[string]$_].class_type -match 'SamplerCustom|KSampler'
        })
        if ($CachedSampler.Count -gt 0) { $Success = $false }
        $BackendSeconds = if ($null -ne $ExecutionStartMs -and $null -ne $ExecutionSuccessMs) { ($ExecutionSuccessMs - $ExecutionStartMs) / 1000.0 } else { $null }
        Add-Result -Backend "comfyui" -Run $MeasuredRun -Warmup $IsWarmup -Success $Success -WallSeconds ($Submit.WallSeconds + $Watch.Elapsed.TotalSeconds) -Gpu (Get-GpuMetrics $Samples $Baseline) -BackendSeconds $BackendSeconds -OutputFile $OutputFile -LogFile $HistoryPath -Notes "prompt_id=$PromptId; status=$Status; cached_nodes=$($CachedNodeIds.Count)"
        Update-ResultOverhead $Results[$Results.Count - 1]
        if (-not $Success) { throw "ComfyUI failed for $Label. See $HistoryPath" }
    }

    if ($null -ne $OwnedComfy) {
        $ComfyStdout = Join-Path $BackendDir "comfy.stdout.log"
        $ComfyStderr = Join-Path $BackendDir "comfy.stderr.log"
        Stop-CapturedProcess $OwnedComfy $ComfyStdout $ComfyStderr
        $script:OwnedComfy = $null

        $ComfyLog = Get-Content -LiteralPath $ComfyStderr -Raw
        $PromptMatches = [regex]::Matches($ComfyLog, 'Prompt executed in\s+[0-9.]+\s+seconds', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
        $PreviousEnd = 0
        $ComfyRows = @($Results | Where-Object { $_.backend -eq "comfyui" })
        for ($Index = 0; $Index -lt [math]::Min($PromptMatches.Count, $ComfyRows.Count); $Index++) {
            $Segment = $ComfyLog.Substring($PreviousEnd, $PromptMatches[$Index].Index - $PreviousEnd)
            $ElapsedMatches = [regex]::Matches($Segment, "$Steps/$Steps \[([0-9:]+)<")
            if ($ElapsedMatches.Count -gt 0) {
                $Elapsed = Convert-TqdmElapsedSeconds $ElapsedMatches[$ElapsedMatches.Count - 1].Groups[1].Value
                $ComfyRows[$Index].sampling_seconds = [math]::Round([double]$Elapsed, 3)
                Update-ResultOverhead $ComfyRows[$Index]
            }
            $PreviousEnd = $PromptMatches[$Index].Index + $PromptMatches[$Index].Length
        }
    }
}

function Get-Median([double[]]$Values) {
    if ($Values.Count -eq 0) { return $null }
    $Sorted = @($Values | Sort-Object)
    $Middle = [int][math]::Floor($Sorted.Count / 2)
    if ($Sorted.Count % 2 -eq 1) { return $Sorted[$Middle] }
    return ($Sorted[$Middle - 1] + $Sorted[$Middle]) / 2.0
}

function Write-Results {
    $OriginalCulture = [System.Threading.Thread]::CurrentThread.CurrentCulture
    [System.Threading.Thread]::CurrentThread.CurrentCulture = [System.Globalization.CultureInfo]::InvariantCulture
    try {
    $CsvPath = Join-Path $RunDir "results.csv"
    $JsonPath = Join-Path $RunDir "results.json"
    $SummaryPath = Join-Path $RunDir "summary.csv"
    $AnalysisPath = Join-Path $RunDir "analysis.csv"
    $Results | Export-Csv -LiteralPath $CsvPath -NoTypeInformation -Encoding utf8
    $Results | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $JsonPath -Encoding utf8
    $Results | Select-Object backend, model_variant, placement, run, warmup, success,
        @{Name="model_load_or_reload_seconds"; Expression={$_.model_load_seconds}},
        conditioning_seconds, sampling_seconds, vae_decode_seconds, overhead_seconds,
        @{Name="engine_internal_total_seconds"; Expression={$_.backend_generation_seconds}},
        @{Name="total_seconds"; Expression={$_.wall_seconds}},
        peak_vram_mib, peak_vram_delta_mib, peak_gpu_utilization_pct, peak_power_w,
        output_file, log_file, notes |
        Export-Csv -LiteralPath $AnalysisPath -NoTypeInformation -Encoding utf8

    $Summary = foreach ($Group in ($Results | Where-Object { -not $_.warmup -and $_.success } | Group-Object backend, model_variant, placement)) {
        $Rows = @($Group.Group)
        $Wall = @($Rows | ForEach-Object { [double]$_.wall_seconds })
        $BackendTimes = @($Rows | Where-Object { $null -ne $_.backend_generation_seconds } | ForEach-Object { [double]$_.backend_generation_seconds })
        [pscustomobject][ordered]@{
            backend = $Rows[0].backend
            model_variant = $Rows[0].model_variant
            placement = $Rows[0].placement
            runs = $Rows.Count
            wall_mean_seconds = [math]::Round(($Wall | Measure-Object -Average).Average, 3)
            wall_median_seconds = [math]::Round((Get-Median $Wall), 3)
            wall_min_seconds = [math]::Round(($Wall | Measure-Object -Minimum).Minimum, 3)
            backend_mean_seconds = if ($BackendTimes.Count) { [math]::Round(($BackendTimes | Measure-Object -Average).Average, 3) } else { $null }
            peak_vram_mib = ($Rows | Measure-Object peak_vram_mib -Maximum).Maximum
            peak_power_w = ($Rows | Measure-Object peak_power_w -Maximum).Maximum
        }
    }
    $Summary | Export-Csv -LiteralPath $SummaryPath -NoTypeInformation -Encoding utf8
    Write-Host ""
    Write-Host "Benchmark results: $RunDir" -ForegroundColor Green
    $Summary | Format-Table -AutoSize
    } finally {
        [System.Threading.Thread]::CurrentThread.CurrentCulture = $OriginalCulture
    }
}

try {
    foreach ($Backend in $Backends) {
        switch ($Backend) {
            "worker" { Invoke-WorkerBenchmarks }
            "cli" { Invoke-CliBenchmarks }
            "comfyui" { Invoke-ComfyBenchmarks }
        }
    }
} finally {
    if ($null -ne $OwnedWorker) {
        Stop-CapturedProcess $OwnedWorker (Join-Path $RunDir "worker\worker.stdout.log") (Join-Path $RunDir "worker\worker.stderr.log")
    }
    if ($null -ne $OwnedComfy) {
        Stop-CapturedProcess $OwnedComfy (Join-Path $RunDir "comfyui\comfy.stdout.log") (Join-Path $RunDir "comfyui\comfy.stderr.log")
    }
    Write-Results
    $HttpClient.Dispose()
}
