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
  <div class="island p-4">
    <h3>Settings</h3>
    <hr />
    <div class="row g-4">
      <div class="col-md-6">
        <h5>UI Settings</h5>
        <div class="d-flex justify-content-between align-items-center mt-3">
          <label for="themeSelect" class="form-label mb-0">Theme</label>
          <select id="themeSelect" class="form-select form-select-sm" v-model="store.theme" style="width: auto; min-width: 120px;">
            <option value="system">System</option>
            <option value="light">Light</option>
            <option value="dark">Dark</option>
          </select>
        </div>
        <div class="d-flex justify-content-between align-items-center mt-3">
          <label for="barPos" class="form-label mb-0">Action Bar Position</label>
          <select id="barPos" class="form-select form-select-sm" v-model="store.actionBarPosition" style="width: auto; min-width: 120px;">
            <option value="bottom">Bottom</option>
            <option value="top">Top</option>
          </select>
        </div>
        <div class="d-flex justify-content-between align-items-center mt-3">
          <label for="assistantPos" class="form-label mb-0">Assistant Position</label>
          <select id="assistantPos" class="form-select form-select-sm" v-model="store.assistantPosition" style="width: auto; min-width: 120px;">
            <option value="right">Right</option>
            <option value="left">Left</option>
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
                        <button class="btn btn-primary" type="button" @click="() => store.updateConfig()">
                          <i class="bi bi-save"></i> Save Path
                        </button>
                      </div>
                      <div class="form-text x-small">Path on the server where images will be saved and loaded from.</div>
                    </div>
          
                    <div class="mb-3">
                      <label for="modelDir" class="form-label small fw-bold text-muted">Model Directory</label>
                      <div class="input-group input-group-sm">
                        <input type="text" id="modelDir" class="form-control" v-model="store.modelDir" placeholder="./models">
                        <button class="btn btn-primary" type="button" @click="store.updateConfig().then(() => store.fetchModels())">
                          <i class="bi bi-save"></i> Save & Scan
                        </button>
                      </div>
          <div class="form-text x-small">Root directory to scan for models.</div>
        </div>
      </div>
    </div>

    <hr class="my-4" />
  </div>
</template>

<style scoped>
.x-small {
  font-size: 0.75rem;
}
</style>