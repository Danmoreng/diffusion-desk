<script setup lang="ts">
import { ref, onMounted, nextTick, computed, watch } from 'vue'
import { Modal, Carousel } from 'bootstrap'
import { useGenerationStore } from '@/stores/generation'
import { useRouter } from 'vue-router'
import DeleteConfirmationModal from './DeleteConfirmationModal.vue'
import RatingInput from './RatingInput.vue'

interface HistoryItem {
  id: string
  name: string
  params?: any
  tags?: string[]
  is_favorite?: boolean
  rating?: number
}

const props = defineProps<{
  initialTags?: string[]
}>()

const emit = defineEmits<{
  (e: 'tag-click', tag: string): void
}>()

const store = useGenerationStore()
const router = useRouter()

const images = ref<HistoryItem[]>([])
const allTags = ref<any[]>([])
const isLoading = ref(true)
const error = ref<string | null>(null)

// Selection State
const isSelectionMode = ref(false)
const selectedImages = ref<string[]>([])

// Deletion State
const showDeleteModal = ref(false)
const imagesToDelete = ref<HistoryItem[]>([])

// Filtering State
const selectedModel = ref('all')
const selectedDateRange = ref('all') // all, today, yesterday, week, month, custom
const minRating = ref(0)
const showFavoritesOnly = ref(false) // Deprecated filter
const selectedTags = ref<string[]>(props.initialTags || [])
const startDate = ref('')
const endDate = ref('')

const newTagInput = ref('')
let currentFetchId = 0

const availableModels = computed(() => {
  const models = new Set<string>()
  images.value.forEach(img => {
    if (img.params?.model) models.add(img.params.model)
  })
  return Array.from(models).sort()
})

const availableTags = computed(() => {
  return allTags.value.map(t => t.name).sort()
})

// --- Selection Methods ---

function toggleSelectionMode() {
  isSelectionMode.value = !isSelectionMode.value
  selectedImages.value = []
}

function toggleSelection(uuid: string) {
  if (selectedImages.value.includes(uuid)) {
    selectedImages.value = selectedImages.value.filter(id => id !== uuid)
  } else {
    selectedImages.value.push(uuid)
  }
}

function selectAll() {
  selectedImages.value = filteredImages.value.map(i => i.id)
}

function deselectAll() {
  selectedImages.value = []
}

function deleteSelected() {
  const items = images.value.filter(i => selectedImages.value.includes(i.id))
  if (items.length > 0) {
    imagesToDelete.value = items
    showDeleteModal.value = true
  }
}

// --- Tag & Rating Methods ---

async function addTag(uuid: string) {
  if (!newTagInput.value.trim()) return
  try {
    const response = await fetch('/v1/history/tags', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ uuid, tag: newTagInput.value.trim() })
    })
    if (response.ok) {
      // Update local state
      const img = images.value.find(i => i.id === uuid)
      if (img) {
        if (!img.tags) img.tags = []
        if (!img.tags.includes(newTagInput.value.trim())) {
          img.tags.push(newTagInput.value.trim())
        }
      }
      newTagInput.value = ''
      // Refresh global tag list
      const tagsRes = await fetch('/v1/history/tags')
      if (tagsRes.ok) allTags.value = await tagsRes.json()
    }
  } catch (e) { console.error('Failed to add tag:', e) }
}

async function removeTag(uuid: string, tag: string) {
  try {
    const response = await fetch('/v1/history/tags', {
      method: 'DELETE',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ uuid, tag })
    })
    if (response.ok) {
      // Update local state
      const img = images.value.find(i => i.id === uuid)
      if (img && img.tags) {
        img.tags = img.tags.filter(t => t !== tag)
      }
      // Refresh global tag list
      const tagsRes = await fetch('/v1/history/tags')
      if (tagsRes.ok) allTags.value = await tagsRes.json()
    }
  } catch (e) { console.error('Failed to remove tag:', e) }
}

