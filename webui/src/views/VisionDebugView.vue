<script setup lang="ts">
import { ref } from 'vue'
import { useGenerationStore } from '@/stores/generation'

const store = useGenerationStore()
const fileInput = ref<HTMLInputElement | null>(null)
const selectedImage = ref<string | null>(null)
const prompt = ref('Describe this image in detail.')
const response = ref('')
const isLoading = ref(false)
const error = ref('')

const onFileChange = (event: Event) => {
  const target = event.target as HTMLInputElement
  if (target.files && target.files[0]) {
    const file = target.files[0]
    const reader = new FileReader()
    reader.onload = (e) => {
      selectedImage.value = e.target?.result as string
    }
    reader.readAsDataURL(file)
  }
}

const analyzeImage = async () => {
  if (!selectedImage.value || !prompt.value) return
  
  isLoading.value = true
  response.value = ''
  error.value = ''
  
  try {
    const res = await fetch('/v1/chat/completions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        messages: [
          {
            role: 'user',
            content: [
              { type: 'text', text: prompt.value },
              { type: 'image_url', image_url: { url: selectedImage.value } }
            ]
          }
        ],
        max_tokens: 1024,
        temperature: 0.1
      })
    })
    
    if (!res.ok) {
      const err = await res.json()
      throw new Error(err.error || 'Request failed')
    }
    
    const data = await res.json()
    if (data.choices && data.choices.length > 0) {
      response.value = data.choices[0].message.content
    } else {
      response.value = 'No response from model.'
    }
  } catch (e: any) {
    error.value = e.message
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <div class="container-fluid h-100 d-flex flex-column overflow-hidden">
    <div class="row h-100">
      <div class="col-md-5 h-100 d-flex flex-column border-end p-3">
        <h3>Vision Debugger</h3>
        <p class="text-muted small">Test the VLLM's ability to see and describe images.</p>
        
        <div class="mb-3">
          <label class="form-label">Select Image</label>
          <input type="file" class="form-control" @change="onFileChange" accept="image/*" ref="fileInput">
        </div>
        
        <div v-if="selectedImage" class="mb-3 flex-grow-1 overflow-hidden d-flex align-items-center justify-content-center bg-dark bg-opacity-10 rounded">
          <img :src="selectedImage" class="img-fluid" style="max-height: 100%; max-width: 100%; object-fit: contain;">
        </div>
        
        <div class="mb-3">
          <label class="form-label">Prompt</label>
          <textarea class="form-control" v-model="prompt" rows="3"></textarea>
        </div>
        
        <button class="btn btn-primary w-100" @click="analyzeImage" :disabled="!selectedImage || isLoading">
          <span v-if="isLoading" class="spinner-border spinner-border-sm me-2"></span>
          Analyze
        </button>
        
        <div v-if="error" class="alert alert-danger mt-3">{{ error }}</div>
      </div>
      
      <div class="col-md-7 h-100 d-flex flex-column p-3 bg-body-tertiary">
        <h5>Model Response</h5>
        <div class="flex-grow-1 border rounded bg-body p-3 overflow-auto font-monospace" style="white-space: pre-wrap;">
          {{ response }}
        </div>
      </div>
    </div>
  </div>
</template>
