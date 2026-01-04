<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useGenerationStore } from '@/stores/generation'

const store = useGenerationStore()
const route = useRoute()

const currentMode = computed(() => {
  if (route.path === '/img2img') return 'img2img'
  if (route.path === '/inpainting') return 'inpainting'
  return 'txt2img'
})

const isGenerationPage = computed(() => {
  // Only show on main generation pages
  return ['/', '/img2img', '/inpainting'].includes(route.path)
})

function handleGenerate() {
  store.triggerGeneration(currentMode.value)
}

function toggleEndless() {
  store.isEndless = !store.isEndless
  // If we turned it ON and are not currently generating, start it
  if (store.isEndless && !store.isGenerating) {
    handleGenerate()
  }
}
</script>

<template>
  <div v-if="isGenerationPage" 
       class="action-bar shadow-sm border-bottom border-top"
       :class="[store.actionBarPosition === 'top' ? 'bar-top' : 'bar-bottom']">
    <div class="container-fluid d-flex justify-content-start align-items-center gap-2 py-2 px-3">
      
      <!-- Primary Action: Generate -->
      <button
        class="btn btn-primary generate-btn d-flex align-items-center gap-2 px-4 py-1 fw-bold position-relative"
        @click="handleGenerate"
        :disabled="store.isModelSwitching || !store.prompt"
      >
        <div class="icon-wrapper d-flex align-items-center justify-content-center">
          <span v-if="store.isGenerating && store.queueCount === 0" class="spinner-border spinner-border-sm" role="status"></span>
          <i v-else-if="store.isGenerating" class="bi bi-layers-fill"></i>
          <i v-else class="bi bi-play-fill fs-5"></i>
        </div>
        <span class="btn-text text-start">
          {{ store.isGenerating ? (store.queueCount > 0 ? 'Queue' : 'Generating...') : (store.isModelSwitching ? 'Switching...' : 'Generate') }}
        </span>
        
        <span v-if="store.queueCount > 0" class="position-absolute top-0 start-100 translate-middle badge rounded-pill bg-danger border border-light">
          {{ store.queueCount }}
          <span class="visually-hidden">queued items</span>
        </span>
      </button>

      <div class="d-flex gap-2 ms-2">
        <button 
          class="btn btn-sm border-0 transition-all" 
          :class="store.isEndless ? 'btn-success text-white shadow-sm' : 'btn-outline-secondary'"
          :title="store.isEndless ? 'Endless Generation: ON' : 'Endless Generation: OFF'"
          @click="toggleEndless"
        >
          <i class="bi bi-repeat"></i>
        </button>
      </div>

      <div class="vr mx-2 text-muted opacity-25"></div>

      <!-- History Navigation -->
      <div class="btn-group shadow-sm" role="group" v-if="store.history.length > 0">
        <button 
          type="button" 
          class="btn btn-outline-secondary d-flex align-items-center justify-content-center px-3" 
          @click="store.goBack()"
          :disabled="!store.canGoBack"
          title="Previous Generation (Restore Parameters)"
        >
          <i class="bi bi-chevron-left"></i>
        </button>
        <div class="input-group-text bg-body border-top border-bottom border-secondary-subtle px-3 fw-mono">
          <span class="small">{{ store.historyIndex + 1 }} <span class="text-muted">/</span> {{ store.history.length }}</span>
        </div>
        <button 
          type="button" 
          class="btn btn-outline-secondary d-flex align-items-center justify-content-center px-3" 
          @click="store.goForward()"
          :disabled="!store.canGoForward"
          title="Next Generation"
        >
          <i class="bi bi-chevron-right"></i>
        </button>
      </div>

    </div>
  </div>
</template>

<style scoped>
.action-bar {
  position: sticky;
  z-index: 1000;
  background-color: var(--bs-body-bg);
  width: 100%;
  transition: all 0.3s ease;
}

.bar-bottom {
  bottom: 0;
  margin-top: auto;
  border-top: 1px solid var(--bs-border-color) !important;
  border-bottom: none !important;
}

.bar-top {
  top: 0;
  border-bottom: 1px solid var(--bs-border-color) !important;
  border-top: none !important;
}

.generate-btn {
  border-radius: var(--bs-border-radius);
  min-width: 180px;
  height: 40px;
  justify-content: center;
}

.icon-wrapper {
  width: 20px;
  height: 20px;
}

.btn-text {
  min-width: 100px;
}

.transition-all {
  transition: all 0.2s ease-in-out;
}
</style>