async function setRating(uuid: string, rating: number) {
  const img = images.value.find(i => i.id === uuid)
  if (!img) return
  try {
    const response = await fetch('/v1/history/rating', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ uuid, rating })
    })
    if (response.ok) {
      img.rating = rating
    }
  } catch (e) { console.error('Failed to set rating:', e) }
}

// --- Deletion Logic ---

function deleteImage(uuid: string) {
  const img = images.value.find(i => i.id === uuid)
  if (img) {
    imagesToDelete.value = [img]
    showDeleteModal.value = true
  }
}

async function performDelete(payload: { deleteFile: boolean }) {
  const items = imagesToDelete.value
  if (!items.length) return

  // Check if we are in modal view (and strictly in modal view, not selection mode batch delete)
  // We use the modalElement visibility check.
  const isModalOpen = modalInstance && modalElement.value?.classList.contains('show') && !isSelectionMode.value

  try {
    // Perform deletions in parallel
    const promises = items.map(async (item) => {
        const url = `/v1/history/images/${item.id}` + (payload.deleteFile ? '?delete_file=true' : '')
        const res = await fetch(url, { method: 'DELETE' })
        return { id: item.id, ok: res.ok }
    })
    
    const results = await Promise.all(promises)
    const successIds = new Set(results.filter(r => r.ok).map(r => r.id))
    
    // Update local state
    images.value = images.value.filter(i => !successIds.has(i.id))
    selectedImages.value = selectedImages.value.filter(id => !successIds.has(id))

    // Handle Modal Logic (Keep Open)
    if (isModalOpen) {
        await nextTick() // Wait for filteredImages to update
        
        if (filteredImages.value.length === 0) {
            modalInstance?.hide()
        } else {
            // Adjust activeIndex if out of bounds
            if (activeIndex.value >= filteredImages.value.length) {
                activeIndex.value = Math.max(0, filteredImages.value.length - 1)
            }
            // Sync carousel
            // .to() usually works if the index is valid. 
            // Since elements are reactive, the carousel might need a moment or a forced update.
            // But Bootstrap 5 carousel with Vue is tricky. 
            // If the active item was deleted, the carousel has no 'active' item in DOM initially.
            // We might need to manually set the class 'active' on the new item if Bootstrap fails.
            // Let's rely on .to() first.
            carouselInstance?.to(activeIndex.value)
        }
    }

  } catch (e) { 
    console.error('Failed to delete image(s):', e) 
  } finally {
    showDeleteModal.value = false
    imagesToDelete.value = []
    
    // If list is empty, exit selection mode
    if (images.value.length === 0) {
        isSelectionMode.value = false
    }
  }
}

const getTimestamp = (filename: string) => {
  try {
    const parts = filename.split('-')
    if (parts.length >= 2) {
      return parseInt(parts[1]) / 1000
    }
  } catch (e) { /* ignore */ }
  return 0
}

const filteredImages = computed(() => {
  let result = images.value

  // Filter by min rating (client-side filter for immediate response, though we also fetch with it)
  // Note: Since we fetch with min_rating, this is just a secondary filter if we change filters without refetching everything
  // But we watch filters and refetch, so this might be redundant but safe.
  if (minRating.value > 0) {
    result = result.filter(img => (img.rating || 0) >= minRating.value)
  }

  // Filter by model
  if (selectedModel.value !== 'all') {
    result = result.filter(img => img.params?.model === selectedModel.value)
  }

  // Filter by date range preset
  if (selectedDateRange.value !== 'all') {
    const now = new Date()
    const today = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
    
    if (selectedDateRange.value === 'custom') {
      if (startDate.value) {
        const start = new Date(startDate.value).getTime()
        result = result.filter(img => getTimestamp(img.name) >= start)
      }
      if (endDate.value) {
        const end = new Date(endDate.value).getTime()
        result = result.filter(img => getTimestamp(img.name) <= end)
      }
    } else {
      const yesterday = today - 86400000
      const lastWeek = today - 7 * 86400000
      const lastMonth = today - 30 * 86400000

      result = result.filter(img => {
        const ts = getTimestamp(img.name)
        if (selectedDateRange.value === 'today') return ts >= today
        if (selectedDateRange.value === 'yesterday') return ts >= yesterday && ts < today
        if (selectedDateRange.value === 'week') return ts >= lastWeek
        if (selectedDateRange.value === 'month') return ts >= lastMonth
        return true
      })
    }
  }

  return result
})

