<script setup lang="ts">
import { computed } from 'vue'
import { useGenerationStore } from '@/stores/generation'

const props = defineProps<{
  modelValue: any
}>()

const emit = defineEmits(['update:modelValue'])

const store = useGenerationStore()

// Helper lists for dropdowns
const availableLlms = computed(() => store.models.filter(m => m.type === 'llm'))
const availableProjectors = computed(() => store.models.filter(m => m.name.toLowerCase().includes('mmproj') || m.id.toLowerCase().includes('mmproj')))

</script>

<template>
  <div class="row g-3">
     <div class="col-12">
       <label class="form-label fw-bold">Preset Name</label>
       <input type="text" v-model="modelValue.name" class="form-control" placeholder="e.g. Qwen2-VL Vision">
     </div>
     
     <div class="col-12"><hr class="my-2"></div>
     
     <div class="col-12">
       <label class="form-label small text-uppercase fw-bold">LLM Model (Required)</label>
       <select v-model="modelValue.model_path" class="form-select">
          <option value="">Select Model...</option>
          <option v-for="m in availableLlms" :key="m.id" :value="m.id">{{ m.name }}</option>
       </select>
     </div>
     
     <div class="col-12">
       <label class="form-label small text-uppercase fw-bold">Vision Projector (mmproj) (Optional)</label>
       <select v-model="modelValue.mmproj_path" class="form-select">
          <option value="">None</option>
          <option v-for="m in availableProjectors" :key="m.id" :value="m.id">{{ m.name }}</option>
       </select>
       <div class="form-text x-small">Select a vision projector (must contain 'mmproj' in name).</div>
     </div>
     
     <div class="col-md-6">
       <label class="form-label small text-uppercase fw-bold">Context Size</label>
       <input type="number" v-model="modelValue.n_ctx" class="form-control" step="1024">
     </div>

     <div class="col-12"><hr class="my-2"></div>
     <div class="col-12">
         <div class="d-flex justify-content-between align-items-center mb-2">
             <label class="form-label small text-uppercase fw-bold mb-0">System Prompts</label>
             <button class="btn btn-xs btn-outline-secondary" type="button" @click="modelValue.system_prompt_assistant=''; modelValue.system_prompt_tagging=''; modelValue.system_prompt_style=''">
                 <i class="bi bi-arrow-counterclockwise"></i> Reset to Defaults
             </button>
         </div>
         
         <div class="mb-3">
             <label class="form-label x-small text-muted">Assistant Role</label>
             <textarea v-model="modelValue.system_prompt_assistant" class="form-control form-control-sm" rows="2" placeholder="Default: You are an integrated creative assistant..."></textarea>
         </div>
         <div class="mb-3">
             <label class="form-label x-small text-muted">Image Tagging</label>
             <textarea v-model="modelValue.system_prompt_tagging" class="form-control form-control-sm" rows="2" placeholder="Default: Analyze the image and provide JSON tags..."></textarea>
         </div>
         <div class="mb-3">
             <label class="form-label x-small text-muted">Style Extraction</label>
             <textarea v-model="modelValue.system_prompt_style" class="form-control form-control-sm" rows="2" placeholder="Default: Extract artistic styles from prompt..."></textarea>
         </div>
     </div>
  </div>
</template>

<style scoped>
.x-small {
  font-size: 0.75rem;
}
.btn-xs {
  padding: 0.1rem 0.25rem;
  font-size: 0.65rem;
  line-height: 1;
}
</style>