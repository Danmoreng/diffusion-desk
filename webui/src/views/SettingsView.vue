<script setup lang="ts">
import { ref } from 'vue'
import { useGenerationStore } from '@/stores/generation'
const store = useGenerationStore()

const testPrompt = ref('Hello, what can you do?')
const testResult = ref('')

async function runTest() {
  testResult.value = 'Thinking...'
  const result = await store.testLlmCompletion(testPrompt.value)
  testResult.value = result || 'Error: check console or server logs.'
}
</script>

<template>
  <div>
    <h3>Settings</h3>
    <hr />
    <div class="row g-4">
      <div class="col-md-6">
        <h5>UI Settings</h5>
        <div class="d-flex justify-content-between align-items-center">
          <label for="themeSelect" class="form-label mb-0">Theme</label>
          <select id="themeSelect" class="form-select form-select-sm" v-model="store.theme" style="width: auto; min-width: 120px;">
            <option value="system">🖥️ System</option>
            <option value="light">☀️ Light</option>
            <option value="dark">🌙 Dark</option>
          </select>
        </div>
      </div>
      <div class="col-md-6">
        <h5>Image Generation</h5>
        <div class="form-check form-switch mb-3">
          <input class="form-check-input" type="checkbox" role="switch" id="saveImages" v-model="store.saveImages">
          <label class="form-check-label" for="saveImages">Save Images Automatically</label>
        </div>
        
        <div class="mb-3">
          <label for="outputDir" class="form-label small fw-bold text-muted">Output Directory</label>
                      <div class="input-group input-group-sm">
                        <input type="text" id="outputDir" class="form-control" v-model="store.outputDir" placeholder="./outputs">
                        <button class="btn btn-primary" type="button" @click="store.updateConfig">
                          💾 Save Path
                        </button>
                      </div>
                      <div class="form-text x-small">Path on the server where images will be saved and loaded from.</div>
                    </div>
          
                    <div class="mb-3">
                      <label for="modelDir" class="form-label small fw-bold text-muted">Model Directory</label>
                      <div class="input-group input-group-sm">
                        <input type="text" id="modelDir" class="form-control" v-model="store.modelDir" placeholder="./models">
                        <button class="btn btn-primary" type="button" @click="store.updateConfig().then(() => store.fetchModels())">
                          💾 Save & Scan
                        </button>
                      </div>
          <div class="form-text x-small">Root directory to scan for models.</div>
        </div>
      </div>
    </div>

    <hr class="my-4" />

    <div class="row g-4">
      <div class="col-md-6">
        <h5>LLM Settings (Llama.cpp)</h5>
        <div class="mb-3">
          <label class="form-label small fw-bold text-muted">Active LLM Model</label>
          <div class="input-group input-group-sm">
            <select class="form-select" v-model="store.currentLlmModel" :disabled="store.isLlmLoading">
              <option value="">None</option>
              <option v-for="m in store.models.filter(m => m.id.includes('text-encoder') || m.id.includes('llm'))" :key="m.id" :value="m.id">
                {{ m.name }}
              </option>
            </select>
            <button class="btn btn-outline-primary" type="button" @click="store.loadLlmModel(store.currentLlmModel)" :disabled="store.isLlmLoading || !store.currentLlmModel">
              <span v-if="store.isLlmLoading" class="spinner-border spinner-border-sm me-1"></span>
              Load LLM
            </button>
          </div>
          <div class="form-text x-small">Select a GGUF model from the text-encoder directory to enable rewriting.</div>
        </div>
      </div>

      <div class="col-md-6">
        <h5>Test LLM Completion</h5>
        <div class="input-group input-group-sm mb-2">
          <input type="text" class="form-control" v-model="testPrompt" placeholder="Enter a test prompt...">
          <button class="btn btn-success" type="button" @click="runTest" :disabled="!store.currentLlmModel || store.isLlmLoading">
            Send Request
          </button>
        </div>
        <div v-if="testResult" class="p-2 border rounded bg-light small" style="white-space: pre-wrap;">
          <strong>Response:</strong><br/>{{ testResult }}
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.x-small {
  font-size: 0.75rem;
}
</style>