// Format timestamp from filename (img-MICROSECONDS-SEED.png)
const formatDate = (filename: string) => {
  try {
    const parts = filename.split('-')
    if (parts.length >= 2) {
      const ms = parseInt(parts[1]) / 1000
      return new Date(ms).toLocaleString(undefined, {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
      })
    }
  } catch (e) {
    /* ignore */
  }
  return filename
}

const modalElement = ref<HTMLElement | null>(null)
const carouselElement = ref<HTMLElement | null>(null)
let modalInstance: Modal | null = null
let carouselInstance: Carousel | null = null

const activeIndex = ref(0)

// Gallery Layout State
const columnsPerRow = ref(Number(localStorage.getItem('gallery-columns')) || 4)

const setColumns = (count: number) => {
  columnsPerRow.value = Math.max(2, Math.min(12, count))
  localStorage.setItem('gallery-columns', String(columnsPerRow.value))
}

function toggleFilterTag(tag: string) {
  if (selectedTags.value.includes(tag)) {
    selectedTags.value = selectedTags.value.filter(t => t !== tag)
  } else {
    selectedTags.value.push(tag)
  }
}

// Expose for parent component
defineExpose({
  columnsPerRow,
  setColumns,
  selectedModel,
  selectedDateRange,
  minRating,
  selectedTags,
  toggleFilterTag,
  startDate,
  endDate,
  availableModels,
  availableTags,
  filteredCount: computed(() => filteredImages.value.length),
  totalCount: computed(() => images.value.length),
  // Selection Exports
  isSelectionMode,
  selectedCount: computed(() => selectedImages.value.length),
  toggleSelectionMode,
  selectAll,
  deselectAll,
  deleteSelected
})

watch([selectedTags, minRating], fetchImages, { deep: true })

async function fetchImages() {
  const fetchId = ++currentFetchId
  isLoading.value = true
  error.value = null
  try {
    let url = '/v1/history/images?limit=200'
    
    // Add multiple tag parameters
    if (selectedTags.value.length > 0) {
      for (const tag of selectedTags.value) {
        url += `&tag=${encodeURIComponent(tag)}`
      }
    }
    
    if (minRating.value > 0) {
      url += `&min_rating=${minRating.value}`
    }
    const response = await fetch(url)
    if (!response.ok) {
      throw new Error('Failed to fetch image history from the server.')
    }
    const data = await response.json()
    
    // Check for race condition
    if (fetchId !== currentFetchId) return

    images.value = data

    // Fetch all available tags for the filter
    const tagsRes = await fetch('/v1/history/tags')
    if (tagsRes.ok) {
      // Tags update is less critical for race conditions but good to be consistent
      if (fetchId === currentFetchId) {
          allTags.value = await tagsRes.json()
      }
    }

    // Wait for the DOM to update with the new images
    await nextTick()
    
    // Initialize modal and carousel now that the elements are in the DOM
    if (modalElement.value && carouselElement.value) {
      modalInstance = new Modal(modalElement.value)
      carouselInstance = new Carousel(carouselElement.value, {
        interval: false, // Do not auto-cycle
        touch: true,
      })

      // Track active index
      carouselElement.value.addEventListener('slid.bs.carousel', (event: any) => {
        activeIndex.value = event.to
      })
    }

  } catch (e: any) {
    if (fetchId === currentFetchId) {
        error.value = e.message
    }
  } finally {
    if (fetchId === currentFetchId) {
        isLoading.value = false
    }
  }
}

function openModal(index: number) {
  if (isSelectionMode.value) return // Disable modal in selection mode
  
  if (carouselInstance && modalInstance) {
    activeIndex.value = index
    carouselInstance.to(index)
    modalInstance.show()
  }
}

