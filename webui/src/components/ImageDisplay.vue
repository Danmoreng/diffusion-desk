<script setup lang="ts">
import { useGenerationStore } from '@/stores/generation'

const store = useGenerationStore()
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
          <div v-if="store.progressMessage" class="alert alert-info py-1 px-2 small mb-3">
            <i class="bi bi-info-circle me-1"></i> {{ store.progressMessage }}
          </div>
          
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
                <span class="me-3"><i class="bi bi-clock"></i> {{ store.progressTime.toFixed(1) }}s</span>
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
        <div v-else-if="store.imageUrls.length > 0" class="results-wrapper results-grid h-100">
          <div 
            v-for="(url, index) in store.imageUrls" 
            :key="index" 
            class="d-flex flex-column align-items-center result-item"
          >
            <div class="image-box flex-grow-1">
              <a :href="url" target="_blank" class="d-flex align-items-center justify-content-center w-100 h-100">
                <img 
                  :src="url" 
                  :alt="'Generated Image ' + (index + 1)" 
                  class="result-img"
                />
              </a>
            </div>

          </div>
        </div>

        <!-- Empty State -->
        <div v-else class="d-flex align-items-center justify-content-center h-100 text-muted border rounded bg-light bg-opacity-10">
          <p class="mb-0 italic">No images generated yet.</p>
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
  overflow-y: auto;
}

.results-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 1rem;
  align-content: start;
}

/* If only one image, make it take full space and center */
.results-grid:has(> .result-item:only-child) {
  display: flex;
  justify-content: center;
  align-items: center;
}

.image-box {
  width: 100%;
  height: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 200px;
}

.result-item {
  background: var(--bs-body-bg);
  border-radius: 0.5rem;
  overflow: hidden;
  box-shadow: 0 2px 4px rgba(0,0,0,0.05);
}

.result-img {
  max-width: 100%;
  max-height: 100%;
  width: auto;
  height: auto;
  object-fit: contain;
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
