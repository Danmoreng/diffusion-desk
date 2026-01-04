<script setup lang="ts">
import { ref, onMounted, computed, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { Modal } from 'bootstrap'
import { useGenerationStore } from '@/stores/generation'

interface TagInfo {
  name: string
  category: string
  count: number
}

// Preset Interfaces
interface ImagePreset {
  id: number
  name: string
  unet_path: string
  vae_path: string
  clip_l_path: string
  clip_g_path: string
  t5xxl_path: string
  vram_weights_mb_estimate: number
  default_params?: any
}

interface LlmPreset {
  id: number
  name: string
  model_path: string
  mmproj_path: string
  n_ctx: number
  role: string
}

const router = useRouter()
const store = useGenerationStore()
const activeTab = computed({
  get: () => {
    const path = router.currentRoute.value.path
    if (path.includes('/manager/presets/image')) return 'image_presets'
    if (path.includes('/manager/presets/llm')) return 'llm_presets'
    if (path.includes('/manager/styles')) return 'styles'
    if (path.includes('/manager/models')) return 'models'
    if (path.includes('/manager/llms')) return 'llms'
    if (path.includes('/manager/loras')) return 'loras'
    if (path.includes('/manager/tags')) return 'tags'
    return 'image_presets' // Default
  },
  set: (val) => {
    // We handle navigation in the template clicks, this setter is just to satisfy computed
  }
})

const tags = ref<TagInfo[]>([])
const isLoading = ref(false)
const searchQuery = ref('')

// Presets State
const imagePresets = ref<ImagePreset[]>([])
const llmPresets = ref<LlmPreset[]>([])
const presetModalRef = ref<HTMLElement | null>(null)
let presetModalInstance: Modal | null = null
const isEditingPreset = ref(false)
const editingImagePreset = ref<ImagePreset>({
  id: 0, name: '', unet_path: '', vae_path: '', clip_l_path: '', clip_g_path: '', t5xxl_path: '', vram_weights_mb_estimate: 0, default_params: {}
})
const editingLlmPreset = ref<LlmPreset>({
  id: 0, name: '', model_path: '', mmproj_path: '', n_ctx: 2048, role: 'Assistant'
})

// Helper lists for dropdowns
const availableModels = computed(() => store.models.filter(m => m.type !== 'llm' && m.type !== 'lora' && m.type !== 'upscaler'))
const availableVaEs = computed(() => store.models.filter(m => m.type === 'vae'))
const availableClips = computed(() => store.models.filter(m => m.type === 'text-encoder' || m.type === 'clip'))
const availableLlms = computed(() => store.models.filter(m => m.type === 'llm'))

const modalRef = ref<HTMLElement | null>(null)
let modalInstance: Modal | null = null

// Style management state
const styleModalRef = ref<HTMLElement | null>(null)
let styleModalInstance: Modal | null = null
const styleToDelete = ref<string | null>(null)
const styleDeleteModalRef = ref<HTMLElement | null>(null)
let styleDeleteModalInstance: Modal | null = null

const editingStyle = ref({
  name: '',
  prompt: '{prompt}, ',
  negative_prompt: ''
})
const isEditing = ref(false)

// Extract Modal State
const extractModalRef = ref<HTMLElement | null>(null)
let extractModalInstance: Modal | null = null
const extractionPrompt = ref('')
const isExtracting = ref(false)
const extractionResult = ref('')

function openExtractModal() {
    extractionPrompt.value = ''
    extractionResult.value = ''
    if (extractModalRef.value) {
        extractModalInstance = new Modal(extractModalRef.value)
        extractModalInstance.show()
    }
}

async function doExtract() {
    if (!extractionPrompt.value) return;
    isExtracting.value = true;
    try {
        await store.extractStylesFromPrompt(extractionPrompt.value)
        extractModalInstance?.hide()
    } catch(e: any) {
        extractionResult.value = e.message || 'Extraction failed'
    } finally {
        isExtracting.value = false;
    }
}

const filteredTags = computed(() => {
  if (!searchQuery.value) return tags.value
  const query = searchQuery.value.toLowerCase()
  return tags.value.filter(t => t.name.toLowerCase().includes(query))
})

const filteredStyles = computed(() => {
  if (!searchQuery.value) return store.styles
  const query = searchQuery.value.toLowerCase()
  return store.styles.filter(s => s.name.toLowerCase().includes(query))
})

// Model management logic
interface ModelMetadataEntry {
  id: string
  metadata: any
}
const modelMetadataList = ref<ModelMetadataEntry[]>([])
const modelEditModalRef = ref<HTMLElement | null>(null)
let modelEditModalInstance: Modal | null = null
const editingModelMetadata = ref<ModelMetadataEntry>({ id: '', metadata: {} })

const filteredModelMetadata = computed(() => {
  if (!searchQuery.value) return modelMetadataList.value
  const query = searchQuery.value.toLowerCase()
  return modelMetadataList.value.filter(m => 
    m.id.toLowerCase().includes(query) || 
    (m.metadata.name && m.metadata.name.toLowerCase().includes(query))
  )
})

async function fetchModelMetadata() {
  try {
    const res = await fetch('/v1/models/metadata')
    if (res.ok) {
      modelMetadataList.value = await res.json()
    }
  } catch (e) {
    console.error('Failed to fetch model metadata', e)
  }
}

async function syncModels() {
  isLoading.value = true
  try {
    await store.fetchModels()
    for (const m of store.models) {
      const existing = modelMetadataList.value.find(entry => entry.id === m.id)
      if (!existing) {
        const defaultMeta = {
          name: m.name,
          type: m.type,
          base: 'SD1.5',
          vae: '',
          llm: '',
          cfg_scale: 7.0,
          sample_steps: 20,
          width: 512,
          height: 512,
          sampling_method: 'euler_a'
        }
        await fetch('/v1/models/metadata', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ id: m.id, metadata: defaultMeta })
        })
      }
    }
    await fetchModelMetadata()
  } catch (e) {
    console.error('Failed to sync models', e)
  } finally {
    isLoading.value = false
  }
}

