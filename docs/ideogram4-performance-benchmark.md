# Ideogram 4 performance benchmark

> **Status as of June 27, 2026:** Current supporting documentation for backend
> performance benchmarking. Staged Ideogram composition generation is already
> implemented and working well.

The repeatable benchmark is `scripts/tests/benchmark_ideogram4.ps1`. It uses the
same prompt, seed, image size, sampler, CFG, and step count for each selected
backend. Every invocation creates a timestamped directory under
`temp/ideogram4-benchmarks/` with logs, images, raw CSV/JSON data, and a summary.

## Q4: worker versus CLI

```powershell
pwsh -File .\scripts\tests\benchmark_ideogram4.ps1 `
  -Backends worker,cli `
  -ModelVariant Q4 `
  -Placement Preset `
  -WarmupRuns 1 `
  -Runs 3
```

`Preset` sends the placement from the application preset. The current worker
applies its Ideogram safety policy and reports the resulting `max_vram` and
`stream_layers` values in the result notes.

For an apples-to-apples CLI run using the worker's current effective placement:

```powershell
pwsh -File .\scripts\tests\benchmark_ideogram4.ps1 `
  -Backends worker,cli `
  -ModelVariant Q4 `
  -Placement WorkerEffective `
  -WarmupRuns 1 `
  -Runs 3
```

## Q8

The Q8 profile uses all three available Q8 components: conditional diffusion,
unconditional diffusion, and Qwen3-VL-8B.

```powershell
pwsh -File .\scripts\tests\benchmark_ideogram4.ps1 `
  -Backends worker,cli `
  -ModelVariant Q8 `
  -Placement WorkerEffective `
  -WarmupRuns 1 `
  -Runs 3
```

Test the known 12 GiB streaming configuration separately:

```powershell
pwsh -File .\scripts\tests\benchmark_ideogram4.ps1 `
  -Backends cli `
  -ModelVariant Q8 `
  -Placement Stream12 `
  -WarmupRuns 1 `
  -Runs 3
```

Test the desktop application's default automatic budget, which reserves 2 GiB
of currently free VRAM:

```powershell
pwsh -File .\scripts\tests\benchmark_ideogram4.ps1 `
  -Backends worker `
  -ModelVariant Q8 `
  -Placement StreamAuto2 `
  -Runs 1
```

### RTX 5080 Laptop 16 GiB result

At 864x1152, 20 Euler steps, CFG 7, and seed 23805, one measured Q8 run
produced these wall times:

| Engine | Placement | Seconds | Peak VRAM MiB |
| --- | --- | ---: | ---: |
| ComfyUI | Dynamic VRAM | 159.951 | 12524 |
| sd-cli | Preset (`max_vram=0`) | 388.554 | 15972 |
| worker | Effective worker policy | 457.228 | 15962 |
| sd-cli | Stream12 (`max_vram=12`) | 127.894 | 10350 |
| worker | Auto reserve 2 GiB (`max_vram=-2`) | 127.835 | 11106 |

The automatic worker profile resolved 14.71 GiB free VRAM to a 12.71 GiB graph
budget and matched the fastest CLI result. Leaving
`max_vram` unlimited pushed stable-diffusion.cpp to the physical VRAM limit and
increased sampling from 120.4 seconds to 381.16 seconds. The worker was slower
before the streaming fix because it used llama.cpp's newer GGML backend, which
failed segmented tensor copies. The worker now builds separately against the
GGML revision bundled with stable-diffusion.cpp.

## ComfyUI

Ideogram 4 requires ComfyUI `v0.24.0` or newer. The tested installation uses
`v0.24.1`. The current ComfyUI-GGUF version also needs the compatibility patch
at `scripts/tests/patches/comfyui-gguf-ideogram4.patch` to recognize the
stable-diffusion.cpp diffusion GGUF files and materialize BF16 tensors safely.

Apply it from the custom node checkout if the changes are not already present:

```powershell
git apply C:\StableDiffusion\diffusion-desk\scripts\tests\patches\comfyui-gguf-ideogram4.patch
```

The ComfyUI model paths must expose `C:\StableDiffusion\models\llm` as both a
`clip` and `text_encoders` search path.

Export the working Ideogram workflow with **Save (API Format)**, then pass it to
the benchmark. Common `CLIPTextEncode`, latent, `KSampler`, and `SaveImage`
inputs are updated automatically.

```powershell
pwsh -File .\scripts\tests\benchmark_ideogram4.ps1 `
  -Backends comfyui `
  -ModelVariant Q4 `
  -ComfyWorkflowPath C:\benchmarks\ideogram4-q4-api.json `
  -WarmupRuns 1 `
  -Runs 3
```

Use `-StartComfyUI` to launch the portable installation automatically. Add
`-ComfyFastFp16Accumulation` to test its fast accumulation mode.

The benchmark starts ComfyUI with `--cache-none`. This is required because
identical same-seed workflows would otherwise return cached node outputs after
the warmup instead of running inference again.

The workflow must already run successfully in ComfyUI, and its model search
paths must expose the Ideogram diffusion, unconditional, Qwen3-VL, and VAE
files. The override tokens resolve to file names, not arbitrary absolute paths,
because ComfyUI loader nodes select models from configured model directories.

Custom workflows can supply an override JSON keyed by node ID and input name:

```json
{
  "12": {
    "model_name": "{{diffusion_model}}"
  },
  "13": {
    "model_name": "{{uncond_diffusion_model}}"
  },
  "14": {
    "clip_name": "{{llm_model}}"
  },
  "15": {
    "vae_name": "{{vae_model}}"
  }
}
```

Supported typed tokens include `{{prompt}}`, `{{negative_prompt}}`, `{{width}}`,
`{{height}}`, `{{steps}}`, `{{cfg_scale}}`, `{{seed}}`, `{{sampler}}`,
`{{scheduler}}`, and the four model tokens shown above.

## Reading the result

- `wall_seconds`: user-visible end-to-end duration.
- `backend_generation_seconds`: internal worker or stable-diffusion.cpp duration.
- For ComfyUI, `backend_generation_seconds` is the interval from
  `execution_start` to `execution_success` in its history API.
- `model_load_seconds`: separately reported model loading where available.
- `peak_vram_delta_mib`: sampled increase over the run's starting VRAM usage.
- `summary.csv`: mean, median, minimum, peak VRAM, and peak power for measured runs.
- `analysis.csv`: one flat row per engine run with phase timings and total time.

Use at least three measured runs. A warmup run removes filesystem cache and
one-time CUDA initialization from the steady-state comparison, while the raw
warmup row remains available for cold-start analysis.
