<script setup lang="ts">
import { ref, onMounted, watch, onUnmounted, nextTick, computed } from 'vue'
import { useGenerationStore } from '@/stores/generation'

const store = useGenerationStore()

const aspectRatio = computed(() => {
  const w = store.width
  const h = store.height
  if (!w || !h) return ''
  const gcd = (a: number, b: number): number => b ? gcd(b, a % b) : a
  const common = gcd(w, h)
  return `${w / common}:${h / common}`
})

const canvasRef = ref<HTMLCanvasElement | null>(null)
const maskCanvasRef = ref<HTMLCanvasElement | null>(null)
const containerRef = ref<HTMLDivElement | null>(null)

const isDrawing = ref(false)
const brushSize = ref(40)
const maskBlur = ref(10)
const maskOpacity = ref(0.6)
const isMaskInverted = ref(false)
const uploadedImage = ref<HTMLImageElement | null>(null)

const uploadedImageWidth = ref(0)
const uploadedImageHeight = ref(0)

const ctx = ref<CanvasRenderingContext2D | null>(null)
const maskCtx = ref<CanvasRenderingContext2D | null>(null)

const initCanvases = async () => {
  await nextTick()
  if (!canvasRef.value || !maskCanvasRef.value) return
  
  canvasRef.value.width = store.width
  canvasRef.value.height = store.height
  maskCanvasRef.value.width = store.width
  maskCanvasRef.value.height = store.height

  ctx.value = canvasRef.value.getContext('2d')
  maskCtx.value = maskCanvasRef.value.getContext('2d', { willReadFrequently: true })
  
  if (maskCtx.value) {
    maskCtx.value.fillStyle = 'black'
    maskCtx.value.fillRect(0, 0, maskCanvasRef.value.width, maskCanvasRef.value.height)
  }
}

// Helper to get processed mask (inverted and/or blurred)
const getProcessedMaskDataUrl = () => {
  if (!maskCanvasRef.value) return null
  
  const tempCanvas = document.createElement('canvas')
  tempCanvas.width = maskCanvasRef.value.width
  tempCanvas.height = maskCanvasRef.value.height
  const tCtx = tempCanvas.getContext('2d')
  if (!tCtx) return null
  
  // 1. Draw current mask
  if (maskBlur.value > 0) {
      tCtx.filter = `blur(${maskBlur.value}px)`
  }
  tCtx.drawImage(maskCanvasRef.value, 0, 0)
  
  // 2. Invert if needed
  if (isMaskInverted.value) {
      const invCanvas = document.createElement('canvas')
      invCanvas.width = tempCanvas.width
      invCanvas.height = tempCanvas.height
      const iCtx = invCanvas.getContext('2d')
      if (iCtx) {
          iCtx.fillStyle = 'white'
          iCtx.fillRect(0, 0, invCanvas.width, invCanvas.height)
          iCtx.globalCompositeOperation = 'difference'
          iCtx.drawImage(tempCanvas, 0, 0)
          return invCanvas.toDataURL('image/png')
      }
  }
  
  return tempCanvas.toDataURL('image/png')
}

const redraw = () => {
  if (!ctx.value || !canvasRef.value || !uploadedImage.value) return
  
  const canvas = canvasRef.value
  const image = uploadedImage.value
  
  // Clear canvas
  ctx.value.clearRect(0, 0, canvas.width, canvas.height)
  
  // Draw image
  ctx.value.drawImage(image, 0, 0, canvas.width, canvas.height)
  
  // Overlay mask for visualization
  if (maskCanvasRef.value) {
    ctx.value.globalAlpha = maskOpacity.value
    
    const tempCanvas = document.createElement('canvas')
    tempCanvas.width = maskCanvasRef.value.width
    tempCanvas.height = maskCanvasRef.value.height
    const tCtx = tempCanvas.getContext('2d')
    if (tCtx) {
        if (maskBlur.value > 0) {
            tCtx.filter = `blur(${maskBlur.value}px)`
        }
        tCtx.drawImage(maskCanvasRef.value, 0, 0)
        
        if (isMaskInverted.value) {
            const invCanvas = document.createElement('canvas')
            invCanvas.width = tempCanvas.width
            invCanvas.height = tempCanvas.height
            const iCtx = invCanvas.getContext('2d')
            if (iCtx) {
                iCtx.fillStyle = 'white'
                iCtx.fillRect(0, 0, invCanvas.width, invCanvas.height)
                iCtx.globalCompositeOperation = 'difference'
                iCtx.drawImage(tempCanvas, 0, 0)
                ctx.value.drawImage(invCanvas, 0, 0, canvas.width, canvas.height)
            }
        } else {
            ctx.value.drawImage(tempCanvas, 0, 0, canvas.width, canvas.height)
        }
    }
    
    ctx.value.globalAlpha = 1.0
  }
}

