<script setup lang="ts">
import { ref } from 'vue'
import { useGenerationStore } from '@/stores/generation'

const store = useGenerationStore()
const emit = defineEmits(['completed'])

const isSaving = ref(false)

async function finishSetup() {
  isSaving.value = true
  // Force update config with setup_completed = true
  await store.updateConfig(true)
  isSaving.value = false
  emit('completed')
}
</script>

<template>
  <div class="setup-overlay d-flex align-items-center justify-content-center">
    <div class="card shadow-lg" style="max-width: 500px; width: 100%;">
      <div class="card-header bg-primary text-white">
        <h5 class="mb-0"><i class="bi bi-rocket-takeoff-fill me-2"></i>Welcome to DiffusionDesk</h5>
      </div>
      <div class="card-body">
        <p class="card-text">
          It looks like this is your first time running DiffusionDesk. 
          Please verify your directory settings to get started.
        </p>
        
        <div class="mb-3">
          <label for="modelDir" class="form-label fw-bold">Model Directory</label>
          <input type="text" id="modelDir" class="form-control" v-model="store.modelDir" placeholder="./models">
          <div class="form-text text-muted">
            Absolute path to your models folder (GGUF, SafeTensors). 
            <br><em>Windows Example: C:\AI\Models</em>
          </div>
        </div>

        <div class="mb-3">
          <label for="outputDir" class="form-label fw-bold">Output Directory</label>
          <input type="text" id="outputDir" class="form-control" v-model="store.outputDir" placeholder="./outputs">
          <div class="form-text text-muted">
            Where generated images will be saved.
          </div>
        </div>

        <div class="alert alert-info d-flex align-items-center" role="alert">
          <i class="bi bi-info-circle-fill me-2"></i>
          <div>
            You can always change these later in the <strong>Settings</strong> tab.
          </div>
        </div>

        <div class="d-grid gap-2">
          <button class="btn btn-primary btn-lg" @click="finishSetup" :disabled="isSaving">
            <span v-if="isSaving" class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
            {{ isSaving ? 'Saving...' : 'Finish Setup' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.setup-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background-color: rgba(0, 0, 0, 0.85);
  z-index: 9999;
  backdrop-filter: blur(5px);
}
</style>