function openModelEditModal(model: ModelMetadataEntry) {
  editingModelMetadata.value = JSON.parse(JSON.stringify(model))
  if (modelEditModalRef.value) {
    modelEditModalInstance = new Modal(modelEditModalRef.value)
    modelEditModalInstance.show()
  }
}

async function saveModelMetadata() {
  try {
    const res = await fetch('/v1/models/metadata', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(editingModelMetadata.value)
    })
    if (res.ok) {
      await fetchModelMetadata()
      modelEditModalInstance?.hide()
    }
  } catch (e) {
    console.error('Failed to save model metadata', e)
  }
}

async function regenerateLoraPreview(model: ModelMetadataEntry) {
  isLoading.value = true
  try {
    const res = await fetch('/v1/models/metadata/preview', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id: model.id })
    })
    if (res.ok) {
      setTimeout(fetchModelMetadata, 2000)
    }
  } catch (e) {
    console.error('Failed to regenerate LoRA preview', e)
  } finally {
    isLoading.value = false
  }
}

async function generateMissingLoraPreviews() {
  isLoading.value = true
  try {
    const missing = modelMetadataList.value.filter(m => m.metadata.type === 'lora' && !m.metadata.preview_path)
    for (const m of missing) {
      await fetch('/v1/models/metadata/preview', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id: m.id })
      })
    }
    setTimeout(fetchModelMetadata, 5000)
  } catch (e) {
    console.error('Failed to fix LoRA previews:', e)
  } finally {
    isLoading.value = false
  }
}

async function fetchTags() {
  isLoading.value = true
  try {
    const res = await fetch('/v1/history/tags')
    if (res.ok) {
      tags.value = await res.json()
    }
  } catch (e) {
    console.error('Failed to fetch tags', e)
  } finally {
    isLoading.value = false
  }
}

function showCleanupModal() {
  if (modalRef.value) {
    modalInstance = new Modal(modalRef.value)
    modalInstance.show()
  }
}

async function confirmCleanup() {
  modalInstance?.hide()
  isLoading.value = true
  try {
    const res = await fetch('/v1/history/tags/cleanup', { method: 'POST' })
    if (res.ok) {
      await fetchTags() 
    }
  } catch (e) {
    console.error('Failed to cleanup tags', e)
  } finally {
    isLoading.value = false
  }
}

// Preset Methods
async function fetchPresets() {
  try {
    const resImg = await fetch('/v1/presets/image')
    imagePresets.value = await resImg.json()
    
    const resLlm = await fetch('/v1/presets/llm')
    llmPresets.value = await resLlm.json()
    
    // Also fetch raw models to populate dropdowns
    store.fetchModels()
  } catch (e) {
    console.error(e)
  }
}

function openPresetModal(type: 'image' | 'llm', preset?: any) {
  isEditingPreset.value = !!preset
  if (type === 'image') {
    if (preset) editingImagePreset.value = { ...preset }
    else editingImagePreset.value = { id: 0, name: '', unet_path: '', vae_path: '', clip_l_path: '', clip_g_path: '', t5xxl_path: '', vram_weights_mb_estimate: 0, default_params: {} }
  } else {
    if (preset) editingLlmPreset.value = { ...preset }
    else editingLlmPreset.value = { id: 0, name: '', model_path: '', mmproj_path: '', n_ctx: 2048, role: 'Assistant' }
  }
  
  if (presetModalRef.value) {
    presetModalInstance = new Modal(presetModalRef.value)
    presetModalInstance.show()
  }
}

async function savePreset(type: 'image' | 'llm') {
  const endpoint = type === 'image' ? '/v1/presets/image' : '/v1/presets/llm'
  const body = type === 'image' ? editingImagePreset.value : editingLlmPreset.value
  
  try {
    const res = await fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    })
    if (res.ok) {
      presetModalInstance?.hide()
      fetchPresets()
    }
  } catch (e) {
    console.error(e)
  }
}

async function deletePreset(type: 'image' | 'llm', id: number) {
  if (!confirm('Are you sure?')) return
  const endpoint = type === 'image' ? `/v1/presets/image/${id}` : `/v1/presets/llm/${id}`
  try {
    const res = await fetch(endpoint, { method: 'DELETE' })
    if (res.ok) fetchPresets()
  } catch (e) {
    console.error(e)
  }
}

// Style Methods
function openStyleModal(style?: any) {
  if (style) {
    editingStyle.value = { ...style }
    isEditing.value = true
  } else {
    editingStyle.value = { name: '', prompt: '{prompt}, ', negative_prompt: '' }
    isEditing.value = false
  }
  if (styleModalRef.value) {
    styleModalInstance = new Modal(styleModalRef.value)
    styleModalInstance.show()
  }
}

async function saveStyle() {
  await store.saveStyle(editingStyle.value)
  styleModalInstance?.hide()
}

async function regeneratePreview(style: any) {
  const updated = { ...style, preview_path: '' }
  await store.saveStyle(updated)
}

async function generateMissingPreviews() {
  isLoading.value = true
  try {
    const res = await fetch('/v1/styles/previews/fix', { method: 'POST' })
    if (res.ok) {
      await store.fetchStyles()
    }
  } catch (e) {
    console.error('Failed to fix previews:', e)
  } finally {
    isLoading.value = false
  }
}

function confirmDeleteStyle(name: string) {
  styleToDelete.value = name
  if (styleDeleteModalRef.value) {
    styleDeleteModalInstance = new Modal(styleDeleteModalRef.value)
    styleDeleteModalInstance.show()
  }
}

async function doDeleteStyle() {
  if (styleToDelete.value) {
    await store.deleteStyle(styleToDelete.value)
    styleDeleteModalInstance?.hide()
    styleToDelete.value = null
  }
}

function navigateToTag(tagName: string) {
  router.push({ path: '/gallery', query: { tags: tagName } })
}

onMounted(() => {
  fetchTags()
  store.fetchStyles()
  fetchModelMetadata()
  fetchPresets()
})

