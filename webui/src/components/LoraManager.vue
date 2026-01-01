<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useGenerationStore } from '@/stores/generation'

const store = useGenerationStore()
const props = defineProps<{
  // If we wanted to filter or pass specific context
}>()

const searchQuery = ref('')
const isExpanded = ref(false)
const loraMetadata = ref<Record<string, any>>({})

const fetchMetadata = async () => {
  try {
    const res = await fetch('/v1/models/metadata')
    if (res.ok) {
      const data = await res.json()
      const metaMap: Record<string, any> = {}
      data.forEach((item: any) => {
        metaMap[item.id] = item.metadata
      })
      loraMetadata.value = metaMap
    }
  } catch (e) {
    console.error('Failed to fetch LoRA metadata', e)
  }
}

const escapeRegex = (str: string) => {
  return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

// We filter models to find LoRAs
const availableLoras = computed(() => {
  return store.models.filter(m => m.type === 'lora').map(m => {
    const name = m.name.replace(/\.[^/.]+$/, "")
    const escapedName = escapeRegex(name)
    const regex = new RegExp(`<lora:${escapedName}:?([0-9.-]+)?>`, 'i')
    const match = store.prompt.match(regex)
    
    const meta = loraMetadata.value[m.id] || {}
    
    return {
      ...m,
      cleanName: name,
      isActive: !!match,
      weight: match && match[1] ? parseFloat(match[1]) : 1.0,
      triggerWord: meta.trigger_word || '',
      previewPath: meta.preview_path || ''
    }
  }).filter(m => m.name.toLowerCase().includes(searchQuery.value.toLowerCase()))
})

const toggleLora = (lora: any) => {
  const escapedName = escapeRegex(lora.cleanName)
  const regex = new RegExp(`<lora:${escapedName}:?[0-9.-]*>`, 'gi')
  
  if (lora.isActive) {
    // Remove from prompt
    store.prompt = store.prompt.replace(regex, '').replace(/\s{2,}/g, ' ').trim()
    
    // Also remove trigger word if present
    if (lora.triggerWord) {
        const twRegex = new RegExp(`\\b${escapeRegex(lora.triggerWord)}\\b,?\\s*`, 'gi')
        store.prompt = store.prompt.replace(twRegex, '').trim()
    }
  } else {
    // Add to prompt
    if (!store.prompt.match(regex)) {
        const tag = `<lora:${lora.cleanName}:1.0>`
        let newPrompt = store.prompt
        
        // Add trigger word if available and not already present
        if (lora.triggerWord && !newPrompt.toLowerCase().includes(lora.triggerWord.toLowerCase())) {
            newPrompt = (lora.triggerWord + ', ' + newPrompt).trim()
        }
        
        store.prompt = (newPrompt + ' ' + tag).trim()
    }
  }
}

const updateLoraWeight = (lora: any, newWeight: number) => {
  const escapedName = escapeRegex(lora.cleanName)
  const regex = new RegExp(`<lora:${escapedName}:?[0-9.-]*>`, 'gi')
  
  if (store.prompt.match(regex)) {
    store.prompt = store.prompt.replace(regex, `<lora:${lora.cleanName}:${newWeight.toFixed(2)}>`)
  } else {
    const tag = `<lora:${lora.cleanName}:${newWeight.toFixed(2)}>`
    store.prompt = (store.prompt + ' ' + tag).trim()
  }
}

// Fetch models on mount just in case
onMounted(() => {
  if (store.models.length === 0) {
    store.fetchModels()
  }
  fetchMetadata()
})
</script>

<template>
  <div class="lora-manager border rounded p-2 bg-body-tertiary">
    <div class="d-flex justify-content-between align-items-center mb-2 cursor-pointer" @click="isExpanded = !isExpanded">
        <label class="x-small text-muted text-uppercase fw-bold mb-0 cursor-pointer user-select-none">
            <i class="bi" :class="isExpanded ? 'bi-caret-down-fill' : 'bi-caret-right-fill'"></i> LoRA Models
        </label>
        <span class="badge bg-secondary rounded-pill" v-if="availableLoras.filter(l => l.isActive).length > 0">
            {{ availableLoras.filter(l => l.isActive).length }} Active
        </span>
    </div>

    <div v-show="isExpanded">
        <div class="input-group input-group-sm mb-2">
            <span class="input-group-text"><i class="bi bi-search"></i></span>
            <input type="text" class="form-control" placeholder="Search LoRAs..." v-model="searchQuery">
        </div>

        <div class="lora-grid">
            <div v-if="availableLoras.length === 0" class="text-center p-3 text-muted small">
                No LoRA models found.
            </div>
            
            <div 
                v-for="lora in availableLoras" 
                :key="lora.id"
                class="lora-card border rounded mb-2 bg-body overflow-hidden"
                :class="{ 'border-primary': lora.isActive }"
            >
                <div class="d-flex align-items-stretch">
                    <!-- Mini Preview -->
                    <div class="lora-mini-preview bg-secondary bg-opacity-10 d-flex align-items-center justify-content-center" style="width: 60px; min-height: 60px;">
                        <img v-if="lora.previewPath" :src="lora.previewPath" class="w-100 h-100 object-fit-cover" />
                        <i v-else class="bi bi-plugin text-muted opacity-50"></i>
                    </div>

                    <div class="p-2 flex-grow-1 min-w-0">
                        <div class="d-flex justify-content-between align-items-center mb-1">
                            <div class="form-check form-switch mb-0">
                                <input 
                                    class="form-check-input" 
                                    type="checkbox" 
                                    :id="'lora-' + lora.id" 
                                    :checked="lora.isActive"
                                    @change="toggleLora(lora)"
                                >
                                <label class="form-check-label small text-break fw-bold" :for="'lora-' + lora.id" :title="lora.name">
                                    {{ lora.cleanName }}
                                </label>
                            </div>
                        </div>
                        
                        <div v-if="lora.isActive" class="px-1 mt-1">
                            <div class="d-flex justify-content-between align-items-center">
                                <span class="x-small text-muted">Weight</span>
                                <span class="x-small fw-bold text-primary">{{ lora.weight.toFixed(2) }}</span>
                            </div>
                            <input 
                                type="range" 
                                class="form-range form-range-sm" 
                                min="0" 
                                max="2" 
                                step="0.05"
                                :value="lora.weight"
                                @input="(e) => updateLoraWeight(lora, parseFloat((e.target as HTMLInputElement).value))"
                            >
                        </div>
                        <div v-else class="x-small text-muted text-truncate" v-if="lora.triggerWord">
                            <i class="bi bi-magic"></i> {{ lora.triggerWord }}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
  </div>
</template>

<style scoped>
.lora-grid {
    max-height: 300px;
    overflow-y: auto;
}

.x-small {
    font-size: 0.7rem;
}

.cursor-pointer {
    cursor: pointer;
}

/* Custom scrollbar for the grid */
.lora-grid::-webkit-scrollbar {
    width: 6px;
}
.lora-grid::-webkit-scrollbar-thumb {
    background-color: var(--bs-secondary);
    border-radius: 3px;
}
</style>
