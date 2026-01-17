<script setup lang="ts">
import { ref, onMounted, watch, nextTick } from 'vue'
import { useAssistantStore } from '@/stores/assistant'
import { useGenerationStore } from '@/stores/generation'
import MarkdownIt from 'markdown-it'

const md = new MarkdownIt({
  html: true,
  linkify: true,
  typographer: true
})

const assistantStore = useAssistantStore()
const generationStore = useGenerationStore()
const newMessage = ref('')
const scrollContainer = ref<HTMLElement | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const isDragging = ref(false)

const scrollToBottom = async () => {
  await nextTick()
  if (scrollContainer.value) {
    scrollContainer.value.scrollTop = scrollContainer.value.scrollHeight
  }
}

const handleSend = () => {
  if ((!newMessage.value.trim() && !assistantStore.attachedImage) || assistantStore.isLoading) return
  assistantStore.sendMessage(newMessage.value, assistantStore.attachedImage || undefined)
  newMessage.value = ''
  assistantStore.attachedImage = null
}

const handleFileUpload = (event: Event) => {
  const target = event.target as HTMLInputElement
  if (target.files && target.files[0]) {
    const reader = new FileReader()
    reader.onload = (e) => {
      assistantStore.attachedImage = e.target?.result as string
    }
    reader.readAsDataURL(target.files[0])
  }
  target.value = '' // Reset input
}

const clearAttachment = () => {
  assistantStore.attachedImage = null
}

const attachLastImage = async () => {
  if (generationStore.imageUrls.length > 0) {
    const url = generationStore.imageUrls[0]
    try {
      const response = await fetch(url)
      const blob = await response.blob()
      const reader = new FileReader()
      reader.onloadend = () => {
        assistantStore.attachImage(reader.result as string)
      }
      reader.readAsDataURL(blob)
    } catch (e) {
      console.error("Failed to attach last image", e)
    }
  }
}

const handleDrop = async (e: DragEvent) => {
  isDragging.value = false
  e.preventDefault()
  
  const files = e.dataTransfer?.files
  if (files && files.length > 0) {
    const file = files[0]
    if (file.type.startsWith('image/')) {
      const reader = new FileReader()
      reader.onload = (event) => {
        assistantStore.attachImage(event.target?.result as string)
      }
      reader.readAsDataURL(file)
      return
    }
  }

  // Handle image URLs (e.g. dragging from elsewhere in the app)
  const html = e.dataTransfer?.getData('text/html')
  if (html) {
    const doc = new DOMParser().parseFromString(html, 'text/html')
    const img = doc.querySelector('img')
    if (img && img.src) {
      // If it's a blob URL from our own app, we should ideally convert it to base64
      // or just use it as is if the backend can handle it.
      // But for better compatibility, let's try to fetch and convert to base64.
      try {
        const response = await fetch(img.src)
        const blob = await response.blob()
        const reader = new FileReader()
        reader.onloadend = () => {
          assistantStore.attachImage(reader.result as string)
        }
        reader.readAsDataURL(blob)
      } catch (err) {
        console.error("Failed to process dropped image URL", err)
      }
    }
  }
}

const onDragOver = (e: DragEvent) => {
  e.preventDefault()
  isDragging.value = true
}

const onDragLeave = () => {
  isDragging.value = false
}

// Markdown formatter using markdown-it
const formatMessage = (text: string | null | Array<any>) => {
  if (!text || typeof text !== 'string') return ''
  return md.render(text)
}

const getToolResult = (toolId: string) => {
  return assistantStore.messages.find(m => m.role === 'tool' && m.tool_call_id === toolId)
}

watch(() => assistantStore.messages.length, scrollToBottom)
watch(() => assistantStore.isOpen, (val) => {
  if (val) scrollToBottom()
})

onMounted(scrollToBottom)
</script>