const onFileChange = (e: Event) => {
  const target = e.target as HTMLInputElement
  if (target.files && target.files[0]) {
    const reader = new FileReader()
    reader.onload = async (event) => {
      const dataUrl = event.target?.result as string
      store.initImage = dataUrl
      
      const img = new Image()
      img.onload = async () => {
        uploadedImage.value = img
        uploadedImageWidth.value = img.width
        uploadedImageHeight.value = img.height
        
        await initCanvases()
        redraw()
        updateStoreMask()
      }
      img.src = dataUrl
    }
    reader.readAsDataURL(target.files[0])
  }
}

const getMousePos = (e: MouseEvent | TouchEvent) => {
  if (!canvasRef.value) return { x: 0, y: 0 }
  const rect = canvasRef.value.getBoundingClientRect()
  const clientX = 'touches' in e ? (e as TouchEvent).touches[0].clientX : (e as MouseEvent).clientX
  const clientY = 'touches' in e ? (e as TouchEvent).touches[0].clientY : (e as MouseEvent).clientY
  
  return {
    x: (clientX - rect.left) * (canvasRef.value.width / rect.width),
    y: (clientY - rect.top) * (canvasRef.value.height / rect.height)
  }
}

const startDrawing = (e: MouseEvent | TouchEvent) => {
  isDrawing.value = true
  if (maskCtx.value) {
    const pos = getMousePos(e)
    maskCtx.value.beginPath()
    maskCtx.value.moveTo(pos.x, pos.y)
  }
  draw(e)
}

const stopDrawing = () => {
  if (!isDrawing.value) return
  isDrawing.value = false
  updateStoreMask()
}

const draw = (e: MouseEvent | TouchEvent) => {
  if (!isDrawing.value || !maskCtx.value || !canvasRef.value) return
  
  const pos = getMousePos(e)
  
  maskCtx.value.lineWidth = brushSize.value
  maskCtx.value.lineCap = 'round'
  maskCtx.value.lineJoin = 'round'
  maskCtx.value.strokeStyle = 'white'
  
  maskCtx.value.lineTo(pos.x, pos.y)
  maskCtx.value.stroke()
  
  redraw()
}

const updateStoreMask = () => {
  store.maskImage = getProcessedMaskDataUrl()
}

const clearMask = () => {
  if (maskCtx.value && maskCanvasRef.value) {
    maskCtx.value.fillStyle = 'black'
    maskCtx.value.fillRect(0, 0, maskCanvasRef.value.width, maskCanvasRef.value.height)
    redraw()
    updateStoreMask()
  }
}

const toggleInvert = () => {
    isMaskInverted.value = !isMaskInverted.value
    redraw()
    updateStoreMask()
}

const clearAll = () => {
  store.initImage = null
  store.maskImage = null
  uploadedImage.value = null
  uploadedImageWidth.value = 0
  uploadedImageHeight.value = 0
  if (ctx.value && canvasRef.value) {
    ctx.value.clearRect(0, 0, canvasRef.value.width, canvasRef.value.height)
  }
  clearMask()
}

const useImageSize = async () => {
  if (uploadedImageWidth.value > 0 && uploadedImageHeight.value > 0) {
    store.width = Math.round(uploadedImageWidth.value / 64) * 64
    store.height = Math.round(uploadedImageHeight.value / 64) * 64
    if (store.width < 64) store.width = 64
    if (store.height < 64) store.height = 64
    
    await initCanvases()
    redraw()
    updateStoreMask()
  }
}

onMounted(async () => {
  if (store.initImage) {
      const img = new Image()
      img.onload = async () => {
          uploadedImage.value = img
          uploadedImageWidth.value = img.width
          uploadedImageHeight.value = img.height
          await initCanvases()
          redraw()
          if (!store.maskImage) {
              updateStoreMask()
          }
      }
      img.src = store.initImage
  }
})