function reuseParameters(navigate = true) {
  const item = filteredImages.value[activeIndex.value]
  if (item && item.params) {
    const p = item.params
    if (p.prompt) store.prompt = p.prompt
    if (p.negative_prompt) store.negativePrompt = p.negative_prompt
    if (p.sample_steps) store.steps = p.sample_steps
    if (p.seed !== undefined) store.seed = p.seed
    if (p.cfg_scale !== undefined) store.cfgScale = p.cfg_scale
    if (p.sampling_method) {
      // Try to find matching sampler in our list
      const sm = p.sampling_method.toLowerCase().replace('_a', ' a')
      if (store.samplers.includes(sm)) {
        store.sampler = sm
      }
    }
    if (p.width) store.width = p.width
    if (p.height) store.height = p.height
    
    if (navigate) {
      // Close modal and navigate
      modalInstance?.hide()
      
      if (p.is_img2img || p.init_image) {
        router.push('/img2img')
      } else {
        router.push('/')
      }
    }
  }
}

function getFormattedParams(item: HistoryItem) {
  if (!item || !item.params) return ''
  const p = item.params
  let s = `${p.prompt}\n`
  if (p.negative_prompt) {
    s += `Negative prompt: ${p.negative_prompt}\n`
  }
  s += `Steps: ${p.sample_steps}, `
  s += `Sampler: ${p.sampling_method}, `
  s += `CFG scale: ${p.cfg_scale}, `
  s += `Seed: ${p.seed}, `
  s += `Size: ${p.width}x${p.height}, `
  if (p.model) s += `Model: ${p.model}, `
  if (p.Time) s += `Time: ${p.Time}, `
  s += `Version: stable-diffusion.cpp`
  return s
}

function copyToClipboard(text: string) {
  navigator.clipboard.writeText(text)
}

async function sendToImg2Img() {
  const item = filteredImages.value[activeIndex.value]
  if (!item) return
  
  const imageUrl = '/outputs/' + item.name
  
  try {
    const response = await fetch(imageUrl)
    const blob = await response.blob()
    const reader = new FileReader()
    reader.onloadend = () => {
      store.initImage = reader.result as string
      
      // Load dimensions
      const img = new Image()
      img.onload = () => {
         // Optionally we could auto-set width/height here too
      }
      img.src = store.initImage

      reuseParameters(false) // Reuse parameters but don't navigate yet
      modalInstance?.hide()
      router.push('/img2img')
    }
    reader.readAsDataURL(blob)
  } catch (err) {
    console.error('Failed to fetch image for img2img:', err)
  }
}

async function upscaleActiveImage() {
  const item = filteredImages.value[activeIndex.value]
  if (!item) return

  try {
    await store.upscaleImage('', item.name)
    // Refresh history to show the new upscaled image
    await fetchImages()
    // Find the new image and select it (it should be at the top)
    activeIndex.value = 0
    carouselInstance?.to(0)
  } catch (err) {
    console.error('Upscaling failed:', err)
  }
}

onMounted(() => {
  fetchImages()
})
</script>

