import { defineStore } from 'pinia'
import { ref } from 'vue'
import { useGenerationStore } from '@/stores/generation'

export interface ChatMessage {
  role: 'user' | 'assistant' | 'system' | 'tool'
  content: string | Array<any> | null
  timestamp: number
  image?: string // Optional base64 image for display in UI
  tool_call_id?: string
  name?: string
  tool_calls?: any[]
}

export const useAssistantStore = defineStore('assistant', () => {
  const isOpen = ref(false)
  const isLoading = ref(false)
  const assistantConfig = ref<any>(null)
  const lastUsage = ref<{ prompt_tokens: number, completion_tokens: number, total_tokens: number } | null>(null)
  const messages = ref<ChatMessage[]>([
    {
      role: 'assistant',
      content: 'Hello! I am your Creative Assistant. I can help you refine prompts, analyze images, or browse your library.',
      timestamp: Date.now()
    }
  ])

  const generationStore = useGenerationStore()

  async function fetchConfig() {
    try {
      const res = await fetch('/v1/assistant/config')
      if (res.ok) {
        assistantConfig.value = await res.json()
      }
    } catch (e) {
      console.error('Failed to fetch assistant config:', e)
    }
  }

  function toggleAssistant() {
    isOpen.value = !isOpen.value
    if (isOpen.value && !assistantConfig.value) {
      fetchConfig()
    }
  }

  async function sendMessage(text: string, image?: string) {
    if (!text.trim() && !image) return

    // Prepare UI message
    const uiMessage: ChatMessage = {
      role: 'user',
      content: text,
      timestamp: Date.now()
    }
    
    if (image) {
      uiMessage.image = image
    }
    
    messages.value.push(uiMessage)
    isLoading.value = true

    try {
      if (!assistantConfig.value) await fetchConfig()

      let currentMessages = [...messages.value]
      let toolCallCount = 0
      const maxToolCalls = 5

      while (toolCallCount < maxToolCalls) {
        // Construct API messages
        const apiMessages: any[] = []

        // 1. System Prompt with Context
        const context = {
          current_model: generationStore.currentModel,
          generation_params: {
            prompt: generationStore.prompt,
            steps: generationStore.steps,
            cfg: generationStore.cfgScale,
            width: generationStore.width,
            height: generationStore.height,
            sampler: generationStore.sampler
          }
        }

        apiMessages.push({
          role: 'system',
          content: `${assistantConfig.value?.system_prompt || 'You are a Creative Assistant.'}
          Current Context: ${JSON.stringify(context, null, 2)}`
        })

        // 2. History
        currentMessages.forEach(m => {
          if (m.role === 'system' && !m.tool_call_id) return 
          
          const msg: any = { role: m.role, content: m.content }
          if (m.name) msg.name = m.name
          if (m.tool_call_id) msg.tool_call_id = m.tool_call_id
          if (m.tool_calls) msg.tool_calls = m.tool_calls
          
          if (m.image) {
            msg.content = [
              { type: 'text', text: typeof m.content === 'string' ? m.content : '' },
              { type: 'image_url', image_url: { url: m.image } }
            ]
          }
          apiMessages.push(msg)
        })

        const body: any = {
          messages: apiMessages,
          stream: false,
          max_tokens: 1024
        }

        // Only add tools if they exist
        if (assistantConfig.value?.tools && assistantConfig.value.tools.length > 0) {
          body.tools = assistantConfig.value.tools
          body.tool_choice = 'auto'
        }

        const response = await fetch('/v1/chat/completions', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(body)
        })

        if (!response.ok) {
          const errData = await response.json().catch(() => ({}))
          throw new Error(errData.error || 'LLM request failed')
        }

        const data = await response.json()
        if (data.usage) {
          lastUsage.value = data.usage
        }
        const choice = data.choices[0]
        const message = choice.message

        // Handle tool calls
        if (message.tool_calls && message.tool_calls.length > 0) {
          toolCallCount++
          
          // Add the assistant's tool call message to history
          const assistantMsg: ChatMessage = {
            role: 'assistant',
            content: message.content || '',
            timestamp: Date.now(),
            ...message // Includes tool_calls
          }
          currentMessages.push(assistantMsg)
          messages.value.push(assistantMsg) // Update UI

          // Execute each tool call
          for (const toolCall of message.tool_calls) {
            let result: any
            const args = JSON.parse(toolCall.function.arguments)

            if (toolCall.function.name === 'update_generation_params') {
                 // Frontend interception
                 try {
                     if (args.prompt !== undefined) generationStore.prompt = args.prompt
                     if (args.negative_prompt !== undefined) generationStore.negativePrompt = args.negative_prompt
                     if (args.steps !== undefined) generationStore.steps = args.steps
                     if (args.width !== undefined) generationStore.width = args.width
                     if (args.height !== undefined) generationStore.height = args.height
                     if (args.cfg_scale !== undefined) generationStore.cfgScale = args.cfg_scale
                     result = { status: "success", message: "Parameters updated in UI." }
                 } catch (e: any) {
                     result = { error: e.message }
                 }
            } else {
                const toolRes = await fetch('/v1/tools/execute', {
                  method: 'POST',
                  headers: { 'Content-Type': 'application/json' },
                  body: JSON.stringify({
                    name: toolCall.function.name,
                    arguments: args
                  })
                })
                result = await toolRes.json()
            }
            
            // Add tool result to history
            const toolMsg: ChatMessage = {
              role: 'tool',
              name: toolCall.function.name,
              tool_call_id: toolCall.id,
              content: JSON.stringify(result),
              timestamp: Date.now()
            }
            currentMessages.push(toolMsg)
            messages.value.push(toolMsg) // Update UI
          }
          // Continue loop to let LLM process tool results
          continue
        }

        // Final assistant message
        const content = message.content || message.reasoning_content || "I'm sorry, I couldn't generate a response."
        messages.value.push({
          role: 'assistant',
          content: content.trim(),
          timestamp: Date.now()
        })
        break
      }
    } catch (error: any) {
      console.error('Failed to send message:', error)
      messages.value.push({
        role: 'system',
        content: `Error: ${error.message}`,
        timestamp: Date.now()
      })
    } finally {
      isLoading.value = false
    }
  }

  function clearHistory() {
    messages.value = [
      {
        role: 'assistant',
        content: 'History cleared. How can I help you now?',
        timestamp: Date.now()
      }
    ]
  }

  return {
    isOpen,
    isLoading,
    messages,
    lastUsage,
    toggleAssistant,
    sendMessage,
    clearHistory
  }
})
