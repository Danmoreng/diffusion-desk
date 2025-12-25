<script setup lang="ts">
import { onMounted, computed, watch, onUnmounted, ref } from 'vue'
import { useGenerationStore } from '@/stores/generation'
import Tooltip from 'bootstrap/js/dist/tooltip'

const store = useGenerationStore()
const miniModelBtn = ref<HTMLElement | null>(null)
const isModelMenuOpen = ref(false)

const toggleModelMenu = () => {
  isModelMenuOpen.value = !isModelMenuOpen.value
}

const selectModel = (id: string) => {
  store.loadModel(id)
  isModelMenuOpen.value = false
}

// Close menu when clicking outside
const closeMenu = (e: MouseEvent) => {
  if (isModelMenuOpen.value && !(e.target as HTMLElement).closest('.model-dropdown-container')) {
    isModelMenuOpen.value = false
  }
}

let tooltips: Tooltip[] = []

const initTooltips = () => {
  // Clear existing
  tooltips.forEach(t => t.dispose())
  tooltips = []
  
  if (store.isSidebarCollapsed) {
    // Wait for DOM to update
    setTimeout(() => {
      // Regular nav links
      const els = document.querySelectorAll('.nav-link[data-bs-toggle="tooltip"], .vram-bar-container[data-bs-toggle="tooltip"]')
      els.forEach(el => {
        tooltips.push(new Tooltip(el, { trigger: 'hover' }))
      })
    }, 100)
  }
}

onMounted(() => {
  store.fetchModels()
  initTooltips()
  window.addEventListener('click', closeMenu)
})

onUnmounted(() => {
  tooltips.forEach(t => t.dispose())
  window.removeEventListener('click', closeMenu)
})

watch(() => store.isSidebarCollapsed, () => {
  initTooltips()
})

const handleModelChange = (event: Event) => {
  const target = event.target as HTMLSelectElement
  if (target.value) {
    store.loadModel(target.value)
  }
}

const menuItems = [
  { path: '/', label: 'Text-to-Image', icon: '🎨' },
  { path: '/img2img', label: 'Image-to-Image', icon: '🖼️' },
  { path: '/inpainting', label: 'Inpainting', icon: '🖌️' },
  { path: '/upscale', label: 'Upscale', icon: '✨' },
  { path: '/exploration', label: 'Dynamic Exploration', icon: '🔍' },
  { path: '/history', label: 'History', icon: '📜' },
]
</script>

