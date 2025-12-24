<script setup lang="ts">
import { ref, onMounted, nextTick, computed } from 'vue'
import { Modal, Carousel } from 'bootstrap'
import { useGenerationStore } from '@/stores/generation'
import { useRouter } from 'vue-router'

interface HistoryItem {
  name: string
  params?: any
}

const store = useGenerationStore()
const router = useRouter()

const images = ref<HistoryItem[]>([])
const isLoading = ref(true)
const error = ref<string | null>(null)

// Filtering State
const selectedModel = ref('all')
const selectedDateRange = ref('all') // all, today, yesterday, week, month, custom
const startDate = ref('')
const endDate = ref('')

const availableModels = computed(() => {
  const models = new Set<string>()
  images.value.forEach(img => {
    if (img.params?.model) models.add(img.params.model)
  })
  return Array.from(models).sort()
})

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

// Expose for parent component
defineExpose({ 
  columnsPerRow, 
  setColumns, 
  selectedModel, 
  selectedDateRange, 
  startDate, 
  endDate, 
  availableModels,
  filteredCount: computed(() => filteredImages.value.length),
  totalCount: computed(() => images.value.length)
})

async function fetchImages() {
  isLoading.value = true
  error.value = null
  try {
    const response = await fetch('/v1/history/images')
    if (!response.ok) {
      throw new Error('Failed to fetch image history from the server.')
    }
    const data = await response.json()
    images.value = data

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
    error.value = e.message
  } finally {
    isLoading.value = false
  }
}

function openModal(index: number) {
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
          <div class="card card-clickable shadow-sm h-100 border-0 bg-dark bg-opacity-10" @click="openModal(index)">
            <img :src="'/outputs/' + image.name" class="card-img-top" :alt="image.name" loading="lazy" />
            <div class="card-footer p-2 x-small border-0 bg-transparent">
              <div class="d-flex justify-content-between text-muted mb-1">
                <span class="text-truncate me-1" :title="image.name">{{ formatDate(image.name) }}</span>
                <span class="fw-bold text-primary">#{{ image.params?.seed || '?' }}</span>
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
        <button class="btn btn-sm btn-link" @click="selectedModel = 'all'; selectedDateRange = 'all'; startDate = ''; endDate = ''">Reset Filters</button>
      </div>
    </template>

    <!-- Modal -->
    <Teleport to="body">
      <div class="modal fade" ref="modalElement" tabindex="-1" aria-labelledby="imageModalLabel" aria-hidden="true">
        <div class="modal-dialog modal-xl modal-dialog-centered">
          <div class="modal-content shadow-lg">
            <div class="modal-header">
              <h5 class="modal-title" id="imageModalLabel">
                {{ filteredImages[activeIndex]?.name || 'Image Viewer' }}
                <small v-if="filteredImages[activeIndex]?.params?.model" class="text-muted ms-2 fs-6 fw-normal">
                  [{{ filteredImages[activeIndex].params.model }}]
                </small>
              </h5>
              <div class="ms-auto me-2 d-flex gap-2">
                <button 
                  v-if="store.upscaleModel"
                  class="btn btn-outline-info btn-sm"
                  @click="upscaleActiveImage"
                  :disabled="store.isUpscaling"
                >
                  <i v-if="store.isUpscaling" class="spinner-border spinner-border-sm me-1"></i>
                  <span v-else>⬆️</span>
                  Upscale
                </button>
                <button 
                  class="btn btn-outline-success btn-sm"
                  @click="sendToImg2Img"
                >
                  🖼️ Send to Img2Img
                </button>
                <button 
                  v-if="filteredImages[activeIndex]?.params" 
                  class="btn btn-outline-primary btn-sm"
                  @click="reuseParameters(true)"
                >
                  ♻️ Reuse Parameters
                </button>
              </div>
              <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
                        <div class="modal-body p-0 bg-black d-flex align-items-center justify-content-center" style="height: 80vh;">
                          <!-- Carousel -->
                          <div ref="carouselElement" id="imageHistoryCarousel" class="carousel slide w-100 h-100">
                            <div class="carousel-inner h-100">
                              <div v-for="(image, index) in filteredImages" :key="`carousel-${image.name}`" class="carousel-item h-100" :class="{ active: index === activeIndex }">
                                <div class="d-flex align-items-center justify-content-center h-100">
                                  <img :src="'/outputs/' + image.name" class="d-block mx-auto" :alt="image.name">
                                </div>
                              </div>
                            </div>                <button class="carousel-control-prev" type="button" data-bs-target="#imageHistoryCarousel" data-bs-slide="prev">
                  <span class="carousel-control-prev-icon" aria-hidden="true"></span>
                  <span class="visually-hidden">Previous</span>
                </button>
                <button class="carousel-control-next" type="button" data-bs-target="#imageHistoryCarousel" data-bs-slide="next">
                  <span class="carousel-control-next-icon" aria-hidden="true"></span>
                  <span class="visually-hidden">Next</span>
                </button>
              </div>
            </div>
            <div class="modal-footer justify-content-start" v-if="filteredImages[activeIndex]?.params">
               <div class="small w-100 text-muted overflow-auto" style="max-height: 150px;">
                  <div class="d-flex justify-content-between align-items-start">
                    <div>
                      <strong>Prompt:</strong> {{ filteredImages[activeIndex].params.prompt }}<br>
                      <strong>Seed:</strong> {{ filteredImages[activeIndex].params.seed }} |
                      <strong>Steps:</strong> {{ filteredImages[activeIndex].params.sample_steps }} |
                      <strong>CFG:</strong> {{ filteredImages[activeIndex].params.cfg_scale }} |
                      <strong>Sampler:</strong> {{ filteredImages[activeIndex].params.sampling_method }} |
                      <strong>Size:</strong> {{ filteredImages[activeIndex].params.width }}x{{ filteredImages[activeIndex].params.height }}
                      <span v-if="filteredImages[activeIndex].params.Time"> | <strong>Time:</strong> {{ filteredImages[activeIndex].params.Time }}</span>
                    </div>
                  </div>
                  <div class="mt-2 pt-2 border-top">
                    <div class="d-flex justify-content-between align-items-center mb-1">
                      <strong>Forge Format:</strong>
                      <button class="btn btn-link btn-sm p-0 text-decoration-none x-small" @click="copyToClipboard(getFormattedParams(filteredImages[activeIndex]))">
                        📋 Copy
                      </button>
                    </div>
                    <pre class="bg-dark bg-opacity-10 p-2 rounded x-small mb-0 text-break-all white-space-pre-wrap">{{ getFormattedParams(filteredImages[activeIndex]) }}</pre>
                  </div>
               </div>
            </div>
          </div>
        </div>
      </div>
    </Teleport>

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