<template>
  <div class="assistant-panel island d-flex flex-column shadow-sm" 
       :class="[
         { 'drag-over': isDragging }
       ]"
       @dragover="onDragOver"
       @dragleave="onDragLeave"
       @drop="handleDrop">
    <!-- Header -->
    <div class="p-3 border-bottom d-flex justify-content-between align-items-center bg-body-tertiary">
      <div class="d-flex align-items-center gap-2">
        <h5 class="mb-0 d-flex align-items-center">
          <i class="bi bi-robot me-2 text-primary"></i>
          Assistant
        </h5>
        <!-- Token Usage Pill -->
        <span v-if="assistantStore.lastUsage" class="badge rounded-pill bg-secondary shadow-sm opacity-75" style="font-size: 0.65rem;">
           {{ assistantStore.lastUsage.total_tokens }} / {{ generationStore.llmContextSize }}
        </span>
      </div>
      <div class="d-flex gap-2">
        <button class="btn btn-sm btn-outline-secondary" @click="assistantStore.clearHistory" title="Clear History">
          <i class="bi bi-trash"></i>
        </button>
        <button class="btn-close" @click="assistantStore.toggleAssistant"></button>
      </div>
    </div>

    <!-- Drop Overlay -->
    <div v-if="isDragging" class="drop-overlay d-flex align-items-center justify-content-center">
        <div class="text-center">
            <i class="bi bi-cloud-arrow-down fs-1 text-primary"></i>
            <p class="mb-0 fw-bold">Drop image to attach</p>
        </div>
    </div>

    <!-- Chat Area -->
    <div class="chat-history p-3 flex-grow-1 overflow-auto" ref="scrollContainer">
      <div v-for="(msg, idx) in assistantStore.messages" :key="idx" 
           class="d-flex flex-column"
           :class="[
             msg.role === 'tool' ? 'd-none' : (msg.tool_calls ? 'mb-2' : 'mb-3'),
             msg.role === 'user' ? 'align-items-end' : 'align-items-start'
           ]">
        
        <!-- 1. Text Content (User or Assistant) -->
        <div v-if="msg.role === 'user' || (msg.role === 'assistant' && msg.content)" 
             class="message-bubble p-2 rounded-3 shadow-sm mb-1" 
             :class="msg.role === 'user' ? 'bg-primary text-white' : 'bg-secondary-subtle text-body'">
          
          <!-- Show Name only if it's a pure text message from assistant (not part of a tool sequence usually) -->
          <div class="small fw-bold mb-1" v-if="msg.role === 'assistant' && !msg.tool_calls">Assistant</div>
          
          <!-- Image in history -->
          <img v-if="msg.image" :src="msg.image" class="img-fluid rounded mb-2" style="max-height: 200px;" alt="User upload" />
          
          <div class="text-break" v-html="formatMessage(msg.content)"></div>

          <div class="message-time text-end opacity-75 mt-1" style="font-size: 0.7em;">
            {{ new Date(msg.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) }}
          </div>
        </div>

        <!-- 2. Tool Calls (Combined Box: Input + Output) -->
        <div v-if="msg.role === 'assistant' && msg.tool_calls" class="w-100 px-2 mt-0 mb-1">
          <div v-for="(tool, tIdx) in msg.tool_calls" :key="tIdx" class="border rounded bg-body-tertiary mb-1 overflow-hidden shadow-sm">
             <details class="tool-combined-details">
                <!-- Header -->
                <summary class="px-2 py-2 small cursor-pointer d-flex align-items-center justify-content-between bg-body-tertiary hover-bg-secondary text-body">
                    <div class="d-flex align-items-center">
                        <i class="bi bi-gear-wide-connected me-2 text-primary"></i>
                        <span class="fw-bold me-1">Tool Call:</span> 
                        <code class="text-primary">{{ tool.function.name }}</code>
                    </div>
                    <!-- Status Indicator -->
                    <i v-if="getToolResult(tool.id)" class="bi bi-check-circle-fill text-success" title="Completed"></i>
                    <div v-else class="spinner-border spinner-border-sm text-secondary" style="width: 0.8rem; height: 0.8rem;"></div>
                </summary>
                
                <!-- Content -->
                <div class="p-2 bg-body border-top">
                    <!-- Input -->
                    <div class="mb-0">
                        <div class="small fw-bold text-secondary mb-1 text-uppercase" style="font-size: 0.7rem;">Input</div>
                        <div class="font-monospace small text-body p-2 rounded border bg-body-secondary text-break" style="white-space: pre-wrap;">{{ tool.function.arguments }}</div>
                    </div>

                    <!-- Output (if available) -->
                    <div v-if="getToolResult(tool.id)">
                        <hr class="my-2 opacity-25">
                        <div class="small fw-bold text-secondary mb-1 text-uppercase" style="font-size: 0.7rem;">Output</div>
                        <pre class="m-0 small text-body text-wrap text-break p-2 rounded border bg-body-secondary" style="max-height: 200px; overflow-y: auto; white-space: pre-wrap;">{{ getToolResult(tool.id)?.content }}</pre>
                    </div>
                </div>
             </details>
          </div>
        </div>
        
        <!-- Standalone Tool messages are hidden, as they are rendered inside the assistant block above -->

      </div>

      <div v-if="assistantStore.isLoading" class="d-flex align-items-center text-muted px-2 mt-2">
        <div class="spinner-border spinner-border-sm me-2" role="status"></div>
        <small>Thinking...</small>
      </div>
    </div>

    <!-- Input Area - Aligned with Action Bar -->
    <div class="assistant-input-area py-2 px-3 bg-body position-relative"
         :class="[generationStore.actionBarPosition === 'top' ? 'border-bottom' : 'border-top']">
      
      <!-- Image Preview -->
      <div v-if="assistantStore.attachedImage" class="attachment-preview mb-2 position-relative d-inline-block">
        <img :src="assistantStore.attachedImage" class="rounded border bg-body" style="height: 60px; width: auto;" />
        <button class="btn btn-danger btn-sm position-absolute top-0 start-100 translate-middle rounded-circle p-0 d-flex align-items-center justify-content-center" 
                style="width: 20px; height: 20px;" 
                @click="clearAttachment">
          <i class="bi bi-x small"></i>
        </button>
      </div>

      <form @submit.prevent="handleSend" class="input-group">
        <button class="btn btn-outline-secondary assistant-btn" type="button" @click="fileInput?.click()" title="Attach Image from File">
          <i class="bi bi-image"></i>
        </button>
        <button 
          class="btn btn-outline-secondary assistant-btn" 
          type="button" 
          @click="attachLastImage" 
          title="Attach Last Generated Image"
          :disabled="generationStore.imageUrls.length === 0"
        >
          <i class="bi bi-magic"></i>
        </button>
        <input 
          type="file" 
          ref="fileInput" 
          class="d-none" 
          accept="image/*" 
          @change="handleFileUpload"
        >
        
        <input 
          v-model="newMessage"
          type="text" 
          class="form-control assistant-input" 
          placeholder="Ask me anything..."
          :disabled="assistantStore.isLoading"
        >
        <button 
          class="btn btn-primary assistant-btn px-3" 
          type="submit" 
          :disabled="( !newMessage.trim() && !assistantStore.attachedImage ) || assistantStore.isLoading"
        >
          <i class="bi bi-send"></i>
        </button>
      </form>
    </div>
  </div>