onUnmounted(() => {
  modalInstance?.dispose()
  styleModalInstance?.dispose()
  styleDeleteModalInstance?.dispose()
  extractModalInstance?.dispose()
  modelEditModalInstance?.dispose()
  presetModalInstance?.dispose()
})
</script>

<template>
  <div class="manager-view h-100 d-flex flex-column">
    <div class="d-flex justify-content-between align-items-center mb-3">
      <h3 class="mb-0">Library Manager</h3>
    </div>

    <!-- Tabs -->
    <ul class="nav nav-tabs mb-3">
      <li class="nav-item">
        <router-link class="nav-link" :class="{ active: activeTab === 'image_presets' }" to="/manager/presets/image">
            <i class="bi bi-stack"></i> Image Presets
        </router-link>
      </li>
      <li class="nav-item">
        <router-link class="nav-link" :class="{ active: activeTab === 'llm_presets' }" to="/manager/presets/llm">
            <i class="bi bi-chat-text"></i> Intelligence Presets
        </router-link>
      </li>
      <li class="nav-item">
        <router-link class="nav-link" :class="{ active: activeTab === 'styles' }" to="/manager/styles">Styles</router-link>
      </li>
      <li class="nav-item">
        <router-link class="nav-link" :class="{ active: activeTab === 'loras' }" to="/manager/loras">LoRAs</router-link>
      </li>
      <li class="nav-item">
        <router-link class="nav-link" :class="{ active: activeTab === 'tags' }" to="/manager/tags">Tags</router-link>
      </li>
      <li class="nav-item">
        <router-link class="nav-link text-muted" :class="{ active: activeTab === 'models' }" to="/manager/models">Files: Models</router-link>
      </li>
      <li class="nav-item">
        <router-link class="nav-link text-muted" :class="{ active: activeTab === 'llms' }" to="/manager/llms">Files: LLM</router-link>
      </li>
    </ul>

    <!-- Image Presets Content -->
    <div v-if="activeTab === 'image_presets'" class="flex-grow-1 d-flex flex-column overflow-hidden">
        <div class="d-flex justify-content-between mb-3 bg-body-secondary p-2 rounded">
            <div class="fw-bold align-self-center px-2">Image Generation Presets</div>
            <button class="btn btn-sm btn-primary" @click="openPresetModal('image')">
                <i class="bi bi-plus-lg"></i> New Preset
            </button>
        </div>
        <div class="flex-grow-1 overflow-auto">
            <div class="row g-3">
                <div v-for="preset in imagePresets" :key="preset.id" class="col-md-6 col-xl-4">
                    <div class="card h-100 shadow-sm border-0">
                        <div class="card-body">
                            <h5 class="card-title">{{ preset.name }}</h5>
                            <div class="small text-muted mb-2">
                                <div v-if="preset.unet_path" class="text-truncate" title="UNet"><i class="bi bi-cpu"></i> {{ preset.unet_path.split('/').pop() }}</div>
                                <div v-if="preset.vae_path" class="text-truncate" title="VAE"><i class="bi bi-palette"></i> {{ preset.vae_path.split('/').pop() }}</div>
                                <div v-if="preset.t5xxl_path" class="text-truncate" title="T5"><i class="bi bi-fonts"></i> {{ preset.t5xxl_path.split('/').pop() }}</div>
                            </div>
                            <div class="d-flex justify-content-between align-items-center mt-3">
                                <button class="btn btn-sm btn-outline-success" @click="store.loadImagePreset(preset.id)" :disabled="isLoading">
                                    <i class="bi bi-play-fill"></i> Load
                                </button>
                                <div>
                                    <button class="btn btn-sm btn-outline-secondary me-1" @click="openPresetModal('image', preset)"><i class="bi bi-pencil"></i></button>
                                    <button class="btn btn-sm btn-outline-danger" @click="deletePreset('image', preset.id)"><i class="bi bi-trash"></i></button>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
                <div v-if="imagePresets.length === 0" class="col-12 text-center py-5 text-muted">
                    No image presets. Create one to get started!
                </div>
            </div>
        </div>
    </div>

    <!-- LLM Presets Content -->
    <div v-if="activeTab === 'llm_presets'" class="flex-grow-1 d-flex flex-column overflow-hidden">
        <div class="d-flex justify-content-between mb-3 bg-body-secondary p-2 rounded">
            <div class="fw-bold align-self-center px-2">Intelligence (LLM) Presets</div>
            <button class="btn btn-sm btn-primary" @click="openPresetModal('llm')">
                <i class="bi bi-plus-lg"></i> New Preset
            </button>
        </div>
        <div class="flex-grow-1 overflow-auto">
            <div class="row g-3">
                <div v-for="preset in llmPresets" :key="preset.id" class="col-md-6 col-xl-4">
                    <div class="card h-100 shadow-sm border-0">
                        <div class="card-body">
                            <div class="d-flex justify-content-between">
                                <h5 class="card-title">{{ preset.name }}</h5>
                                <span class="badge bg-secondary">{{ preset.role }}</span>
                            </div>
                            <div class="small text-muted mb-2 mt-2">
                                <div class="text-truncate fw-bold" title="Model"><i class="bi bi-chat-text"></i> {{ preset.model_path.split('/').pop() }}</div>
                                <div v-if="preset.mmproj_path" class="text-truncate text-success" title="Vision Projector"><i class="bi bi-eye"></i> {{ preset.mmproj_path.split('/').pop() }}</div>
                                <div class="text-muted"><i class="bi bi-memory"></i> Context: {{ preset.n_ctx }}</div>
                            </div>
                            <div class="d-flex justify-content-between align-items-center mt-3">
                                <button class="btn btn-sm btn-outline-success" @click="store.loadLlmPreset(preset.id)" :disabled="isLoading">
                                    <i class="bi bi-play-fill"></i> Load
                                </button>
                                <div>
                                    <button class="btn btn-sm btn-outline-secondary me-1" @click="openPresetModal('llm', preset)"><i class="bi bi-pencil"></i></button>
                                    <button class="btn btn-sm btn-outline-danger" @click="deletePreset('llm', preset.id)"><i class="bi bi-trash"></i></button>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
                <div v-if="llmPresets.length === 0" class="col-12 text-center py-5 text-muted">
                    No intelligence presets. Create one to get started!
                </div>
            </div>
        </div>
    </div>

    <!-- Tags Content -->
    <div v-if="activeTab === 'tags'" class="flex-grow-1 d-flex flex-column overflow-hidden">
      <!-- Toolbar -->
      <div class="d-flex gap-2 mb-3 bg-body-secondary p-2 rounded">
        <input type="text" v-model="searchQuery" class="form-control form-control-sm" placeholder="Search tags..." style="max-width: 250px;">
        <div class="vr mx-1"></div>
        <button class="btn btn-sm btn-outline-danger" @click="showCleanupModal" :disabled="isLoading">
          <i class="bi bi-eraser"></i> Cleanup Unused Tags
        </button>
        <button class="btn btn-sm btn-outline-secondary ms-auto" @click="fetchTags" :disabled="isLoading">
          <i class="bi bi-arrow-repeat"></i> Refresh
        </button>
      </div>
      <!-- Table -->
      <div class="flex-grow-1 overflow-auto border rounded bg-body">
        <table class="table table-hover table-striped mb-0">
          <thead class="sticky-top bg-body-tertiary">
            <tr>
              <th scope="col" style="width: 50%">Name</th>
              <th scope="col">Category</th>
              <th scope="col" class="text-end">Usage Count</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="isLoading && tags.length === 0">
              <td colspan="3" class="text-center py-5 text-muted">Loading tags...</td>
            </tr>
            <tr v-else-if="filteredTags.length === 0">
              <td colspan="3" class="text-center py-5 text-muted">No tags found.</td>
            </tr>
            <tr v-for="tag in filteredTags" :key="tag.name">
              <td class="fw-medium">
                <a href="#" @click.prevent="navigateToTag(tag.name)" class="text-decoration-none text-body">
                   {{ tag.name }}
                   <span class="ms-1 small text-primary opacity-50"><i class="bi bi-box-arrow-up-right"></i></span>
                </a>
              </td>
              <td><span class="badge bg-secondary bg-opacity-25 text-body-emphasis border">{{ tag.category }}</span></td>
              <td class="text-end font-monospace">{{ tag.count }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="mt-2 text-muted x-small">
        Total tags: {{ tags.length }}
      </div>
    </div>

    <!-- Styles Content -->
    <div v-else-if="activeTab === 'styles'" class="flex-grow-1 d-flex flex-column overflow-hidden">
      <!-- Toolbar -->
      <div class="d-flex gap-2 mb-3 bg-body-secondary p-2 rounded">
        <input type="text" v-model="searchQuery" class="form-control form-control-sm" placeholder="Search styles..." style="max-width: 250px;">
        <button class="btn btn-sm btn-primary" @click="openStyleModal()">
          <i class="bi bi-plus-lg"></i> Add New Style
        </button>
        <button class="btn btn-sm btn-outline-success" @click="openExtractModal()">
          <i class="bi bi-magic"></i> Extract from Prompt
        </button>
        <button class="btn btn-sm btn-outline-info" @click="generateMissingPreviews()" :disabled="isLoading">
          <i class="bi bi-image"></i> Generate Missing
        </button>
        <button class="btn btn-sm btn-outline-secondary ms-auto" @click="store.fetchStyles()" :disabled="isLoading">
          <i class="bi bi-arrow-repeat"></i> Refresh
        </button>
      </div>
      <!-- Styles Grid -->
      <div class="flex-grow-1 overflow-auto">
        <div class="row g-3">
          <div v-for="style in filteredStyles" :key="style.name" class="col-md-6 col-xl-4">
            <div class="card h-100 shadow-sm border-0 overflow-hidden">
              <div class="style-preview-container position-relative bg-dark bg-opacity-10 d-flex align-items-center justify-content-center" style="height: 180px;">
                <img v-if="style.preview_path" :src="style.preview_path" class="w-100 h-100 object-fit-cover" alt="Style Preview">
                <div v-else class="text-center text-muted">
                  <div class="fs-1 opacity-25"><i class="bi bi-palette"></i></div>
                  <div class="x-small">No preview yet</div>
                </div>
                <div class="position-absolute top-0 end-0 m-2">
                   <button class="btn btn-xs btn-dark bg-opacity-50 border-0 rounded-circle p-1" @click="regeneratePreview(style)" title="Regenerate Preview">
                     <i class="bi bi-arrow-repeat"></i>
                   </button>
                </div>
              </div>
              <div class="card-body">
                <div class="d-flex justify-content-between align-items-start mb-2">
                  <h5 class="card-title mb-0">{{ style.name }}</h5>
                  <div class="btn-group">
                    <button class="btn btn-sm btn-outline-secondary" @click="openStyleModal(style)"><i class="bi bi-pencil"></i></button>
                    <button class="btn btn-sm btn-outline-danger" @click="confirmDeleteStyle(style.name)"><i class="bi bi-trash"></i></button>
                  </div>
                </div>
                <div class="mb-2">
                  <span class="x-small text-uppercase fw-bold text-muted d-block">Positive Prompt:</span>
                  <p class="small text-truncate mb-0 italic" :title="style.prompt">{{ style.prompt }}</p>
                </div>
                <div v-if="style.negative_prompt">
                  <span class="x-small text-uppercase fw-bold text-muted d-block">Negative Prompt:</span>
                  <p class="small text-truncate mb-0 italic text-secondary" :title="style.negative_prompt">{{ style.negative_prompt }}</p>
                </div>
              </div>
            </div>
          </div>
          <div v-if="filteredStyles.length === 0" class="col-12 text-center py-5 text-muted">
             No styles found. Create one to get started!
          </div>
        </div>
      </div>
    </div>

    <!-- Models Content (File View) -->
    <div v-else-if="activeTab === 'models'" class="flex-grow-1 d-flex flex-column overflow-hidden">
      <!-- Toolbar -->
      <div class="d-flex gap-2 mb-3 bg-body-secondary p-2 rounded">
        <input type="text" v-model="searchQuery" class="form-control form-control-sm" placeholder="Search models..." style="max-width: 250px;">
        <button class="btn btn-sm btn-outline-primary" @click="syncModels()" :disabled="isLoading">
          <i class="bi bi-arrow-repeat"></i> Sync Models from Disk
        </button>
      </div>
      <!-- Models Table -->
      <div class="flex-grow-1 overflow-auto bg-body rounded shadow-sm">
        <table class="table table-hover mb-0 align-middle">
          <thead class="table-light sticky-top">
            <tr>
              <th>Model ID</th>
              <th>Type</th>
              <th>Base</th>
              <th>Components</th>
              <th>Default Params</th>
              <th class="text-end">Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="model in filteredModelMetadata.filter(m => m.metadata.type !== 'llm' && m.metadata.type !== 'lora')" :key="model.id">
              <td>
                <div class="fw-bold">{{ model.id }}</div>
                <div class="x-small text-muted" v-if="model.metadata.name">{{ model.metadata.name }}</div>
              </td>
              <td>
                <span class="badge bg-secondary opacity-75">{{ model.metadata.type || 'unknown' }}</span>
              </td>
              <td>
                <span class="badge bg-info bg-opacity-10 text-info border border-info border-opacity-25">{{ model.metadata.base || 'N/A' }}</span>
              </td>
              <td>
                <div class="x-small" v-if="model.metadata.vae">
                  <span class="text-muted text-uppercase fw-bold" style="font-size: 0.6rem;">VAE:</span> 
                  <span class="text-truncate d-inline-block align-middle ms-1" style="max-width: 120px;" :title="model.metadata.vae">{{ model.metadata.vae.split('/').pop() }}</span>
                </div>
                <div class="x-small" v-if="model.metadata.llm">
                  <span class="text-muted text-uppercase fw-bold" style="font-size: 0.6rem;">LLM:</span> 
                  <span class="text-truncate d-inline-block align-middle ms-1" style="max-width: 120px;" :title="model.metadata.llm">{{ model.metadata.llm.split('/').pop() }}</span>
                </div>
                <div class="x-small text-muted italic" v-if="!model.metadata.vae && !model.metadata.llm">Standard</div>
              </td>
              <td>
                <div class="x-small">
                  <span class="me-2" title="CFG Scale"><i class="bi bi-sliders"></i> {{ model.metadata.cfg_scale || '7.0' }}</span>
                  <span class="me-2" title="Steps"><i class="bi bi-bar-chart-steps"></i> {{ model.metadata.sample_steps || '20' }}</span>
                  <span class="me-2" title="Resolution"><i class="bi bi-aspect-ratio"></i> {{ model.metadata.width || '512' }}x{{ model.metadata.height || '512' }}</span>
                </div>
              </td>
              <td class="text-end">
                <button class="btn btn-sm btn-outline-secondary" @click="openModelEditModal(model)">
                  <i class="bi bi-pencil"></i> Edit
                </button>
              </td>
            </tr>
            <tr v-if="filteredModelMetadata.filter(m => m.metadata.type !== 'llm' && m.metadata.type !== 'lora').length === 0">
              <td colspan="5" class="text-center py-5 text-muted">
                No models found in database. Try syncing from disk.
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- LLMs Content (File View) -->
    <div v-else-if="activeTab === 'llms'" class="flex-grow-1 d-flex flex-column overflow-hidden">
      <!-- Toolbar -->
      <div class="d-flex gap-2 mb-3 bg-body-secondary p-2 rounded">
        <input type="text" v-model="searchQuery" class="form-control form-control-sm" placeholder="Search LLMs..." style="max-width: 250px;">
        <button class="btn btn-sm btn-outline-primary" @click="syncModels()" :disabled="isLoading">
          <i class="bi bi-arrow-repeat"></i> Sync Models from Disk
        </button>
      </div>
      <!-- LLMs Table -->
      <div class="flex-grow-1 overflow-auto bg-body rounded shadow-sm">
        <table class="table table-hover mb-0 align-middle">
          <thead class="table-light sticky-top">
            <tr>
              <th>Model ID</th>
              <th>Vision Projector</th>
              <th>Context</th>
              <th class="text-end">Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="model in filteredModelMetadata.filter(m => m.metadata.type === 'llm')" :key="model.id">
              <td>
                <div class="fw-bold">{{ model.metadata.name || model.id }}</div>
                <div class="x-small text-muted font-monospace">{{ model.id }}</div>
              </td>
              <td>
                <span v-if="model.metadata.mmproj" class="badge bg-success bg-opacity-10 text-success border border-success border-opacity-25">
                  <i class="bi bi-eye"></i> {{ model.metadata.mmproj.split('/').pop() }}
                </span>
                <span v-else class="text-muted x-small italic">None (Text Only)</span>
              </td>
              <td>
                <span class="badge bg-secondary bg-opacity-10 text-secondary">{{ model.metadata.n_ctx || '2048' }}</span>
              </td>
              <td class="text-end">
                <button class="btn btn-sm btn-outline-secondary" @click="openModelEditModal(model)">
                  <i class="bi bi-pencil"></i> Edit
                </button>
              </td>
            </tr>
            <tr v-if="filteredModelMetadata.filter(m => m.metadata.type === 'llm').length === 0">
              <td colspan="4" class="text-center py-5 text-muted">
                No LLM models found. Try syncing from disk.
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- LoRAs Content -->
    <div v-else-if="activeTab === 'loras'" class="flex-grow-1 d-flex flex-column overflow-hidden">
      <!-- Toolbar -->
      <div class="d-flex gap-2 mb-3 bg-body-secondary p-2 rounded">
        <input type="text" v-model="searchQuery" class="form-control form-control-sm" placeholder="Search LoRAs..." style="max-width: 250px;">
        <button class="btn btn-sm btn-outline-primary" @click="syncModels()" :disabled="isLoading">
          <i class="bi bi-arrow-repeat"></i> Sync LoRAs from Disk
        </button>
        <button class="btn btn-sm btn-outline-info ms-auto" @click="generateMissingLoraPreviews()" :disabled="isLoading">
          <i class="bi bi-image"></i> Generate Missing Previews
        </button>
      </div>
      <!-- LoRAs Grid -->
      <div class="flex-grow-1 overflow-auto">
        <div class="row g-3">
          <div v-for="model in filteredModelMetadata.filter(m => m.metadata.type === 'lora')" :key="model.id" class="col-md-6 col-xl-4">
            <div class="card h-100 shadow-sm border-0 overflow-hidden lora-card" :class="{ 'has-trigger': model.metadata.trigger_word }">
              <div class="lora-preview-container position-relative bg-dark bg-opacity-10 d-flex align-items-center justify-content-center" style="height: 160px;">
                <img v-if="model.metadata.preview_path" :src="model.metadata.preview_path" class="w-100 h-100 object-fit-cover" alt="LoRA Preview">
                <div v-else class="text-center text-muted">
                  <div class="fs-1 opacity-25"><i class="bi bi-plugin"></i></div>
                  <div class="x-small">No preview</div>
                </div>
                <div class="position-absolute top-0 end-0 m-2">
                   <button class="btn btn-xs btn-dark bg-opacity-50 border-0 rounded-circle p-1" @click="regenerateLoraPreview(model)" title="Regenerate Preview">
                     <i class="bi bi-arrow-repeat"></i>
                   </button>
                </div>
                <div class="position-absolute bottom-0 start-0 m-2" v-if="model.metadata.base">
                  <span class="badge bg-dark bg-opacity-75 x-small">{{ model.metadata.base }}</span>
                </div>
              </div>
              <div class="card-body p-3">
                <div class="d-flex justify-content-between align-items-start mb-2">
                  <h6 class="card-title mb-0 text-truncate" :title="model.metadata.name || model.id">{{ model.metadata.name || model.id.split('/').pop().replace(/\.[^/.]+$/, "") }}</h6>
                  <button class="btn btn-xs btn-outline-secondary border-0" @click="openModelEditModal(model)"><i class="bi bi-pencil"></i></button>
                </div>
                <div class="mb-1" v-if="model.metadata.trigger_word">
                  <span class="x-small text-uppercase fw-bold text-primary d-block mb-1">Trigger Word:</span>
                  <code class="small px-2 py-1 bg-primary bg-opacity-10 text-primary rounded d-inline-block">{{ model.metadata.trigger_word }}</code>
                </div>
                <div class="mt-2 text-muted x-small text-truncate" :title="model.id">
                  <i class="bi bi-file-earmark-binary"></i> {{ model.id }}
                </div>
              </div>
            </div>
          </div>
          <div v-if="filteredModelMetadata.filter(m => m.metadata.type === 'lora').length === 0" class="col-12 text-center py-5 text-muted">
             No LoRA models found. Sync from disk to see them here!
          </div>
        </div>
      </div>
    </div>

    <!-- Placeholders for other tabs -->
    <div v-else class="flex-grow-1 d-flex align-items-center justify-content-center text-muted">
      <div class="text-center">
        <h4><i class="bi bi-cone-striped"></i> Work in Progress</h4>
        <p>This section is coming soon.</p>
      </div>
    </div>

    <!-- Cleanup Confirmation Modal -->
    <Teleport to="body">
    <div class="modal fade" ref="modalRef" tabindex="-1" aria-hidden="true">
      <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content">
          <div class="modal-header border-bottom-0">
            <h5 class="modal-title text-danger"><i class="bi bi-eraser"></i> Cleanup Tags</h5>
            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
          </div>
          <div class="modal-body text-center py-4">
            <div class="fs-1 mb-3"><i class="bi bi-trash"></i></div>
            <p class="mb-0">Are you sure you want to delete all tags that are not assigned to any image?</p>
            <p class="text-muted small mt-2">This action cannot be undone.</p>
          </div>
          <div class="modal-footer border-top-0 justify-content-center pb-4">
            <button type="button" class="btn btn-secondary px-4" data-bs-dismiss="modal">Cancel</button>
            <button type="button" class="btn btn-danger px-4" @click="confirmCleanup">Cleanup</button>
          </div>
        </div>
      </div>
    </div>
    </Teleport>

    <Teleport to="body">
    <div class="modal fade" ref="extractModalRef" tabindex="-1" aria-hidden="true">
      <div class="modal-dialog modal-lg modal-dialog-centered">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">🪄 Extract Styles from Prompt</h5>
            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
          </div>
          <div class="modal-body">
            <p class="text-muted small">Paste a detailed image description or prompt. The LLM will analyze it and extract reusable art styles, artists, or aesthetics as new style presets.</p>
            <div class="mb-3">
              <textarea v-model="extractionPrompt" class="form-control" rows="5" placeholder="e.g. A futuristic city with neon lights, cyberpunk aesthetic, blade runner style, highly detailed..."></textarea>
            </div>
            <div v-if="extractionResult" class="alert alert-danger">{{ extractionResult }}</div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
            <button type="button" class="btn btn-success" @click="doExtract" :disabled="!extractionPrompt || isExtracting">
                <span v-if="isExtracting" class="spinner-border spinner-border-sm me-1"></span>
                Extract & Save
            </button>
          </div>
        </div>
      </div>
    </div>
    </Teleport>

    <!-- Style Edit Modal -->
    <Teleport to="body">
    <div class="modal fade" ref="styleModalRef" tabindex="-1" aria-hidden="true">
      <div class="modal-dialog modal-lg modal-dialog-centered">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">{{ isEditing ? 'Edit Style' : 'New Style' }}</h5>
            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
          </div>
          <div class="modal-body">
            <div class="mb-3">
              <label class="form-label fw-bold">Name</label>
              <input type="text" v-model="editingStyle.name" class="form-control" placeholder="Style Name (e.g. Cinematic)" :disabled="isEditing">
            </div>
            <div class="mb-3">
              <label class="form-label fw-bold">Positive Prompt Template</label>
              <textarea v-model="editingStyle.prompt" class="form-control" rows="3" placeholder="Use {prompt} to inject original prompt"></textarea>
              <div class="form-text x-small">Example: <code>{prompt}, (masterpiece), 8k, detailed</code></div>
            </div>
            <div class="mb-3">
              <label class="form-label fw-bold">Negative Prompt Template</label>
              <textarea v-model="editingStyle.negative_prompt" class="form-control" rows="2" placeholder="Tags to always avoid in this style"></textarea>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
            <button type="button" class="btn btn-primary" @click="saveStyle" :disabled="!editingStyle.name">Save Style</button>
          </div>
        </div>
      </div>
    </div>
    </Teleport>

    <!-- Preset Edit Modal -->
    <Teleport to="body">
    <div class="modal fade" ref="presetModalRef" tabindex="-1" aria-hidden="true">
      <div class="modal-dialog modal-lg modal-dialog-centered">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">{{ isEditingPreset ? 'Edit' : 'New' }} Preset</h5>
            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
          </div>
          <div class="modal-body">
            
            <!-- Image Preset Form -->
            <div v-if="activeTab === 'image_presets'" class="row g-3">
               <div class="col-12">
                 <label class="form-label fw-bold">Preset Name</label>
                 <input type="text" v-model="editingImagePreset.name" class="form-control" placeholder="e.g. Flux Dev + FP8 T5">
               </div>
               
               <div class="col-12"><hr class="my-2"></div>
               
               <div class="col-12">
                 <label class="form-label small text-uppercase fw-bold">UNet / Main Model (Required)</label>
                 <select v-model="editingImagePreset.unet_path" class="form-select">
                    <option value="">Select Model...</option>
                    <option v-for="m in availableModels" :key="m.id" :value="m.id">{{ m.name }} ({{ m.type }})</option>
                 </select>
               </div>
               
               <div class="col-md-6">
                 <label class="form-label small text-uppercase fw-bold">VAE (Optional)</label>
                 <select v-model="editingImagePreset.vae_path" class="form-select">
                    <option value="">None (Use Embedded)</option>
                    <option v-for="m in availableVaEs" :key="m.id" :value="m.id">{{ m.name }}</option>
                 </select>
               </div>
               
               <div class="col-md-6">
                 <label class="form-label small text-uppercase fw-bold">T5 / Text Encoder 2 (Optional)</label>
                 <select v-model="editingImagePreset.t5xxl_path" class="form-select">
                    <option value="">None (Use Embedded)</option>
                    <option v-for="m in availableClips" :key="m.id" :value="m.id">{{ m.name }}</option>
                 </select>
               </div>
               
               <div class="col-md-6">
                 <label class="form-label small text-uppercase fw-bold">CLIP L (Optional)</label>
                 <select v-model="editingImagePreset.clip_l_path" class="form-select">
                    <option value="">None</option>
                    <option v-for="m in availableClips" :key="m.id" :value="m.id">{{ m.name }}</option>
                 </select>
               </div>
               
               <div class="col-md-6">
                 <label class="form-label small text-uppercase fw-bold">CLIP G (Optional)</label>
                 <select v-model="editingImagePreset.clip_g_path" class="form-select">
                    <option value="">None</option>
                    <option v-for="m in availableClips" :key="m.id" :value="m.id">{{ m.name }}</option>
                 </select>
               </div>
               
               <div class="col-12">
                   <div class="form-text x-small">Note: For Flux/SD3, ensure you select the correct CLIP/T5 combination if not using a GGUF that embeds them.</div>
               </div>
            </div>

            <!-- LLM Preset Form -->
            <div v-else class="row g-3">
               <div class="col-12">
                 <label class="form-label fw-bold">Preset Name</label>
                 <input type="text" v-model="editingLlmPreset.name" class="form-control" placeholder="e.g. Qwen2-VL Vision">
               </div>
               
               <div class="col-md-6">
                 <label class="form-label small text-uppercase fw-bold">Role</label>
                 <select v-model="editingLlmPreset.role" class="form-select">
                    <option value="Assistant">Assistant (Chat/Tools)</option>
                    <option value="Vision">Vision (Tagging/Analysis)</option>
                    <option value="Roleplay">Roleplay</option>
                 </select>
               </div>
               
               <div class="col-12"><hr class="my-2"></div>
               
               <div class="col-12">
                 <label class="form-label small text-uppercase fw-bold">LLM Model (Required)</label>
                 <select v-model="editingLlmPreset.model_path" class="form-select">
                    <option value="">Select Model...</option>
                    <option v-for="m in availableLlms" :key="m.id" :value="m.id">{{ m.name }}</option>
                 </select>
               </div>
               
               <div class="col-12">
                 <label class="form-label small text-uppercase fw-bold">Vision Projector (mmproj) (Optional)</label>
                 <input type="text" v-model="editingLlmPreset.mmproj_path" class="form-control" placeholder="e.g. mmproj-model-f16.gguf">
                 <div class="form-text x-small">Filename of the projector in the 'models' directory.</div>
               </div>
               
               <div class="col-md-6">
                 <label class="form-label small text-uppercase fw-bold">Context Size</label>
                 <input type="number" v-model="editingLlmPreset.n_ctx" class="form-control" step="1024">
               </div>
            </div>

          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
            <button type="button" class="btn btn-primary" @click="savePreset(activeTab === 'image_presets' ? 'image' : 'llm')">Save Preset</button>
          </div>
        </div>
      </div>
    </div>
    </Teleport>

    <!-- Model Edit Modal -->
    <Teleport to="body">
    <div class="modal fade" ref="modelEditModalRef" tabindex="-1" aria-hidden="true">
      <div class="modal-dialog modal-lg modal-dialog-centered">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">Edit Model: {{ editingModelMetadata.id }}</h5>
            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
          </div>
          <div class="modal-body">
            <div class="row g-3">
              <div class="col-md-12">
                <label class="form-label fw-bold">Display Name</label>
                <input type="text" v-model="editingModelMetadata.metadata.name" class="form-control" placeholder="Friendly name for the model">
              </div>
              <div class="col-md-6">
                <label class="form-label fw-bold small text-uppercase">Type</label>
                <select v-model="editingModelMetadata.metadata.type" class="form-select">
                  <option value="stable-diffusion">Stable Diffusion (Standard)</option>
                  <option value="lora">LoRA</option>
                  <option value="llm">LLM (Chat/Vision)</option>
                  <option value="flux">Flux (GGUF)</option>
                  <option value="sd3">SD3 (GGUF)</option>
                  <option value="upscaler">Upscaler (ESRGAN)</option>
                </select>
              </div>
              <div class="col-md-6">
                <label class="form-label fw-bold small text-uppercase">Base Model</label>
                <select v-model="editingModelMetadata.metadata.base" class="form-select">
                  <option value="SD1.5">SD 1.5</option>
                  <option value="SDXL">SDXL</option>
                  <option value="SD3">SD 3.x</option>
                  <option value="Flux">Flux.1</option>
                  <option value="Other">Other</option>
                </select>
              </div>
              
              <!-- Trigger Word (LoRA Only) -->
              <div class="col-md-12" v-if="editingModelMetadata.metadata.type === 'lora'">
                <label class="form-label fw-bold small text-uppercase text-primary">Trigger Word</label>
                <input type="text" v-model="editingModelMetadata.metadata.trigger_word" class="form-control border-primary" placeholder="e.g. ohwx, style of...">
                <div class="form-text x-small">This word will be automatically added to the prompt when you activate this LoRA.</div>
              </div>

              <!-- LLM Specific -->
              <div class="col-md-12" v-if="editingModelMetadata.metadata.type === 'llm'">
                <div class="col-12"><hr class="my-2"></div>
                <label class="form-label fw-bold small text-uppercase text-success">Multimodal Projector (Vision)</label>
                <input type="text" v-model="editingModelMetadata.metadata.mmproj" class="form-control border-success" placeholder="e.g. mmproj-model-f16.gguf">
                <div class="form-text x-small">Filename of the vision projector (must be in models directory). Required for image analysis.</div>
                
                <div class="row mt-2">
                    <div class="col-6">
                        <label class="form-label fw-bold small text-uppercase">Context Size</label>
                        <input type="number" v-model.number="editingModelMetadata.metadata.n_ctx" class="form-control" step="1024" placeholder="2048">
                    </div>
                    <div class="col-6">
                        <label class="form-label fw-bold small text-uppercase">Max Image Tokens</label>
                        <input type="number" v-model.number="editingModelMetadata.metadata.image_max_tokens" class="form-control" step="256" placeholder="-1 (Auto)">
                    </div>
                </div>
              </div>

              <!-- Advanced Components (Base Model Only) -->
              <template v-if="editingModelMetadata.metadata.type !== 'lora' && editingModelMetadata.metadata.type !== 'upscaler' && editingModelMetadata.metadata.type !== 'llm'">
                <div class="col-12"><hr class="my-2"></div>
                <div class="col-md-6">
                  <label class="form-label fw-bold small text-uppercase">VAE Path</label>
                  <input type="text" v-model="editingModelMetadata.metadata.vae" class="form-control" placeholder="e.g. vae/ae.safetensors">
                </div>
                <div class="col-md-6">
                  <label class="form-label fw-bold small text-uppercase">Text Encoder / LLM Path</label>
                  <input type="text" v-model="editingModelMetadata.metadata.llm" class="form-control" placeholder="e.g. text-encoder/Qwen.gguf">
                </div>
                <div class="col-12"><hr class="my-2"></div>
                <div class="col-md-4">
                  <label class="form-label fw-bold small text-uppercase">Default CFG</label>
                  <input type="number" v-model.number="editingModelMetadata.metadata.cfg_scale" class="form-control" step="0.5">
                </div>
                <div class="col-md-4">
                  <label class="form-label fw-bold small text-uppercase">Default Steps</label>
                  <input type="number" v-model.number="editingModelMetadata.metadata.sample_steps" class="form-control">
                </div>
                <div class="col-md-4">
                  <label class="form-label fw-bold small text-uppercase">Default Sampler</label>
                  <select v-model="editingModelMetadata.metadata.sampling_method" class="form-select">
                    <option v-for="s in store.samplers" :key="s" :value="s">{{ s }}</option>
                  </select>
                </div>
                <div class="col-md-6">
                  <label class="form-label fw-bold small text-uppercase">Default Width</label>
                  <input type="number" v-model.number="editingModelMetadata.metadata.width" class="form-control" step="64">
                </div>
                <div class="col-md-6">
                  <label class="form-label fw-bold small text-uppercase">Default Height</label>
                  <input type="number" v-model.number="editingModelMetadata.metadata.height" class="form-control" step="64">
                </div>
              </template>
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancel</button>
            <button type="button" class="btn btn-primary" @click="saveModelMetadata">Save Parameters</button>
          </div>
        </div>
      </div>
    </div>
    </Teleport>

    <!-- Style Delete Modal -->
    <Teleport to="body">
    <div class="modal fade" ref="styleDeleteModalRef" tabindex="-1" aria-hidden="true">
      <div class="modal-dialog modal-sm modal-dialog-centered">
        <div class="modal-content">
          <div class="modal-header border-bottom-0">
            <h5 class="modal-title text-danger">Delete Style?</h5>
            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
          </div>
          <div class="modal-body text-center">
            Are you sure you want to delete <strong>{{ styleToDelete }}</strong>?
          </div>
          <div class="modal-footer border-top-0 justify-content-center">
            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">No</button>
            <button type="button" class="btn btn-danger" @click="doDeleteStyle">Yes, Delete</button>
          </div>
        </div>
      </div>
    </div>
    </Teleport>
  </div>
</template>

<style scoped>
.x-small {
  font-size: 0.75rem;
}
.italic {
  font-style: italic;
}
.btn-xs {
  padding: 0.1rem 0.25rem;
  font-size: 0.65rem;
  line-height: 1;
}
.style-preview-container .btn-xs {
  opacity: 0;
  transition: opacity 0.2s;
}
.style-preview-container:hover .btn-xs {
  opacity: 1;
}
</style>