<template>
  <div>
    <!-- Loading/Error/Empty State -->
    <div v-if="isLoading" class="text-center my-5">
      <div class="spinner-border text-primary" role="status">
        <span class="visually-hidden">Loading...</span>
      </div>
    </div>
    <div v-else-if="error" class="alert alert-danger">
      {{ error }}
    </div>
    <div v-else-if="images.length === 0" class="text-center text-muted my-5">
      🖼️
      <p class="mt-3">No images found in history.</p>
      <p>Generate some images with the "Save Images Automatically" setting enabled.</p>
    </div>

    <template v-else>
      <!-- Image Grid (Custom CSS Grid) -->
      <div v-if="filteredImages.length > 0" class="custom-gallery-grid" :style="{ '--cols': columnsPerRow }">
        <div v-for="(image, index) in filteredImages" :key="image.name" class="gallery-item">
          <div 
            class="card card-clickable shadow-sm h-100 border-0 bg-dark bg-opacity-10 position-relative overflow-hidden"
            :class="{ 'border border-primary border-2 bg-primary bg-opacity-10': selectedImages.includes(image.id) }"
            @click="isSelectionMode ? toggleSelection(image.id) : openModal(index)"
          >
            <!-- Checkbox Overlay for Selection Mode -->
            <div v-if="isSelectionMode" class="position-absolute top-0 end-0 p-2" style="z-index: 5;">
               <div class="form-check">
                  <input class="form-check-input shadow-none" type="checkbox" :checked="selectedImages.includes(image.id)" style="transform: scale(1.3);">
               </div>
            </div>

            <img :src="'/outputs/' + image.name" class="card-img-top" :alt="image.name" loading="lazy" />
            
            <div class="card-footer p-2 x-small border-0 bg-transparent">
              <div class="d-flex justify-content-between text-muted mb-1">
                <span class="text-truncate me-1" :title="image.name">{{ formatDate(image.name) }}</span>
                <span v-if="image.rating && image.rating > 0" class="text-warning small" title="Rating">★ {{ image.rating }}</span>
              </div>
              <div v-if="image.params?.model" class="d-flex justify-content-between text-secondary opacity-75 mt-1">
                <span class="text-truncate" :title="image.params.model">📦 {{ image.params.model }}</span>
                <span v-if="image.params.Time" class="text-nowrap ml-1">⏱️ {{ image.params.Time }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
      
      <!-- No Results -->
      <div v-else class="text-center my-5 text-muted">
        🔍
        <p class="mt-2">No images match your current filters.</p>
        <button class="btn btn-sm btn-link" @click="selectedModel = 'all'; selectedDateRange = 'all'; minRating = 0; startDate = ''; endDate = '';">Reset Filters</button>
      </div>
    </template>

    <!-- Modal -->
    <Teleport to="body">
      <div class="modal fade" ref="modalElement" tabindex="-1" aria-labelledby="imageModalLabel" aria-hidden="true">
        <div class="modal-dialog modal-xl modal-dialog-centered" style="max-width: 95vw;">
          <div class="modal-content shadow-lg overflow-hidden border-0" style="height: 90vh;">
            
            <div class="d-flex h-100">
              <!-- Left Column: Image Carousel -->
              <div class="flex-grow-1 bg-black position-relative overflow-hidden h-100 d-flex align-items-center justify-content-center">
                  <div ref="carouselElement" id="imageHistoryCarousel" class="carousel slide w-100 h-100">
                    <div class="carousel-inner h-100">
                      <div v-for="(image, index) in filteredImages" :key="`carousel-${image.name}`" class="carousel-item h-100" :class="{ active: index === activeIndex }">
                        <div class="d-flex align-items-center justify-content-center h-100">
                          <img :src="'/outputs/' + image.name" class="d-block" :alt="image.name" style="max-width: 100%; max-height: 100%; object-fit: contain;">
                        </div>
                      </div>
                    </div>
                    <button class="carousel-control-prev" type="button" data-bs-target="#imageHistoryCarousel" data-bs-slide="prev">
                      <span class="carousel-control-prev-icon" aria-hidden="true"></span>
                      <span class="visually-hidden">Previous</span>
                    </button>
                    <button class="carousel-control-next" type="button" data-bs-target="#imageHistoryCarousel" data-bs-slide="next">
                      <span class="carousel-control-next-icon" aria-hidden="true"></span>
                      <span class="visually-hidden">Next</span>
                    </button>
                  </div>
              </div>

              <!-- Right Column: Info & Actions -->
              <div class="d-flex flex-column border-start bg-body" style="width: 380px; min-width: 380px;">
                <!-- Header -->
                <div class="p-3 border-bottom d-flex justify-content-between align-items-start">
                  <div>
                    <div class="d-flex align-items-center mb-1">
                        <!-- Rating Input Component -->
                        <RatingInput 
                          v-if="filteredImages[activeIndex]"
                          :model-value="filteredImages[activeIndex].rating || 0" 
                          @update:model-value="(val) => setRating(filteredImages[activeIndex].id, val)"
                          class="me-2"
                        />
                        <h6 class="mb-0 text-break fw-bold text-truncate" style="max-width: 200px;" :title="filteredImages[activeIndex]?.name">
                          {{ filteredImages[activeIndex]?.name || 'Image Viewer' }}
                        </h6>
                    </div>
                    <small v-if="filteredImages[activeIndex]?.params?.model" class="text-muted small">
                      {{ filteredImages[activeIndex].params.model }}
                    </small>
                  </div>
                  <button type="button" class="btn-close ms-2" data-bs-dismiss="modal" aria-label="Close"></button>
                </div>

                <!-- Actions -->
                <div class="p-3 border-bottom d-flex flex-wrap gap-2 justify-content-center bg-body-tertiary">
                    <button 
                      v-if="store.upscaleModel"
                      class="btn btn-outline-info btn-sm flex-grow-1"
                      @click="upscaleActiveImage"
                      :disabled="store.isUpscaling"
                    >
                      <i v-if="store.isUpscaling" class="spinner-border spinner-border-sm me-1"></i>
                      <span v-else>⬆️</span>
                      Upscale
                    </button>
                    <button 
                      class="btn btn-outline-success btn-sm flex-grow-1"
                      @click="sendToImg2Img"
                    >
                      🖼️ Img2Img
                    </button>
                    <button 
                      v-if="filteredImages[activeIndex]?.params" 
                      class="btn btn-outline-primary btn-sm flex-grow-1"
                      @click="reuseParameters(true)"
                    >
                      ♻️ Reuse
                    </button>
                    <button 
                      v-if="filteredImages[activeIndex]"
                      class="btn btn-outline-danger btn-sm flex-grow-1"
                      @click="deleteImage(filteredImages[activeIndex].id)"
                    >
                      🗑️ Delete
                    </button>
                </div>

                <!-- Metadata Scrollable Area -->
                <div class="flex-grow-1 overflow-y-auto p-3 custom-scrollbar">
                   <div v-if="filteredImages[activeIndex]" class="small text-muted">
                      
                      <!-- Basic Info -->
                      <div v-if="filteredImages[activeIndex].params" class="mb-3">
                        <div class="mb-2">
                          <span class="fw-bold text-uppercase x-small text-secondary">Prompt</span>
                          <div class="p-2 bg-body-tertiary rounded border mt-1" style="max-height: 150px; overflow-y: auto;">
                            {{ filteredImages[activeIndex].params.prompt }}
                          </div>
                        </div>
                        
                        <div v-if="filteredImages[activeIndex].params.negative_prompt" class="mb-2">
                          <span class="fw-bold text-uppercase x-small text-secondary">Negative Prompt</span>
                          <div class="p-2 bg-body-tertiary rounded border mt-1 small text-muted">
                            {{ filteredImages[activeIndex].params.negative_prompt }}
                          </div>
                        </div>

                        <div class="d-grid gap-2" style="grid-template-columns: 1fr 1fr;">
                          <div><span class="fw-bold">Seed:</span> {{ filteredImages[activeIndex].params.seed }}</div>
                          <div><span class="fw-bold">Steps:</span> {{ filteredImages[activeIndex].params.sample_steps }}</div>
                          <div><span class="fw-bold">CFG:</span> {{ filteredImages[activeIndex].params.cfg_scale }}</div>
                          <div><span class="fw-bold">Size:</span> {{ filteredImages[activeIndex].params.width }}x{{ filteredImages[activeIndex].params.height }}</div>
                          <div v-if="filteredImages[activeIndex].params.Time" class="grid-column-span-2">
                            <span class="fw-bold">Time:</span> {{ filteredImages[activeIndex].params.Time }}
                          </div>
                        </div>
                      </div>

                      <!-- Tags Section -->
                      <div class="mb-3 pt-3 border-top">
                        <div class="d-flex justify-content-between align-items-center mb-2">
                          <span class="fw-bold text-uppercase x-small text-secondary">Tags</span>
                        </div>
                        <div class="input-group input-group-sm mb-2">
                            <input type="text" v-model="newTagInput" class="form-control" placeholder="Add tag..." @keyup.enter="addTag(filteredImages[activeIndex].id)">
                            <button class="btn btn-outline-secondary" type="button" @click="addTag(filteredImages[activeIndex].id)">+</button>
                        </div>
                        <div class="d-flex flex-wrap gap-1">
                          <span v-for="tag in filteredImages[activeIndex].tags" :key="tag" 
                            class="badge bg-primary bg-opacity-10 text-primary border border-primary border-opacity-25 fw-normal d-flex align-items-center gap-1"
                          >
                            <span 
                                @click="emit('tag-click', tag); modalInstance?.hide()" 
                                style="cursor: pointer;"
                                :title="selectedTags.includes(tag) ? 'Remove from filter' : 'Filter by this tag'"
                            >
                                {{ tag }}
                                <span v-if="selectedTags.includes(tag)" class="fw-bold ms-1 text-primary">✓</span>
                            </span>
                            <span @click.stop="removeTag(filteredImages[activeIndex].id, tag)" class="text-danger ms-1" style="cursor: pointer; font-size: 0.8rem;">&times;</span>
                          </span>
                          <span v-if="!filteredImages[activeIndex].tags?.length" class="text-muted italic small">No tags yet.</span>
                        </div>
                      </div>

                      <!-- Forge Format -->
                      <div v-if="filteredImages[activeIndex].params" class="pt-3 border-top">
                        <div class="d-flex justify-content-between align-items-center mb-2">
                          <span class="fw-bold text-uppercase x-small text-secondary">Generation Parameters</span>
                          <button class="btn btn-link btn-sm p-0 text-decoration-none x-small" @click="copyToClipboard(getFormattedParams(filteredImages[activeIndex]))">
                            📋 Copy
                          </button>
                        </div>
                        <pre class="bg-dark bg-opacity-10 p-2 rounded x-small mb-0 text-break-all white-space-pre-wrap" style="max-height: 200px; overflow-y: auto;">{{ getFormattedParams(filteredImages[activeIndex]) }}</pre>
                      </div>
                   </div>
                </div>
              </div>
            </div>

          </div>
        </div>
      </div>
    </Teleport>

    <DeleteConfirmationModal 
      v-if="showDeleteModal" 
      :image-url="imagesToDelete.length === 1 ? '/outputs/' + imagesToDelete[0].name : undefined"
      :count="imagesToDelete.length"
      @confirm="performDelete"
      @cancel="showDeleteModal = false"
    />

  </div>
</template>

<style scoped>
.custom-gallery-grid {
  display: grid;
  grid-template-columns: repeat(var(--cols, 4), 1fr);
  gap: 1rem;
}

.gallery-item {
  min-width: 0; /* Prevent grid breakout */
}

.card-img-top {
  aspect-ratio: 1 / 1;
  object-fit: cover;
}

.x-small {
  font-size: 0.65rem;
}
.text-break-all {
  word-break: break-all;
}
.white-space-pre-wrap {
  white-space: pre-wrap;
}
.card-clickable {
  cursor: pointer;
  transition: transform 0.2s ease-in-out;
}
.card-clickable:hover {
  transform: scale(1.03);
}
.modal-body img {
  max-height: 100%;
  max-width: 100%;
  width: auto;
  height: auto;
  object-fit: contain;
}

/* Enhanced Carousel Controls */
.carousel-control-prev,
.carousel-control-next {
  width: 10%;
  opacity: 0.7;
  z-index: 5;
}

.carousel-control-prev-icon,
.carousel-control-next-icon {
  background-color: rgba(0, 0, 0, 0.5);
  background-size: 60%;
  border-radius: 50%;
  width: 3rem;
  height: 3rem;
  transition: all 0.2s ease;
}

.carousel-control-prev:hover .carousel-control-prev-icon,
.carousel-control-next:hover .carousel-control-next-icon {
  background-color: rgba(0, 0, 0, 0.8);
  transform: scale(1.1);
}
</style>