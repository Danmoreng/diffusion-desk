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

const router = useRouter()
const store = useGenerationStore()
const activeTab = ref('tags')
const tags = ref<TagInfo[]>([])
const isLoading = ref(false)
const searchQuery = ref('')

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
        // Maybe show a toast or success message?
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
      await fetchTags() // Refresh list
    }
  } catch (e) {
    console.error('Failed to cleanup tags', e)
  } finally {
    isLoading.value = false
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
  // Clear path and save to trigger background generation
  const updated = { ...style, preview_path: '' }
  await store.saveStyle(updated)
}

async function generateMissingPreviews() {
  isLoading.value = true
  try {
    const res = await fetch('/v1/styles/previews/fix', { method: 'POST' })
    if (res.ok) {
      const data = await res.json()
      console.log(`Triggered generation for ${data.count} styles`)
      // Previews happen in background, but we refresh list to see progress if any finished instantly
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
})

onUnmounted(() => {
  modalInstance?.dispose()
  styleModalInstance?.dispose()
  styleDeleteModalInstance?.dispose()
  extractModalInstance?.dispose()
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
        <a class="nav-link" :class="{ active: activeTab === 'tags' }" href="#" @click.prevent="activeTab = 'tags'">Tags</a>
      </li>
      <li class="nav-item">
        <a class="nav-link" :class="{ active: activeTab === 'styles' }" href="#" @click.prevent="activeTab = 'styles'">Styles</a>
      </li>
      <li class="nav-item">
        <a class="nav-link disabled" href="#" title="Coming soon">LoRAs</a>
      </li>
      <li class="nav-item">
        <a class="nav-link disabled" href="#" title="Coming soon">Embeddings</a>
      </li>
    </ul>

    <!-- Tags Content -->
    <div v-if="activeTab === 'tags'" class="flex-grow-1 d-flex flex-column overflow-hidden">
      
      <!-- Toolbar -->
      <div class="d-flex gap-2 mb-3 bg-body-secondary p-2 rounded">
        <input type="text" v-model="searchQuery" class="form-control form-control-sm" placeholder="Search tags..." style="max-width: 250px;">
        <div class="vr mx-1"></div>
        <button class="btn btn-sm btn-outline-danger" @click="showCleanupModal" :disabled="isLoading">
          🧹 Cleanup Unused Tags
        </button>
        <button class="btn btn-sm btn-outline-secondary ms-auto" @click="fetchTags" :disabled="isLoading">
          🔄 Refresh
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
                   <span class="ms-1 small text-primary opacity-50">↗</span>
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
          ➕ Add New Style
        </button>
        <button class="btn btn-sm btn-outline-success" @click="openExtractModal()">
          🪄 Extract from Prompt
        </button>
        <button class="btn btn-sm btn-outline-info" @click="generateMissingPreviews()" :disabled="isLoading">
          🖼️ Generate Missing
        </button>
        <button class="btn btn-sm btn-outline-secondary ms-auto" @click="store.fetchStyles()" :disabled="isLoading">
          🔄 Refresh
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
                  <div class="fs-1 opacity-25">🎨</div>
                  <div class="x-small">No preview yet</div>
                </div>
                <div class="position-absolute top-0 end-0 m-2">
                   <button class="btn btn-xs btn-dark bg-opacity-50 border-0 rounded-circle p-1" @click="regeneratePreview(style)" title="Regenerate Preview">
                     🔄
                   </button>
                </div>
              </div>
              <div class="card-body">
                <div class="d-flex justify-content-between align-items-start mb-2">
                  <h5 class="card-title mb-0">{{ style.name }}</h5>
                  <div class="btn-group">
                    <button class="btn btn-sm btn-outline-secondary" @click="openStyleModal(style)">✏️</button>
                    <button class="btn btn-sm btn-outline-danger" @click="confirmDeleteStyle(style.name)">🗑️</button>
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

    <!-- Placeholders for other tabs -->
    <div v-else class="flex-grow-1 d-flex align-items-center justify-content-center text-muted">
      <div class="text-center">
        <h4>🚧 Work in Progress</h4>
        <p>This section is coming soon.</p>
      </div>
    </div>

    <!-- Cleanup Confirmation Modal -->
    <Teleport to="body">
    <div class="modal fade" ref="modalRef" tabindex="-1" aria-hidden="true">
      <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content">
          <div class="modal-header border-bottom-0">
            <h5 class="modal-title text-danger">🧹 Cleanup Tags</h5>
            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
          </div>
          <div class="modal-body text-center py-4">
            <div class="fs-1 mb-3">🗑️</div>
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