watch([() => store.width, () => store.height], async (newVals, oldVals) => {
    if (newVals[0] !== oldVals[0] || newVals[1] !== oldVals[1]) {
        if (uploadedImage.value) {
            await initCanvases()
            redraw()
            updateStoreMask()
        }
    }
})

watch(maskBlur, () => {
    redraw()
    updateStoreMask()
})

</script>

<template>
  <div class="inpainting-canvas-container card shadow-sm p-3">
    <div class="d-flex justify-content-between align-items-center mb-3">
      <h5 class="mb-0">Inpainting Canvas</h5>
      <div v-if="store.initImage" class="d-flex gap-3 align-items-center">
        <div class="d-flex align-items-center gap-2">
            <label class="small text-muted text-nowrap">Brush:</label>
            <input type="range" v-model.number="brushSize" min="5" max="150" class="form-range" style="width: 70px;">
            <span class="badge bg-secondary" style="min-width: 30px;">{{ brushSize }}</span>
        </div>

        <div class="d-flex align-items-center gap-2">
            <label class="small text-muted text-nowrap" title="Softness of the mask edges">Softness:</label>
            <input type="range" v-model.number="maskBlur" min="0" max="64" class="form-range" style="width: 70px;">
            <span class="badge bg-secondary" style="min-width: 30px;">{{ maskBlur }}</span>
        </div>
        
        <div class="form-check form-switch mb-0">
          <input class="form-check-input" type="checkbox" id="invertMask" :checked="isMaskInverted" @change="toggleInvert">
          <label class="form-check-label small text-nowrap" for="invertMask">Invert</label>
        </div>

        <button class="btn btn-outline-warning btn-sm" @click="clearMask">Clear</button>
        <button class="btn btn-outline-danger btn-sm" @click="clearAll">Reset</button>
      </div>
    </div>

    <div v-if="!store.initImage" class="upload-zone border rounded p-5 text-center cursor-pointer" @click="($refs.fileInput as any).click()">
      <div class="display-1 mb-3 text-muted"><i class="bi bi-palette"></i></div>
      <h4>Upload an image to start inpainting</h4>
      <p class="text-muted">You will be able to paint over the areas you want to regenerate.</p>
      <input type="file" ref="fileInput" class="d-none" accept="image/*" @change="onFileChange" />
    </div>

    <div v-else class="canvas-wrapper position-relative text-center border rounded bg-dark overflow-hidden" ref="containerRef">
      <canvas 
        ref="canvasRef" 
        class="main-canvas"
        @mousedown="startDrawing"
        @mousemove="draw"
        @mouseup="stopDrawing"
        @mouseleave="stopDrawing"
        @touchstart.prevent="startDrawing"
        @touchmove.prevent="draw"
        @touchend.prevent="stopDrawing"
      ></canvas>
      <canvas ref="maskCanvasRef" style="display: none;"></canvas>
      
      <div class="canvas-info p-2 bg-dark text-white-50 small d-flex justify-content-between">
          <span>{{ uploadedImageWidth }}x{{ uploadedImageHeight }}</span>
          <span v-if="isMaskInverted" class="badge bg-info">Inverted</span>
          <span>Target: {{ store.width }}x{{ store.height }} ({{ aspectRatio }})</span>
          <a href="#" class="text-info" @click.prevent="useImageSize">Match Size</a>
      </div>
    </div>
    
    <div class="mt-3 alert alert-info py-2 small mb-0">
        <span v-if="!isMaskInverted">Painting <strong>REPLACED</strong> area.</span>
        <span v-else>Painting <strong>KEPT</strong> area.</span>
        Increase <strong>Softness</strong> for smoother blending.
    </div>
  </div>
</template>

<style scoped>
.inpainting-canvas-container {
  min-height: 400px;
}

.upload-zone {
  cursor: pointer;
  background-color: #f8f9fa;
  transition: background-color 0.2s;
}

[data-bs-theme="dark"] .upload-zone {
  background-color: #2b3035;
}

.upload-zone:hover {
  background-color: #e9ecef;
}

[data-bs-theme="dark"] .upload-zone:hover {
  background-color: #373b3e;
}

.canvas-wrapper {
  background-color: #1a1a1a;
  box-shadow: inset 0 0 10px rgba(0,0,0,0.5);
}

.main-canvas {
  max-width: 100%;
  max-height: 70vh;
  cursor: crosshair;
  display: block;
  margin: 0 auto;
  touch-action: none;
}

.cursor-pointer {
  cursor: pointer;
}
</style>