<template>
  <div class="sidebar-inner p-2 d-flex flex-column h-100 pt-5">
    <!-- Model Section -->
    <div class="mb-4 px-1" v-if="!store.isSidebarCollapsed">
      <h6 class="mb-2 x-small text-uppercase fw-bold text-muted">Model</h6>
      <select 
        class="form-select form-select-sm mb-2" 
        :value="store.currentModel" 
        @change="handleModelChange"
        :disabled="store.isModelSwitching || store.isGenerating"
      >
        <option v-if="store.isModelsLoading" disabled>Loading...</option>
        <template v-else>
          <option v-for="model in store.models.filter(m => m.type === 'stable-diffusion' || m.type === 'root')" :key="model.id" :value="model.id">
            {{ model.name }}
          </option>
        </template>
      </select>
    </div>
    
    <!-- Mini Model Icon (for collapsed state) -->
    <div class="mb-4 text-center model-dropdown-container" v-else>
      <button 
        class="btn btn-link p-0 border-0 fs-4 cursor-pointer text-decoration-none" 
        type="button" 
        @click.stop="toggleModelMenu"
        title="Switch Model"
      >
        📦
      </button>
      
      <!-- Custom Absolute Dropdown -->
      <div v-if="isModelMenuOpen" class="custom-dropdown shadow border rounded bg-body">
        <div class="dropdown-header border-bottom py-2 px-3 fw-bold small text-uppercase">Select Model</div>
        <div class="dropdown-list">
          <div v-if="store.isModelsLoading" class="p-3 text-muted small text-center italic">Loading...</div>
          <template v-else>
            <button 
              v-for="model in store.models.filter(m => m.type === 'stable-diffusion' || m.type === 'root')" 
              :key="model.id"
              class="dropdown-item-btn w-100 text-start border-0 bg-transparent py-2 px-3 small d-flex justify-content-between align-items-center"
              :class="{ 'active-model': model.id === store.currentModel }"
              @click="selectModel(model.id)"
              :disabled="store.isModelSwitching || store.isGenerating"
            >
              <span class="text-truncate">{{ model.name }}</span>
              <span v-if="model.id === store.currentModel" class="ms-2">✅</span>
            </button>
          </template>
        </div>
      </div>
    </div>

    <!-- Menu Section -->
    <div class="flex-grow-1">
      <h6 class="mb-2 px-1 x-small text-uppercase fw-bold text-muted" v-if="!store.isSidebarCollapsed">Menu</h6>
      <ul class="nav nav-pills flex-column gap-1">
        <li class="nav-item" v-for="item in menuItems" :key="item.path">
          <router-link 
            :to="item.path" 
            class="nav-link d-flex align-items-center px-2 py-2"
            :class="{ 'justify-content-center': store.isSidebarCollapsed }"
            :title="store.isSidebarCollapsed ? item.label : ''"
            data-bs-toggle="tooltip"
            data-bs-placement="right"
          >
            <span class="fs-5 me-2" :class="{ 'me-0': store.isSidebarCollapsed }">{{ item.icon }}</span>
            <span v-if="!store.isSidebarCollapsed" class="text-truncate">{{ item.label }}</span>
          </router-link>
        </li>
      </ul>
    </div>

    <!-- Bottom Actions -->
    <div class="mt-auto border-top pt-2">
      <!-- VRAM Info -->
      <div class="px-1 mb-3" v-if="store.vramInfo.total > 0">
        <div class="d-flex justify-content-between align-items-center mb-1" v-if="!store.isSidebarCollapsed">
          <span class="x-small text-uppercase fw-bold text-muted">VRAM Usage</span>
          <span class="x-small font-monospace">{{ (store.vramInfo.total - store.vramInfo.free).toFixed(1) }}/{{ store.vramInfo.total.toFixed(0) }}GB</span>
        </div>
        
        <div 
          class="vram-bar-container" 
          :title="store.isSidebarCollapsed ? `VRAM: ${(store.vramInfo.total - store.vramInfo.free).toFixed(1)}/${store.vramInfo.total.toFixed(0)}GB` : ''"
          data-bs-toggle="tooltip"
          data-bs-placement="right"
        >
          <div class="vram-bar">
            <div 
              class="vram-segment sd" 
              :style="{ width: (store.vramInfo.sd / store.vramInfo.total * 100) + '%' }"
              title="Stable Diffusion"
            ></div>
            <div 
              class="vram-segment llm" 
              :style="{ width: (store.vramInfo.llm / store.vramInfo.total * 100) + '%' }"
              title="LLM"
            ></div>
            <div 
              class="vram-segment other" 
              :style="{ width: ((store.vramInfo.total - store.vramInfo.free - store.vramInfo.sd - store.vramInfo.llm) / store.vramInfo.total * 100) + '%' }"
              title="System/Other"
            ></div>
          </div>
        </div>

        <div class="vram-legend mt-2" v-if="!store.isSidebarCollapsed">
          <div class="d-flex align-items-center gap-1 x-small mb-1" title="SD Worker (Text Encoder + UNet)">
            <span class="legend-dot sd"></span>
            <span class="text-muted">SD:</span>
            <span class="ms-auto font-monospace">{{ store.vramInfo.sd.toFixed(1) }}GB</span>
          </div>
          <div class="d-flex align-items-center gap-1 x-small mb-1" title="LLM Worker">
            <span class="legend-dot llm"></span>
            <span class="text-muted">LLM:</span>
            <span class="ms-auto font-monospace">{{ store.vramInfo.llm.toFixed(1) }}GB</span>
          </div>
          <div class="d-flex align-items-center gap-1 x-small" title="Other processes using GPU">
            <span class="legend-dot other"></span>
            <span class="text-muted">Sys:</span>
            <span class="ms-auto font-monospace">{{ Math.max(0, store.vramInfo.total - store.vramInfo.free - store.vramInfo.sd - store.vramInfo.llm).toFixed(1) }}GB</span>
          </div>
        </div>
      </div>

      <router-link 
        to="/settings" 
        class="nav-link d-flex align-items-center px-2 py-2 mb-1"
        :class="{ 'justify-content-center': store.isSidebarCollapsed }"
        title="Settings"
        data-bs-toggle="tooltip"
        data-bs-placement="right"
      >
        <span class="fs-5 me-2" :class="{ 'me-0': store.isSidebarCollapsed }">⚙️</span>
        <span v-if="!store.isSidebarCollapsed">Settings</span>
      </router-link>
    </div>
  </div>
</template>

<style scoped>
.sidebar-inner {
  overflow: visible !important;
}

.model-dropdown-container {
  position: relative;
}

.custom-dropdown {
  position: absolute;
  left: 100%;
  top: 0;
  margin-left: 10px;
  width: 280px;
  max-height: 400px;
  overflow-y: auto;
  z-index: 9999 !important;
  background-color: var(--bs-body-bg);
}

.dropdown-item-btn {
  color: var(--bs-body-color);
  transition: all 0.15s ease-in-out;
  border-radius: 4px;
}

.dropdown-item-btn:hover:not(:disabled) {
  background-color: var(--bs-primary-bg-subtle) !important;
  color: var(--bs-primary-text-emphasis);
}

.dropdown-item-btn.active-model {
  background-color: var(--bs-primary) !important;
  color: white !important;
  font-weight: 600;
}

.dropdown-item-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.x-small {
  font-size: 0.65rem;
  letter-spacing: 0.05rem;
}

.nav-link {
  color: var(--bs-body-color);
  border-radius: 8px;
  transition: background-color 0.2s;
  white-space: nowrap;
}

.nav-link:hover {
  background-color: var(--bs-secondary-bg);
}

.router-link-active {
  background-color: var(--bs-primary) !important;
  color: white !important;
}

.cursor-pointer {
  cursor: pointer;
}

.no-caret::after {
  display: none !important;
}

.dropdown-menu {
  z-index: 9999 !important;
}

.vram-bar-container {
  padding: 2px 0;
}

.vram-bar {
  height: 8px;
  background-color: var(--bs-secondary-bg);
  border-radius: 4px;
  display: flex;
  overflow: hidden;
  border: 1px solid var(--bs-border-color);
}

.vram-segment {
  height: 100%;
  transition: width 0.5s ease-in-out;
}

.vram-segment.sd {
  background-color: var(--bs-primary);
}

.vram-segment.llm {
  background-color: var(--bs-success);
}

.vram-segment.other {
  background-color: var(--bs-warning);
}

.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}

.legend-dot.sd { background-color: var(--bs-primary); }
.legend-dot.llm { background-color: var(--bs-success); }
.legend-dot.other { background-color: var(--bs-warning); }

.vram-legend {
  user-select: none;
}
</style>
