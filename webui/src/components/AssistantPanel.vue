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
const attachedImage = ref<string | null>(null)

const scrollToBottom = async () => {
  await nextTick()
  if (scrollContainer.value) {
    scrollContainer.value.scrollTop = scrollContainer.value.scrollHeight
  }
}

const handleSend = () => {
  if ((!newMessage.value.trim() && !attachedImage.value) || assistantStore.isLoading) return
  assistantStore.sendMessage(newMessage.value, attachedImage.value || undefined)
  newMessage.value = ''
  attachedImage.value = null
}

const handleFileUpload = (event: Event) => {
  const target = event.target as HTMLInputElement
  if (target.files && target.files[0]) {
    const reader = new FileReader()
    reader.onload = (e) => {
      attachedImage.value = e.target?.result as string
    }
    reader.readAsDataURL(target.files[0])
  }
  target.value = '' // Reset input
}

const clearAttachment = () => {
  attachedImage.value = null
}

// Markdown formatter using markdown-it
const formatMessage = (text: string | null | Array<any>) => {
  if (!text || typeof text !== 'string') return ''
  return md.render(text)
}

watch(() => assistantStore.messages.length, scrollToBottom)
watch(() => assistantStore.isOpen, (val) => {
  if (val) scrollToBottom()
})

onMounted(scrollToBottom)
</script>

<template>
  <div class="assistant-panel bg-body d-flex flex-column shadow-sm" 
       :class="[generationStore.assistantPosition === 'right' ? 'border-start' : 'border-end']">
    <!-- Header -->
    <div class="p-3 border-bottom d-flex justify-content-between align-items-center bg-body-tertiary">
      <h5 class="mb-0 d-flex align-items-center">
        <i class="bi bi-robot me-2 text-primary"></i>
        Assistant
      </h5>
      <div class="d-flex gap-2">
        <button class="btn btn-sm btn-outline-secondary" @click="assistantStore.clearHistory" title="Clear History">
          <i class="bi bi-trash"></i>
        </button>
        <button class="btn-close" @click="assistantStore.toggleAssistant"></button>
      </div>
    </div>

    <!-- Chat Area -->
    <div class="chat-history p-3 flex-grow-1 overflow-auto" ref="scrollContainer">
      <div v-for="(msg, idx) in assistantStore.messages" :key="idx" 
           class="mb-3 d-flex flex-column"
           :class="msg.role === 'user' ? 'align-items-end' : 'align-items-start'">
        
        <div v-if="msg.role !== 'tool'" 
             class="message-bubble p-2 rounded-3 shadow-sm" 
             :class="msg.role === 'user' ? 'bg-primary text-white' : 'bg-secondary-subtle text-body'">
          <div class="small fw-bold mb-1" v-if="msg.role === 'assistant'">Assistant</div>
          
          <!-- Image in history -->
          <img v-if="msg.image" :src="msg.image" class="img-fluid rounded mb-2" style="max-height: 200px;" alt="User upload" />
          
          <div class="text-break" v-html="formatMessage(msg.content)"></div>
        </div>

        <!-- Tool Call Indicator -->
        <div v-else class="x-small text-muted mb-2 px-2">
          <i class="bi bi-gear-fill me-1"></i> Used tool: <code>{{ msg.name }}</code>
        </div>

        <div class="message-time text-muted mt-1" v-if="msg.role !== 'tool'">
          {{ new Date(msg.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) }}
        </div>
      </div>

      <div v-if="assistantStore.isLoading" class="d-flex align-items-center text-muted px-2">
        <div class="spinner-border spinner-border-sm me-2" role="status"></div>
        <small>Thinking...</small>
      </div>
    </div>

    <!-- Input Area - Aligned with Action Bar -->
    <div class="assistant-input-area py-2 px-3 bg-body"
         :class="[generationStore.actionBarPosition === 'top' ? 'border-bottom' : 'border-top']">
      <!-- Image Preview -->
      <div v-if="attachedImage" class="attachment-preview mb-2 position-relative d-inline-block">
        <img :src="attachedImage" class="rounded border bg-body" style="height: 60px; width: auto;" />
        <button class="btn btn-danger btn-sm position-absolute top-0 start-100 translate-middle rounded-circle p-0 d-flex align-items-center justify-content-center" 
                style="width: 20px; height: 20px;" 
                @click="clearAttachment">
          <i class="bi bi-x small"></i>
        </button>
      </div>

      <form @submit.prevent="handleSend" class="input-group">
        <button class="btn btn-outline-secondary assistant-btn" type="button" @click="fileInput?.click()" title="Attach Image">
          <i class="bi bi-image"></i>
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
          :disabled="( !newMessage.trim() && !attachedImage ) || assistantStore.isLoading"
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
}

.assistant-input-area {
  min-height: 57px;
  background-color: var(--bs-body-bg) !important;
  transition: all 0.3s ease;
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
