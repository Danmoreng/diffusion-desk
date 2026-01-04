
# Test Presets API
$baseUrl = "http://localhost:1234"

# 1. Create Image Preset
$presetBody = @{
    name = "Test SDXL Preset"
    unet_path = "sd_xl_base_1.0.safetensors"
    vae_path = "sdxl_vae.safetensors"
    vram_weights_mb_estimate = 6500
} | ConvertTo-Json

Write-Host "Creating Image Preset..."
try {
    $res = Invoke-RestMethod -Uri "$baseUrl/v1/presets/image" -Method Post -Body $presetBody -ContentType "application/json"
    Write-Host "Success: $($res.status)"
} catch {
    Write-Host "Error: $_"
}

# 2. List Image Presets
Write-Host "Listing Image Presets..."
$presets = Invoke-RestMethod -Uri "$baseUrl/v1/presets/image" -Method Get
Write-Host "Found $($presets.Count) presets."
$presets | ForEach-Object { Write-Host "- $($_.name) (ID: $($_.id))" }

# 3. Create LLM Preset
$llmPresetBody = @{
    name = "Test Vision Preset"
    model_path = "Qwen2-VL-7B-Instruct-Q4_K_M.gguf"
    mmproj_path = "mmproj-Qwen2-VL-7B-Instruct-Q4_K_M.gguf"
    n_ctx = 4096
    role = "Vision"
} | ConvertTo-Json

Write-Host "Creating LLM Preset..."
try {
    $res = Invoke-RestMethod -Uri "$baseUrl/v1/presets/llm" -Method Post -Body $llmPresetBody -ContentType "application/json"
    Write-Host "Success: $($res.status)"
} catch {
    Write-Host "Error: $_"
}

# 4. List LLM Presets
Write-Host "Listing LLM Presets..."
$llmPresets = Invoke-RestMethod -Uri "$baseUrl/v1/presets/llm" -Method Get
Write-Host "Found $($llmPresets.Count) presets."
$llmPresets | ForEach-Object { Write-Host "- $($_.name) (ID: $($_.id))" }
