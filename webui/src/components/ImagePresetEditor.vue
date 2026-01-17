<script setup lang="ts">
import { computed } from 'vue'
import { useGenerationStore } from '@/stores/generation'

const props = defineProps<{
  modelValue: any
}>()

const emit = defineEmits(['update:modelValue'])

const store = useGenerationStore()

// Helper lists for dropdowns
const availableModels = computed(() => store.models.filter(m => m.type !== 'llm' && m.type !== 'lora' && m.type !== 'upscaler'))
const availableVaEs = computed(() => store.models.filter(m => m.type === 'vae'))
const availableClips = computed(() => store.models.filter(m => m.type === 'text-encoder' || m.type === 'clip'))
const availableLlmEncoders = computed(() => store.models.filter(m => m.type === 'text-encoder' || m.type === 'llm'))

</script>

<template>
  <div class="row g-3">
     <div class="col-12">
       <label class="form-label fw-bold">Preset Name</label>
       <input type="text" v-model="modelValue.name" class="form-control" placeholder="e.g. Flux Dev + FP8 T5">
     </div>
     
     <div class="col-12"><hr class="my-2"></div>
     
     <div class="col-12">
       <label class="form-label small text-uppercase fw-bold">UNet / Main Model (Required)</label>
       <select v-model="modelValue.unet_path" class="form-select">
          <option value="">Select Model...</option>
          <option v-for="m in availableModels" :key="m.id" :value="m.id">{{ m.name }} ({{ m.type }})</option>
       </select>
     </div>
     
     <div class="col-md-6">
       <label class="form-label small text-uppercase fw-bold">VAE (Optional)</label>
       <select v-model="modelValue.vae_path" class="form-select">
          <option value="">None (Use Embedded)</option>
          <option v-for="m in availableVaEs" :key="m.id" :value="m.id">{{ m.name }}</option>
       </select>
     </div>
     
     <div class="col-md-6">
       <label class="form-label small text-uppercase fw-bold">T5 / Text Encoder 2 (Optional)</label>
       <select v-model="modelValue.t5xxl_path" class="form-select">
          <option value="">None (Use Embedded)</option>
          <option v-for="m in availableClips" :key="m.id" :value="m.id">{{ m.name }}</option>
       </select>
     </div>

     <div class="col-md-6">
       <label class="form-label small text-uppercase fw-bold">LLM Text Encoder 3 (Optional)</label>
       <select v-model="modelValue.llm_path" class="form-select">
          <option value="">None</option>
          <option v-for="m in availableLlmEncoders" :key="m.id" :value="m.id">{{ m.name }}</option>
       </select>
     </div>
     
     <div class="col-md-6">
       <label class="form-label small text-uppercase fw-bold">CLIP L (Optional)</label>
       <select v-model="modelValue.clip_l_path" class="form-select">
          <option value="">None</option>
          <option v-for="m in availableClips" :key="m.id" :value="m.id">{{ m.name }}</option>
       </select>
     </div>
     
     <div class="col-md-6">
       <label class="form-label small text-uppercase fw-bold">CLIP G (Optional)</label>
       <select v-model="modelValue.clip_g_path" class="form-select">
          <option value="">None</option>
          <option v-for="m in availableClips" :key="m.id" :value="m.id">{{ m.name }}</option>
       </select>
     </div>
     
     <div class="col-12"><hr class="my-2"></div>
     
     <div class="col-12">
        <label class="form-label fw-bold">Default Generation Parameters</label>
        <div class="row g-2">
            <div class="col-6 col-md-4">
                <label class="form-label small text-muted">Width</label>
                <input type="number" class="form-control form-control-sm" v-model.number="modelValue.default_params.width" placeholder="1024">
            </div>
            <div class="col-6 col-md-4">
                <label class="form-label small text-muted">Height</label>
                <input type="number" class="form-control form-control-sm" v-model.number="modelValue.default_params.height" placeholder="1024">
            </div>
            <div class="col-6 col-md-4">
                <label class="form-label small text-muted">Sampler</label>
                <select v-model="modelValue.default_params.sampler" class="form-select form-select-sm">
                    <option :value="undefined">Model Default</option>
                    <option v-for="s in store.samplers" :key="s" :value="s">{{ s }}</option>
                </select>
            </div>
            <div class="col-6 col-md-6">
                <label class="form-label small text-muted">Steps</label>
                <input type="number" class="form-control form-control-sm" v-model.number="modelValue.default_params.steps" placeholder="20">
            </div>
            <div class="col-6 col-md-6">
                <label class="form-label small text-muted">CFG Scale</label>
                <input type="number" class="form-control form-control-sm" v-model.number="modelValue.default_params.cfg_scale" step="0.1" placeholder="3.5">
            </div>
        </div>
     </div>

     <div class="col-12">
         <div class="form-text x-small">Note: For Flux/SD3, ensure you select the correct CLIP/T5 combination if not using a GGUF that embeds them.</div>
     </div>
  </div>
</template>

<style scoped>
.x-small {
  font-size: 0.75rem;
}
</style>