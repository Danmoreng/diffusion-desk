<script setup lang="ts">
import { ref, watch } from 'vue'
import { useGenerationStore } from '@/stores/generation'

const props = defineProps<{
  mode: 'txt2img' | 'img2img' | 'inpainting'
}>()

const store = useGenerationStore()

const n = () => {
  if (!store.prompt || store.isGenerating || store.isModelSwitching) return
      store.generateImage({
      prompt: store.prompt,
      negative_prompt: store.negativePrompt,
      steps: store.steps,
      seed: store.seed,
      cfgScale: store.cfgScale,
      strength: store.strength,
      batchCount: store.batchCount,
      sampler: store.sampler,
      width: store.width,
      height: store.height,
      saveImages: store.saveImages,
      initImage: (props.mode === 'img2img' || props.mode === 'inpainting') ? store.initImage : null,
      maskImage: props.mode === 'inpainting' ? store.maskImage : null
    })
  }
  const onFileChange = (e: Event) => {
  const target = e.target as HTMLInputElement
  if (target.files && target.files[0]) {
    const reader = new FileReader()
    reader.onload = (event) => {
      const dataUrl = event.target?.result as string
      store.initImage = dataUrl
      
      // Get image dimensions
      const img = new Image()
      img.onload = () => {
        uploadedImageWidth.value = img.width
        uploadedImageHeight.value = img.height
      }
      img.src = dataUrl
    }
    reader.readAsDataURL(target.files[0])
  }
}

const uploadedImageWidth = ref(0)
const uploadedImageHeight = ref(0)
const scaleFactor = ref(1.0)

const useImageSize = () => {
  if (uploadedImageWidth.value > 0 && uploadedImageHeight.value > 0) {
    // Round to nearest multiple of 64
    store.width = Math.round((uploadedImageWidth.value * scaleFactor.value) / 64) * 64
    store.height = Math.round((uploadedImageHeight.value * scaleFactor.value) / 64) * 64
    
    // Ensure minimum of 64
    if (store.width < 64) store.width = 64
    if (store.height < 64) store.height = 64
  }
}

watch(scaleFactor, () => {
  useImageSize()
})

const clearInitImage = () => {
  store.initImage = null
  uploadedImageWidth.value = 0
  uploadedImageHeight.value = 0
}
</script>

