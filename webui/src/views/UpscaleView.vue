<script setup lang="ts">
import { ref, computed } from 'vue'
import { useGenerationStore } from '@/stores/generation'
import ImageDisplay from '../components/ImageDisplay.vue'

const store = useGenerationStore()
const fileInput = ref<HTMLInputElement | null>(null)
const selectedImage = ref<string | null>(null)
const uploadedImageWidth = ref(0)
const uploadedImageHeight = ref(0)

const aspectRatio = computed(() => {
  const w = uploadedImageWidth.value
  const h = uploadedImageHeight.value
  if (!w || !h) return ''
  const gcd = (a: number, b: number): number => b ? gcd(b, a % b) : a
  const common = gcd(w, h)
  return `${w / common}:${h / common}`
})

const esrganModels = computed(() => {
  return store.models.filter(m => m.type === 'esrgan')
})

const onFileChange = (e: Event) => {
  const target = e.target as HTMLInputElement
  if (target.files && target.files[0]) {
    const reader = new FileReader()
    reader.onload = (event) => {
      const dataUrl = event.target?.result as string
      selectedImage.value = dataUrl
      
      const img = new Image()
      img.onload = () => {
        uploadedImageWidth.value = img.width
        uploadedImageHeight.value = img.height
      }
      img.src = dataUrl
    }
    reader.readAsDataURL(target.files[0])
  }
}

const handleUpscale = async () => {
  if (!selectedImage.value || !store.upscaleModel) return
  try {
    // upscaleImage action in store already handles loading model if needed
    // but the store action currently expects upscaleModel to be set
    await store.upscaleImage(selectedImage.value)
  } catch (e) {
    console.error('Upscale failed:', e)
  }
}

const clearImage = () => {
  selectedImage.value = null
  uploadedImageWidth.value = 0
  uploadedImageHeight.value = 0
  store.imageUrls = []
}
</script>

<template>
  <div class="d-flex flex-column flex-lg-row gap-2 h-100">
    <section class="col-lg-5 d-flex flex-column gap-2" style="min-width: 0;">
      <div class="island p-3 h-100 overflow-y-auto">
        <h5 class="mb-3">Image Upscaling (ESRGAN)</h5>
        
        <div class="mb-4">
          <label class="form-label fw-bold small text-uppercase text-muted">1. Select Image</label>
          <div v-if="!selectedImage" class="upscale-upload-zone border rounded p-5 text-center cursor-pointer" @click="fileInput?.click()">
            <div class="display-4 mb-2"><i class="bi bi-image"></i></div>
            <p class="mb-0">Click to upload or drag & drop</p>
            <input type="file" ref="fileInput" class="d-none" accept="image/*" @change="onFileChange" />
          </div>
          <div v-else class="position-relative border rounded p-2 text-center bg-dark">
            <img :src="selectedImage" class="img-fluid rounded" style="max-height: 300px;" />
            <div class="mt-2 text-white-50 small">
              {{ uploadedImageWidth }}x{{ uploadedImageHeight }} ({{ aspectRatio }})
            </div>
            <button class="btn btn-danger btn-sm position-absolute top-0 end-0 m-2" @click="clearImage"><i class="bi bi-x-lg"></i></button>
          </div>
        </div>

        <div class="mb-3">
          <label for="upscaleModel" class="form-label fw-bold small text-uppercase text-muted">2. Select Model</label>
          <select id="upscaleModel" v-model="store.upscaleModel" class="form-select">
            <option value="" disabled>Choose an ESRGAN model...</option>
            <option v-for="model in esrganModels" :key="model.id" :value="model.id">
              {{ model.name }}
            </option>
          </select>
          <div v-if="esrganModels.length === 0" class="form-text text-danger mt-1">
            No ESRGAN models found in models/esrgan directory.
          </div>
        </div>

        <div class="mb-4">
          <label for="upscaleFactor" class="form-label d-flex justify-content-between fw-bold small text-uppercase text-muted">
            <span>3. Upscale Factor</span>
            <span class="badge bg-primary">{{ store.upscaleFactor || 'Auto' }}</span>
          </label>
          <select id="upscaleFactor" v-model.number="store.upscaleFactor" class="form-select">
            <option :value="0">Auto (Model Default)</option>
            <option :value="2">2x</option>
            <option :value="4">4x</option>
          </select>
          <div class="form-text small">Note: Most models are fixed at 4x.</div>
        </div>

        <div class="d-grid">
          <button 
            class="btn btn-primary btn-lg" 
            :disabled="!selectedImage || !store.upscaleModel || store.isUpscaling"
            @click="handleUpscale"
          >
            <span v-if="store.isUpscaling" class="spinner-border spinner-border-sm me-2" role="status"></span>
            {{ store.isUpscaling ? 'Upscaling...' : 'Upscale Image' }}
          </button>
        </div>
      </div>
      
      <div class="alert alert-secondary py-2 small mb-0">
        <strong>Tip:</strong> ESRGAN upscaling is great for sharpening photos and textures without changing the content. It is much faster than Highres-fix but doesn't "hallucinate" new details.
      </div>
    </section>

    <section class="flex-fill d-flex flex-column" style="min-width: 0;">
      <div class="island h-100 p-3 d-flex flex-column">
        <h5 class="mb-3">Result</h5>
        <div class="flex-grow-1 d-flex align-items-center justify-content-center border rounded bg-light overflow-hidden position-relative" style="min-height: 400px;">
           <ImageDisplay v-if="store.imageUrls.length > 0" />
           <div v-else class="text-muted text-center p-5">
              <div class="display-1 opacity-25 mb-3"><i class="bi bi-magic"></i></div>
              <p>Upscaled image will appear here</p>
           </div>
           
           <div v-if="store.isUpscaling" class="position-absolute top-0 start-0 w-100 h-100 d-flex flex-column align-items-center justify-content-center bg-white bg-opacity-75" style="z-index: 10;">
              <div class="spinner-grow text-primary mb-3" role="status" style="width: 3rem; height: 3rem;"></div>
              <h5 class="text-primary">Enhancing Image...</h5>
           </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.upscale-upload-zone {
  background-color: #f8f9fa;
  cursor: pointer;
  transition: all 0.2s ease;
}

[data-bs-theme="dark"] .upscale-upload-zone {
  background-color: #2b3035;
}

.upscale-upload-zone:hover {
  background-color: #e9ecef;
  border-color: #0d6efd !important;
}

[data-bs-theme="dark"] .upscale-upload-zone:hover {
  background-color: #373b3e;
}

.cursor-pointer {
  cursor: pointer;
}
</style>