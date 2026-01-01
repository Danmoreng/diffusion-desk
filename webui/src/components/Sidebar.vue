<script setup lang="ts">
import { onMounted, computed, watch, onUnmounted, ref } from 'vue'
import { useGenerationStore } from '@/stores/generation'
import Tooltip from 'bootstrap/js/dist/tooltip'
import VramIndicator from './VramIndicator.vue'
import ModelSelector from './ModelSelector.vue'

const store = useGenerationStore()
const modelSelectorRef = ref<any>(null)

// Close menu when clicking outside
const closeMenu = (e: MouseEvent) => {
  if (modelSelectorRef.value) {
    modelSelectorRef.value.closeMenus()
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
  { path: '/', label: 'Text-to-Image', icon: 'bi-palette' },
  { path: '/img2img', label: 'Image-to-Image', icon: 'bi-image' },
  { path: '/inpainting', label: 'Inpainting', icon: 'bi-brush' },
  { path: '/upscale', label: 'Upscale', icon: 'bi-stars' },
  { path: '/exploration', label: 'Dynamic Exploration', icon: 'bi-search' },
  { path: '/gallery', label: 'Gallery', icon: 'bi-images' },
  { path: '/manager', label: 'Library', icon: 'bi-collection' },
]
</script>

<template>
  <div class="sidebar-inner p-2 d-flex flex-column h-100 pt-5">
    
    <ModelSelector ref="modelSelectorRef" />

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
            <i class="fs-5 me-2 bi" :class="[item.icon, { 'me-0': store.isSidebarCollapsed }]"></i>
            <span v-if="!store.isSidebarCollapsed" class="text-truncate">{{ item.label }}</span>
          </router-link>
        </li>
      </ul>
    </div>

    <!-- Bottom Actions -->
    <div class="mt-auto border-top pt-2">
      <VramIndicator />

      <router-link 
        to="/settings" 
        class="nav-link d-flex align-items-center px-2 py-2 mb-1"
        :class="{ 'justify-content-center': store.isSidebarCollapsed }"
        title="Settings"
        data-bs-toggle="tooltip"
        data-bs-placement="right"
      >
        <i class="fs-5 me-2 bi bi-gear" :class="{ 'me-0': store.isSidebarCollapsed }"></i>
        <span v-if="!store.isSidebarCollapsed">Settings</span>
      </router-link>
    </div>
  </div>
</template>

<style scoped>
.sidebar-inner {
  overflow: visible !important;
}

.model-dropdown-container, .llm-dropdown-container {
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

.dropdown-item-btn.active-model-llm {
  background-color: var(--bs-success) !important;
  color: white !important;
  font-weight: 600;
}

.dropdown-item-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.pulse-animation {
  animation: pulse 1.5s infinite ease-in-out;
}

@keyframes pulse {
  0% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.2); opacity: 0.7; }
  100% { transform: scale(1); opacity: 1; }
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
