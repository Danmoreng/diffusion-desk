<script setup lang="ts">
import { ref } from 'vue'
import { useGenerationStore } from '@/stores/generation'
import ImagePresetEditor from '@/components/ImagePresetEditor.vue'
import LlmPresetEditor from '@/components/LlmPresetEditor.vue'

const store = useGenerationStore()
const emit = defineEmits(['completed'])

const step = ref(1)
const isSaving = ref(false)
const isScanning = ref(false)

// Preset Data
const imagePreset = ref({
  id: 0,
  name: 'Default SD',
  unet_path: '',
  vae_path: '',
  clip_l_path: '',
  clip_g_path: '',
  t5xxl_path: '',
  llm_path: '',
  vram_weights_mb_estimate: 0,
  default_params: {},
  preferred_params: { memory: { force_clip_cpu: false } }
})

const llmPreset = ref({
  id: 0,
  name: 'Default Assistant',
  model_path: '',
  mmproj_path: '',
  n_ctx: 2048,
  system_prompt_assistant: '',
  system_prompt_tagging: '',
  system_prompt_style: ''
})

async function nextStep() {
  if (step.value === 1) {
    isSaving.value = true
    try {
      // 1. Save Paths (but don't mark completed yet)
      await store.updateConfig(false)
      
      // 2. Scan Models
      isScanning.value = true
      await store.fetchModels()
      
      // 3. Pre-select first available models if any
      const unetModels = store.models.filter(m => m.type === 'stable-diffusion' || m.type === 'root')
      const llmModels = store.models.filter(m => m.type === 'llm')
      
      if (unetModels.length > 0) imagePreset.value.unet_path = unetModels[0].id
      if (llmModels.length > 0) llmPreset.value.model_path = llmModels[0].id
      
      step.value = 2
    } catch (e) {
      console.error("Setup error:", e)
    } finally {
      isSaving.value = false
      isScanning.value = false
    }
  }
}

async function finishSetup() {
  isSaving.value = true
  try {
    // 1. Create Image Preset if model selected
    if (imagePreset.value.unet_path) {
      const id = await store.saveImagePreset(imagePreset.value)
      if (id) {
        await store.loadImagePreset(id)
      }
    }

    // 2. Create LLM Preset if model selected
    if (llmPreset.value.model_path) {
      const id = await store.saveLlmPreset(llmPreset.value)
      if (id) {
        await store.loadLlmPreset(id)
      }
    }

    // 3. Mark Setup Completed
    await store.updateConfig(true)
    emit('completed')
  } catch (e) {
    console.error("Finish setup error:", e)
  } finally {
    isSaving.value = false
  }
}
</script>

<template>
  <div class="setup-overlay d-flex align-items-center justify-content-center">
    <div class="card shadow-lg" style="max-width: 800px; width: 100%;">
      <div class="card-header bg-primary text-white d-flex justify-content-between align-items-center">
        <h5 class="mb-0 d-flex align-items-center">
          <img src="/diffusion-desk-icon-256.png" alt="Logo" width="28" height="28" class="me-2 rounded-2">
          Welcome to DiffusionDesk
        </h5>
        <span class="badge bg-light text-primary">Step {{ step }} of 2</span>
      </div>
      
      <!-- STEP 1: Directories -->
      <div v-if="step === 1" class="card-body">
        <p class="card-text">
          Let's verify where your files are located.
        </p>
        
        <div class="mb-3">
          <label for="modelDir" class="form-label fw-bold">Model Directory</label>
          <input type="text" id="modelDir" class="form-control" v-model="store.modelDir" placeholder="./models">
          <div class="form-text text-muted">
            Root folder containing <code>stable-diffusion/</code>, <code>llm/</code>, <code>vae/</code> subfolders.
          </div>
        </div>

        <div class="mb-3">
          <label for="outputDir" class="form-label fw-bold">Output Directory</label>
          <input type="text" id="outputDir" class="form-control" v-model="store.outputDir" placeholder="./outputs">
        </div>

        <div class="d-grid gap-2 mt-4">
          <button class="btn btn-primary btn-lg" @click="nextStep" :disabled="isSaving || isScanning">
            <span v-if="isSaving || isScanning" class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
            {{ isScanning ? 'Scanning Models...' : 'Next: Scan & Configure Presets' }}
          </button>
        </div>
      </div>

      <!-- STEP 2: Presets -->
      <div v-if="step === 2" class="card-body" style="max-height: 80vh; overflow-y: auto;">
        <p class="card-text">
          Great! We found some models. Let's create your starter presets using the standard configuration.
        </p>

        <div class="card mb-3 bg-body-tertiary border-0">
          <div class="card-body">
            <h6 class="card-title fw-bold text-primary mb-3"><i class="bi bi-image me-2"></i>Image Generation Preset</h6>
            <ImagePresetEditor v-model="imagePreset" />
          </div>
        </div>

        <div class="card mb-3 bg-body-tertiary border-0">
          <div class="card-body">
            <h6 class="card-title fw-bold text-success mb-3"><i class="bi bi-chat-square-text me-2"></i>Intelligence Preset</h6>
            <LlmPresetEditor v-model="llmPreset" />
          </div>
        </div>

        <div class="d-grid gap-2 mt-4">
          <button class="btn btn-success btn-lg" @click="finishSetup" :disabled="isSaving">
            <span v-if="isSaving" class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>
            Finish & Launch
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
  background-color: rgba(0, 0, 0, 0.92); /* Slightly darker for focus */
  z-index: 9999;
  backdrop-filter: blur(8px);
}
</style>