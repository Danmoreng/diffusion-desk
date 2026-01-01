<script setup lang="ts">
import { ref } from 'vue'
import { useGenerationStore } from '@/stores/generation'

const store = useGenerationStore()
const isModelMenuOpen = ref(false)
const isLlmMenuOpen = ref(false)

const toggleModelMenu = () => {
  isModelMenuOpen.value = !isModelMenuOpen.value
  isLlmMenuOpen.value = false
}

const toggleLlmMenu = () => {
  isLlmMenuOpen.value = !isLlmMenuOpen.value
  isModelMenuOpen.value = false
}

const selectModel = (id: string) => {
  store.loadModel(id)
  isModelMenuOpen.value = false
}

const selectLlmModel = (id: string) => {
  store.loadLlmModel(id)
  isLlmMenuOpen.value = false
}

const handleModelChange = (event: Event) => {
  const target = event.target as HTMLSelectElement
  if (target.value) {
    store.loadModel(target.value)
  }
}

// Close menu when clicking outside - handled by Sidebar or individual components
defineExpose({
  closeMenus: () => {
    isModelMenuOpen.value = false
    isLlmMenuOpen.value = false
  }
})
</script>

<template>
  <div class="model-selectors">
    <!-- Full Sidebar State -->
    <template v-if="!store.isSidebarCollapsed">
      <!-- SD Model Section -->
      <div class="mb-2 px-1">
        <div class="d-flex justify-content-between align-items-center mb-1">
          <h6 class="x-small text-uppercase fw-bold text-muted mb-0">Model</h6>
          <div v-if="store.isModelSwitching || store.isModelsLoading" class="spinner-border spinner-border-sm text-primary" style="width: 0.7rem; height: 0.7rem;" role="status">
            <span class="visually-hidden">Loading...</span>
          </div>
        </div>
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

      <!-- LLM Section -->
      <div class="mb-4 px-1">
        <div class="d-flex justify-content-between align-items-center mb-1">
          <h6 class="x-small text-uppercase fw-bold text-muted mb-0">Intelligence</h6>
          <div v-if="store.isLlmLoading" class="spinner-border spinner-border-sm text-success" style="width: 0.7rem; height: 0.7rem;" role="status">
            <span class="visually-hidden">Loading...</span>
          </div>
          <span v-else-if="store.currentLlmModel" class="badge rounded-pill x-small py-1" :class="store.isLlmLoaded ? 'text-bg-success' : 'text-bg-secondary'" :title="store.isLlmLoaded ? 'Loaded in VRAM' : 'Standby (on disk)'">
            {{ store.isLlmLoaded ? 'VRAM' : 'STANDBY' }}
          </span>
        </div>
        <select 
          class="form-select form-select-sm mb-1" 
          :value="store.currentLlmModel" 
          @change="(e) => store.loadLlmModel((e.target as HTMLSelectElement).value)"
          :disabled="store.isLlmLoading || store.isGenerating"
        >
          <option value="">None</option>
          <option v-for="m in store.models.filter(m => m.type === 'llm')" :key="m.id" :value="m.id">
            {{ m.name }}
          </option>
        </select>
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
          title="Switch SD Model"
        >
          <i class="bi bi-image"></i>
        </button>
        
        <!-- SD Custom Dropdown -->
        <div v-if="isModelMenuOpen" class="custom-dropdown shadow border rounded bg-body text-start">
          <div class="dropdown-header border-bottom py-2 px-3 fw-bold small text-uppercase">Stable Diffusion Model</div>
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
                <i v-if="model.id === store.currentModel" class="bi bi-check-lg ms-2"></i>
              </button>
            </template>
          </div>
        </div>
      </div>

      <div class="llm-dropdown-container">
        <button 
          class="btn btn-link p-0 border-0 fs-4 cursor-pointer text-decoration-none position-relative" 
          :class="{ 'pulse-animation': store.isLlmLoading }"
          type="button" 
          @click.stop="toggleLlmMenu"
          title="Switch Intelligence Model"
        >
          <i class="bi bi-cpu"></i>
          <span v-if="store.currentLlmModel" class="position-absolute bottom-0 end-0 translate-middle-x p-1 border border-light rounded-circle" :class="store.isLlmLoaded ? 'bg-success' : 'bg-secondary'" style="width: 8px; height: 8px;"></span>
        </button>

        <!-- LLM Custom Dropdown -->
        <div v-if="isLlmMenuOpen" class="custom-dropdown shadow border rounded bg-body text-start">
          <div class="dropdown-header border-bottom py-2 px-3 fw-bold small text-uppercase">Intelligence Model</div>
          <div class="dropdown-list">
            <button 
                class="dropdown-item-btn w-100 text-start border-0 bg-transparent py-2 px-3 small"
                @click="selectLlmModel('')"
            >
              None
            </button>
            <button 
              v-for="model in store.models.filter(m => m.type === 'llm')" 
              :key="model.id"
              class="dropdown-item-btn w-100 text-start border-0 bg-transparent py-2 px-3 small d-flex justify-content-between align-items-center"
              :class="{ 'active-model-llm': model.id === store.currentLlmModel }"
              @click="selectLlmModel(model.id)"
              :disabled="store.isLlmLoading || store.isGenerating"
            >
              <span class="text-truncate">{{ model.name }}</span>
              <i v-if="model.id === store.currentLlmModel" class="bi bi-check-lg ms-2"></i>
            </button>
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
</style>