</template>

<style scoped>
.assistant-panel {
  width: 400px;
  min-width: 300px;
  height: 100%;
  flex-shrink: 0;
  z-index: 1000;
  overflow: hidden;
}

.assistant-input-area {
  min-height: 57px;
  background-color: var(--bs-body-bg) !important;
  transition: all 0.3s ease;
  flex-shrink: 0;
}

.assistant-btn, .assistant-input {
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.chat-history {
  display: flex;
  flex-direction: column;
  background-image: radial-gradient(var(--bs-border-color) 1px, transparent 1px);
  background-size: 20px 20px;
  min-height: 0; /* Critical for shrinking when input grows */
}

.assistant-panel.drag-over {
  border: 2px dashed var(--bs-primary) !important;
  background-color: var(--bs-primary-bg-subtle) !important;
}

.drop-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(var(--bs-primary-rgb), 0.1);
  backdrop-filter: blur(2px);
  z-index: 100;
  pointer-events: none;
}

.message-bubble {
  max-width: 85%;
  font-size: 0.95rem;
}

.message-bubble :deep(p:last-child) {
  margin-bottom: 0;
}

.message-bubble :deep(pre) {
  background-color: rgba(0, 0, 0, 0.1);
  padding: 0.5rem;
  border-radius: 4px;
  overflow-x: auto;
  margin-top: 0.5rem;
}

.message-bubble :deep(code) {
  font-family: var(--bs-font-monospace);
  font-size: 0.85rem;
}

.bg-primary .message-bubble :deep(pre) {
  background-color: rgba(255, 255, 255, 0.2);
}

.message-bubble :deep(ul), .message-bubble :deep(ol) {
  padding-left: 1.2rem;
  margin-bottom: 0.5rem;
}

.message-time {
  font-size: 0.7rem;
}

.attachment-preview {
  margin-bottom: 10px;
}

/* Custom Scrollbar */
.chat-history::-webkit-scrollbar {
  width: 6px;
}
.chat-history::-webkit-scrollbar-thumb {
  background: var(--bs-border-color);
  border-radius: 3px;
}
</style>
