<script setup lang="ts">
import { computed } from 'vue'
import { useGenerationStore } from '@/stores/generation'
import { useRouter } from 'vue-router'

const store = useGenerationStore()
const router = useRouter()

const parametersString = computed(() => {
  if (!store.lastParams) return ''
  const p = store.lastParams
  const modelName = store.models.find(m => m.id === store.currentModel)?.id || 'unknown'
  
  let s = `${p.prompt}\n`
  if (p.negative_prompt) {
    s += `Negative prompt: ${p.negative_prompt}\n`
  }
  
  const samplerName = p.sampler.toLowerCase().replace(' a', '_a').replace(/\+\+/g, 'pp')
  
  s += `Steps: ${p.steps}, `
  s += `Sampler: ${samplerName}, `
  s += `CFG scale: ${p.cfgScale}, `
  s += `Seed: ${p.seed}, `
  s += `Size: ${p.width}x${p.height}, `
  s += `Model: ${modelName}, `
  if (p.total_generation_time) {
    s += `Time: ${p.total_generation_time.toFixed(2)}s, `
  }
  s += `Version: stable-diffusion.cpp`
  
  return s
})

function copyParameters() {
  if (parametersString.value) {
    navigator.clipboard.writeText(parametersString.value)
  }
}

function sendToImg2Img(url: string) {
  store.initImage = url
  router.push('/img2img')
}
</script>

<template>
  <div class="card shadow-sm h-100 border-0">
    <div class="card-body d-flex flex-column p-3">
      <div class="image-display-container flex-grow-1 mb-3">
        <!-- Loading State -->
        <div v-if="store.isGenerating" class="d-flex flex-column align-items-center justify-content-center h-100 text-muted p-5 bg-dark bg-opacity-10 rounded">
          <div class="spinner-border text-primary mb-3" role="status">
            <span class="visually-hidden">Loading...</span>
          </div>
          <h5 class="text-primary mb-1">{{ store.progressPhase }}</h5>
          <p class="mb-3 small">Generating image(s)...</p>
          
          <!-- Progress Bar -->
          <div v-if="store.progressSteps > 0" class="w-100 px-4" style="max-width: 400px;">
            <div class="progress mb-2" style="height: 12px;">
              <div 
                class="progress-bar progress-bar-striped progress-bar-animated" 
                role="progressbar" 
                :style="{ width: (store.progressStep / store.progressSteps * 100) + '%' }"
                :aria-valuenow="store.progressStep" 
                :aria-valuemin="0" 
                :aria-valuemax="store.progressSteps"
              ></div>
            </div>
            <div class="d-flex justify-content-between x-small fw-bold">
              <span>Step {{ store.progressStep }} / {{ store.progressSteps }}</span>
              <span>
                <span class="me-3">⏱ {{ store.progressTime.toFixed(1) }}s</span>
                <span v-if="store.eta > 0">Remaining: ~{{ store.eta }}s</span>
              </span>
            </div>
          </div>
        </div>

        <!-- Error State -->
        <div v-else-if="store.error" class="alert alert-danger h-100 mb-0">
          <h4 class="alert-heading">Error</h4>
          <p>{{ store.error }}</p>
        </div>

        <!-- Results Grid -->
        <div v-else-if="store.imageUrls.length > 0" class="results-wrapper h-100">
          <div 
            v-for="(url, index) in store.imageUrls" 
            :key="index" 
            class="d-flex flex-column align-items-center h-100"
          >
            <div class="image-box flex-grow-1">
              <a :href="url" target="_blank" class="d-flex align-items-center justify-content-center h-100">
                <img 
                  :src="url" 
                  :alt="'Generated Image ' + (index + 1)" 
                  class="result-img"
                />
              </a>
            </div>
            <div class="py-3 w-100 text-center border-top mt-3 bg-body-tertiary rounded-bottom">
              <button class="btn btn-sm btn-outline-success px-4" @click="sendToImg2Img(url)">
                🖼️ Send to Img2Img
              </button>
            </div>
          </div>
        </div>

        <!-- Empty State -->
        <div v-else class="d-flex align-items-center justify-content-center h-100 text-muted border rounded bg-light bg-opacity-10">
          <p class="mb-0 italic">No images generated yet.</p>
        </div>
      </div>

      <!-- Metadata Section -->
      <div v-if="store.imageUrls.length > 0 && !store.isGenerating && store.lastParams" class="metadata-pane p-3 rounded bg-body-secondary small">
        <div class="mb-2">
          <span class="fw-bold text-muted text-uppercase x-small d-block mb-1">Prompt</span>
          <div class="prompt-text">{{ store.lastParams.prompt }}</div>
        </div>
        <div class="row g-3">
          <div class="col-6 col-md-3">
            <span class="fw-bold text-muted text-uppercase x-small d-block mb-1">Dimensions</span>
            {{ store.lastParams.width }} x {{ store.lastParams.height }}
          </div>
          <div class="col-6 col-md-3">
            <span class="fw-bold text-muted text-uppercase x-small d-block mb-1">Steps</span>
            {{ store.lastParams.steps }}
          </div>
          <div class="col-6 col-md-3">
            <span class="fw-bold text-muted text-uppercase x-small d-block mb-1">CFG Scale</span>
            {{ store.lastParams.cfgScale }}
          </div>
          <div class="col-6 col-md-3">
            <span class="fw-bold text-muted text-uppercase x-small d-block mb-1">Sampler</span>
            {{ store.lastParams.sampler }}
          </div>
          <div v-if="store.lastParams.total_generation_time" class="col-6 col-md-3">
            <span class="fw-bold text-muted text-uppercase x-small d-block mb-1">Time</span>
            {{ store.lastParams.total_generation_time.toFixed(2) }}s
          </div>
        </div>
        
        <div class="mt-3 pt-3 border-top">
          <div class="d-flex justify-content-between align-items-center mb-1">
            <span class="fw-bold text-muted text-uppercase x-small">A1111 / Forge Format</span>
            <button class="btn btn-link btn-sm p-0 text-decoration-none x-small" @click="copyParameters">
              📋 Copy
            </button>
          </div>
          <pre class="bg-dark bg-opacity-25 p-2 rounded x-small mb-0 text-break-all white-space-pre-wrap">{{ parametersString }}</pre>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.text-break-all {
  word-break: break-all;
}
.white-space-pre-wrap {
  white-space: pre-wrap;
}
.image-display-container {
  min-height: 450px;
}

.results-wrapper {
  overflow: hidden;
}

.image-box {
  width: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
}

.result-img {
  max-width: 100%;
  max-height: 60vh;
  width: auto;
  height: auto;
  object-fit: scale-down;
  display: block;
}

.metadata-pane {
  border-left: 4px solid var(--bs-primary);
}

.prompt-text {
  line-height: 1.4;
  color: var(--bs-body-color);
}

.x-small {
  font-size: 0.7rem;
}

.italic {
  font-style: italic;
}
</style>