<template>
  <div class="card shadow-sm p-3">
    <form @submit.prevent="n">
      <!-- Img2Img / Inpainting Upload & Scaling -->
      <div class="mb-3" v-if="mode === 'img2img' || mode === 'inpainting'">
        <div v-if="mode === 'img2img'">
          <label class="form-label">Initial Image:</label>
          <div v-if="!store.initImage" class="image-upload-dropzone border rounded p-4 text-center" @click="($refs.fileInput as any).click()">
            <span class="display-6">📁</span>
            <p class="mb-0 mt-2">Click to upload or drag & drop</p>
            <input type="file" ref="fileInput" class="d-none" accept="image/*" @change="onFileChange" />
          </div>
          <div v-else class="position-relative border rounded p-2 text-center">
            <img :src="store.initImage" class="img-thumbnail" style="max-height: 200px;" />
            <div class="mt-2 px-3">
              <label class="form-label d-flex justify-content-between x-small text-muted text-uppercase fw-bold mb-1">
                <span>Upscale Factor:</span>
                <span class="text-primary">{{ scaleFactor.toFixed(1) }}x</span>
              </label>
              <input type="range" class="form-range" v-model.number="scaleFactor" min="1.0" max="2.0" step="0.1">
            </div>
            <div class="mt-2 d-flex justify-content-center flex-wrap gap-2">
              <button type="button" class="btn btn-outline-secondary btn-sm" @click="useImageSize">
                📏 Apply Scale ({{ Math.round((uploadedImageWidth * scaleFactor) / 64) * 64 }}x{{ Math.round((uploadedImageHeight * scaleFactor) / 64) * 64 }})
              </button>
              <button type="button" class="btn btn-danger btn-sm" @click="clearInitImage">
                🗑️ Clear
              </button>
            </div>
          </div>
        </div>
        
        <div v-if="mode === 'inpainting' && store.initImage" class="border rounded p-2 text-center mb-3">
            <div class="px-3">
              <label class="form-label d-flex justify-content-between x-small text-muted text-uppercase fw-bold mb-1">
                <span>Input Scale Factor:</span>
                <span class="text-primary">{{ scaleFactor.toFixed(1) }}x</span>
              </label>
              <input type="range" class="form-range" v-model.number="scaleFactor" min="1.0" max="2.0" step="0.1">
            </div>
            <button type="button" class="btn btn-outline-secondary btn-sm mt-1" @click="useImageSize">
              📏 Apply Scale to Canvas
            </button>
        </div>
      </div>

      <!-- Strength Slider (only if initImage exists) -->
      <div class="mb-3" v-if="(mode === 'img2img' || mode === 'inpainting') && store.initImage">
        <label for="strength" class="form-label d-flex justify-content-between">
          <span>Denoising Strength:</span>
          <span class="badge bg-primary">{{ store.strength }}</span>
        </label>
        <input type="range" class="form-range" id="strength" v-model.number="store.strength" min="0" max="1" step="0.01">
        <div class="form-text text-muted small">How much to change the initial image. 1.0 = completely new image.</div>
      </div>

      <div class="mb-3">
        <div class="d-flex justify-content-between align-items-center mb-2">
          <div class="d-flex align-items-center gap-2">
            <label for="prompt" class="form-label mb-0">Prompt:</label>
            <button 
              type="button" 
              class="btn btn-outline-success btn-sm py-0" 
              style="font-size: 0.7rem; height: 20px;"
              @click="store.enhancePrompt"
              :disabled="store.isLlmThinking || !store.prompt"
            >
              <span v-if="store.isLlmThinking" class="spinner-border spinner-border-sm" style="width: 0.7rem; height: 0.7rem;"></span>
              <span v-else>🪄 Enhance</span>
            </button>
          </div>
          <button 
            v-if="store.prompt.includes('Steps: ')"
            type="button" 
            class="btn btn-outline-primary btn-sm py-0" 
            style="font-size: 0.75rem;"
            @click="store.parseA1111Parameters(store.prompt)"
          >
            ✨ Apply Forge Parameters
          </button>
        </div>
        <textarea
          id="prompt"
          v-model="store.prompt"
          rows="4"
          class="form-control"
          placeholder="A cinematic photograph of..."
          required
        ></textarea>
      </div>

      <div class="mb-3">
        <label for="negativePrompt" class="form-label">Negative Prompt:</label>
        <textarea
          id="negativePrompt"
          v-model="store.negativePrompt"
          rows="3"
          class="form-control"
          placeholder="deformed, bad anatomy, blurry..."
        ></textarea>
      </div>

      <div class="row g-2 mb-3">
        <div class="col-md-4">
          <div class="input-group input-group-sm">
            <span class="input-group-text">Steps</span>
            <input
              type="number"
              v-model.number="store.steps"
              min="1"
              max="150"
              class="form-control"
            />
          </div>
        </div>
        <div class="col-md-4">
          <div class="input-group input-group-sm">
            <span class="input-group-text">Batch</span>
            <input
              type="number"
              v-model.number="store.batchCount"
              min="1"
              max="8"
              class="form-control"
            />
          </div>
        </div>
        <div class="col-md-4">
          <div class="input-group input-group-sm">
            <span class="input-group-text">Seed</span>
            <input
                type="number"
                v-model.number="store.seed"
                class="form-control"
            />
            <button type="button" class="btn btn-outline-secondary" @click="store.reuseLastSeed" title="Reuse Last Seed" :disabled="!store.lastParams">
                ♻️
            </button>
            <button type="button" class="btn btn-outline-secondary" @click="store.randomizeSeed" title="Randomize Seed">
                🎲
            </button>
          </div>
        </div>
      </div>

      <div class="row g-2 mb-3 align-items-center">
        <div class="col">
          <div class="input-group input-group-sm">
            <span class="input-group-text">Width</span>
            <input
              type="number"
              v-model.number="store.width"
              min="64"
              max="2048"
              step="64"
              class="form-control"
            />
          </div>
        </div>
        <div class="col-auto">
          <button type="button" class="btn btn-outline-secondary btn-sm" @click="store.swapDimensions" title="Swap Dimensions">
            ⇄
          </button>
        </div>
        <div class="col">
          <div class="input-group input-group-sm">
            <span class="input-group-text">Height</span>
            <input
              type="number"
              v-model.number="store.height"
              min="64"
              max="2048"
              step="64"
              class="form-control"
            />
          </div>
        </div>
      </div>
      <div v-if="store.width * store.height > 1024 * 1024" class="alert alert-warning py-1 small">
        ⚠️ High resolution detected. This may be slow or crash without VAE Tiling.
      </div>

      <div class="row g-2 mb-4">
        <div class="col-md-6">
          <div class="input-group input-group-sm">
            <span class="input-group-text">CFG</span>
            <input
              type="number"
              v-model.number="store.cfgScale"
              min="1"
              max="30"
              step="0.1"
              class="form-control"
            />
          </div>
        </div>
        <div class="col-md-6">
          <div class="input-group input-group-sm">
            <span class="input-group-text">Sampler</span>
            <select
              v-model="store.sampler"
              class="form-select"
            >
              <option v-for="s in store.samplers" :key="s" :value="s">{{ s }}</option>
            </select>
          </div>
        </div>
      </div>

      <!-- Hires-fix Section -->
      <div class="mb-4">
        <div class="form-check form-switch mb-2">
          <input class="form-check-input" type="checkbox" id="hiresFix" v-model="store.hiresFix">
          <label class="form-check-label fw-bold" for="hiresFix">Highres-fix</label>
        </div>
        
        <div v-if="store.hiresFix" class="hires-fix-container border-0 p-3 mt-2 mb-3 rounded">
          <div class="row g-2 mb-2">
            <div class="col-md-7">
              <label for="hiresUpscaler" class="x-small text-muted mb-1 d-block text-uppercase fw-bold">Upscaler:</label>
              <select id="hiresUpscaler" v-model="store.hiresUpscaleModel" class="form-select form-select-sm">
                <option value="">None (Simple Resize)</option>
                <option v-for="model in store.models.filter(m => m.type === 'esrgan')" :key="model.id" :value="model.id">
                  {{ model.name }}
                </option>
              </select>
            </div>
            <div class="col-md-5">
              <label for="hiresFactor" class="x-small text-muted mb-1 d-block text-uppercase fw-bold">Upscale by:</label>
              <input type="number" id="hiresFactor" v-model.number="store.hiresUpscaleFactor" min="1" max="4" step="0.25" class="form-control form-control-sm">
            </div>
          </div>
          
          <div class="mb-2">
            <label for="hiresDenoise" class="x-small text-muted mb-1 d-block text-uppercase fw-bold d-flex justify-content-between">
              <span>Denoising Strength:</span>
              <span class="text-primary">{{ store.hiresDenoisingStrength }}</span>
            </label>
            <input type="range" class="form-range" id="hiresDenoise" v-model.number="store.hiresDenoisingStrength" min="0" max="1" step="0.01">
          </div>

          <div class="row g-2">
            <div class="col-md-12">
              <label for="hiresSteps" class="x-small text-muted mb-1 d-block text-uppercase fw-bold">Hires Steps:</label>
              <input type="number" id="hiresSteps" v-model.number="store.hiresSteps" min="1" max="150" class="form-control form-control-sm">
            </div>
          </div>
        </div>
      </div>

      <div class="d-grid">
        <button
          type="submit"
          class="btn btn-primary"
          :disabled="store.isGenerating || store.isModelSwitching"
        >
          <span v-if="store.isGenerating" class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>
          <span v-if="store.isModelSwitching" class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>
          {{ store.isGenerating ? ' Generating...' : (store.isModelSwitching ? ' Switching model...' : 'Generate') }}
        </button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.image-upload-dropzone {
  cursor: pointer;
  transition: all 0.2s;
  background-color: #f8f9fa;
  color: #6c757d;
}

[data-bs-theme="dark"] .image-upload-dropzone {
  background-color: #2b3035 !important;
  color: #adb5bd !important;
  border-color: #495057 !important;
}

.image-upload-dropzone:hover {
  background-color: #e9ecef;
  border-color: #0d6efd !important;
}

[data-bs-theme="dark"] .image-upload-dropzone:hover {
  background-color: #373b3e !important;
  border-color: #3d8bfd !important;
}

.hires-fix-container {
  background-color: #f8f9fa;
}

[data-bs-theme="dark"] .hires-fix-container {
  background-color: #2b3035 !important;
}

.x-small {
  font-size: 0.65rem;
}
</style>
