<script setup lang="ts">
import { ref, computed } from 'vue'
import { useGenerationStore } from '@/stores/generation'
import { useRouter } from 'vue-router'

const store = useGenerationStore()
const router = useRouter()
const isModelMenuOpen = ref(false)
const isLlmMenuOpen = ref(false)

const toggleModelMenu = () => {
  isModelMenuOpen.value = !isModelMenuOpen.value
  isLlmMenuOpen.value = false
  if (isModelMenuOpen.value && store.imagePresets.length === 0) store.fetchPresets()
}

const toggleLlmMenu = () => {
  isLlmMenuOpen.value = !isLlmMenuOpen.value
  isModelMenuOpen.value = false
  if (isLlmMenuOpen.value && store.llmPresets.length === 0) store.fetchPresets()
}

const selectPreset = (id: number) => {
  store.loadImagePreset(id)
  isModelMenuOpen.value = false
}

const selectLlmPreset = (id: number) => {
  store.loadLlmPreset(id)
  isLlmMenuOpen.value = false
}

// We check if current loaded model matches a preset
const activePresetName = computed(() => {
    if (store.currentImagePresetId > 0) {
        const p = store.imagePresets.find(p => p.id === store.currentImagePresetId)
        if (p) return p.name
    }
    // Fallback: try to match by UNet path
    if (store.currentModel) {
        const p = store.imagePresets.find(p => p.unet_path === store.currentModel)
        if (p) return p.name
        return store.currentModel.split('/').pop() // Show raw name if no preset matches
    }
    return 'Select Preset'
})

const activeLlmPresetName = computed(() => {
    if (store.currentLlmPresetId > 0) {
        const p = store.llmPresets.find(p => p.id === store.currentLlmPresetId)
        if (p) return p.name
    }
    // Fallback
    if (store.currentLlmModel) {
        const p = store.llmPresets.find(p => p.model_path === store.currentLlmModel)
        if (p) return p.name
        return store.currentLlmModel.split('/').pop()
    }
    return 'None'
})

// Close menu when clicking outside - handled by Sidebar or individual components
defineExpose({
  closeMenus: () => {
    isModelMenuOpen.value = false
    isLlmMenuOpen.value = false
  }
})

const navigateToManager = (tab: string) => {
    router.push('/manager/presets/' + tab)
    isModelMenuOpen.value = false
    isLlmMenuOpen.value = false
}
</script>

<template>
  <div class="model-selectors">
    <!-- Full Sidebar State -->
    <template v-if="!store.isSidebarCollapsed">
      <!-- SD Preset Section -->
      <div class="mb-2 px-1">
        <div class="d-flex justify-content-between align-items-center mb-1">
          <h6 class="x-small text-uppercase fw-bold text-muted mb-0">Image Preset</h6>
          <div v-if="store.isModelSwitching || store.isModelsLoading" class="spinner-border spinner-border-sm text-primary" style="width: 0.7rem; height: 0.7rem;" role="status">
            <span class="visually-hidden">Loading...</span>
          </div>
          <button v-else class="btn btn-link p-0 text-decoration-none x-small" @click="navigateToManager('image')" title="Manage Presets"><i class="bi bi-gear"></i></button>
        </div>
        
        <div class="dropdown w-100">
           <button class="btn btn-sm btn-outline-secondary w-100 text-start text-truncate d-flex justify-content-between align-items-center bg-body" type="button" @click.stop="toggleModelMenu">
             <span>{{ activePresetName }}</span>
             <i class="bi bi-chevron-down x-small"></i>
           </button>
           
           <div class="dropdown-menu w-100 show" v-if="isModelMenuOpen" style="position: absolute; transform: translate3d(0px, 32px, 0px); inset: 0px auto auto 0px;">
                <div v-if="store.imagePresets.length === 0" class="p-2 text-center text-muted small">
                    No presets found.<br>
                    <a href="#" @click.prevent="navigateToManager('image')">Create one here</a>
                </div>
                <button v-for="p in store.imagePresets" :key="p.id" class="dropdown-item small" @click="selectPreset(p.id)" :class="{ active: p.id === store.currentImagePresetId }">
                    {{ p.name }}
                </button>
           </div>
        </div>
      </div>

      <!-- LLM Preset Section -->
      <div class="mb-4 px-1">
        <div class="d-flex justify-content-between align-items-center mb-1">
          <h6 class="x-small text-uppercase fw-bold text-muted mb-0">Intelligence</h6>
          <div v-if="store.isLlmLoading" class="spinner-border spinner-border-sm text-success" style="width: 0.7rem; height: 0.7rem;" role="status">
            <span class="visually-hidden">Loading...</span>
          </div>
          <button v-else-if="!store.isLlmLoading" class="btn btn-link p-0 text-decoration-none x-small ms-auto me-2" @click="navigateToManager('llm')" title="Manage Presets"><i class="bi bi-gear"></i></button>
          <span v-if="store.currentLlmModel" class="badge rounded-pill x-small py-1" :class="store.isLlmLoaded ? 'text-bg-success' : 'text-bg-secondary'" :title="store.isLlmLoaded ? 'Loaded in VRAM' : 'Standby (on disk)'">
            {{ store.isLlmLoaded ? 'VRAM' : 'STANDBY' }}
          </span>
        </div>
        
        <div class="dropdown w-100">
           <button class="btn btn-sm btn-outline-secondary w-100 text-start text-truncate d-flex justify-content-between align-items-center bg-body" type="button" @click.stop="toggleLlmMenu">
             <span>{{ activeLlmPresetName }}</span>
             <i class="bi bi-chevron-down x-small"></i>
           </button>
           
           <div class="dropdown-menu w-100 show" v-if="isLlmMenuOpen" style="position: absolute; transform: translate3d(0px, 32px, 0px); inset: 0px auto auto 0px;">
                <div v-if="store.llmPresets.length === 0" class="p-2 text-center text-muted small">
                    No presets found.<br>
                    <a href="#" @click.prevent="navigateToManager('llm')">Create one here</a>
                </div>
                <button v-for="p in store.llmPresets" :key="p.id" class="dropdown-item small" @click="selectLlmPreset(p.id)" :class="{ active: p.id === store.currentLlmPresetId }">
                    {{ p.name }}
                </button>
           </div>
        </div>
      </div>
    </template>

    <!-- Collapsed Sidebar State -->
    <div class="mb-4 text-center d-flex flex-column gap-3" v-else>
      <div class="model-dropdown-container">
        <button 
          class="btn btn-link p-0 border-0 fs-4 cursor-pointer text-decoration-none" 
          :class="{ 'pulse-animation': store.isModelSwitching || store.isModelsLoading }"
          type="button" 
          @click.stop="toggleModelMenu"
          title="Switch Image Preset"
        >
          <i class="bi bi-image"></i>
        </button>
        
        <!-- SD Custom Dropdown -->
        <div v-if="isModelMenuOpen" class="custom-dropdown shadow border rounded bg-body text-start">
          <div class="dropdown-header border-bottom py-2 px-3 fw-bold small text-uppercase">Image Presets</div>
          <div class="dropdown-list">
            <div v-if="store.imagePresets.length === 0" class="p-3 text-muted small text-center italic">No presets.</div>
            <template v-else>
              <button 
                v-for="p in store.imagePresets" 
                :key="p.id"
                class="dropdown-item-btn w-100 text-start border-0 bg-transparent py-2 px-3 small d-flex justify-content-between align-items-center"
                :class="{ 'active-model': p.id === store.currentImagePresetId }"
                @click="selectPreset(p.id)"
              >
                <span class="text-truncate">{{ p.name }}</span>
                <i v-if="p.id === store.currentImagePresetId" class="bi bi-check-lg ms-2"></i>
              </button>
            </template>
            <div class="border-top p-2 text-center">
                <a href="#" @click.prevent="navigateToManager('image')" class="small text-decoration-none">Manage Presets</a>
            </div>
          </div>
        </div>
      </div>

      <div class="llm-dropdown-container">
        <button 
          class="btn btn-link p-0 border-0 fs-4 cursor-pointer text-decoration-none position-relative" 
          :class="{ 'pulse-animation': store.isLlmLoading }"
          type="button" 
          @click.stop="toggleLlmMenu"
          title="Switch Intelligence Preset"
        >
          <i class="bi bi-cpu"></i>
          <span v-if="store.currentLlmModel" class="position-absolute bottom-0 end-0 translate-middle-x p-1 border border-light rounded-circle" :class="store.isLlmLoaded ? 'bg-success' : 'bg-secondary'" style="width: 8px; height: 8px;"></span>
        </button>

        <!-- LLM Custom Dropdown -->
        <div v-if="isLlmMenuOpen" class="custom-dropdown shadow border rounded bg-body text-start">
          <div class="dropdown-header border-bottom py-2 px-3 fw-bold small text-uppercase">Intelligence Presets</div>
          <div class="dropdown-list">
             <div v-if="store.llmPresets.length === 0" class="p-3 text-muted small text-center italic">No presets.</div>
            <button 
              v-for="p in store.llmPresets" 
              :key="p.id"
              class="dropdown-item-btn w-100 text-start border-0 bg-transparent py-2 px-3 small d-flex justify-content-between align-items-center"
              :class="{ 'active-model-llm': p.id === store.currentLlmPresetId }"
              @click="selectLlmPreset(p.id)"
            >
              <span class="text-truncate">{{ p.name }}</span>
              <i v-if="p.id === store.currentLlmPresetId" class="bi bi-check-lg ms-2"></i>
            </button>
             <div class="border-top p-2 text-center">
                <a href="#" @click.prevent="navigateToManager('llm')" class="small text-decoration-none">Manage Presets</a>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
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
</style>
