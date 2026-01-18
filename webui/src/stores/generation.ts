import { defineStore } from 'pinia'
import { ref, watch, computed } from 'vue'

export const useGenerationStore = defineStore('generation', () => {
  // --- State Initialization ---
  const defaults = {
    prompt: `A cinematic, melancholic photograph of a solitary hooded figure walking through a sprawling, rain-slicked metropolis at night. The city lights are a chaotic blur of neon orange and cool blue, reflecting on the wet asphalt.`,
    negativePrompt: 'deformed, bad anatomy, bad proportions, blurry, cloned face, cropped, gross proportions, jpeg artifacts, low quality, lowres, malformed, morbid, mutated, mutilated, out of frame, ugly, username, watermark, signature',
    steps: 4,
    cfgScale: 1.0,
    sampler: 'euler_a',
    width: 1024,
    height: 768,
    theme: 'system' as 'light' | 'dark' | 'system',
    saveImages: true,
    strength: 0.75,
    batchCount: 1,
    actionBarPosition: 'bottom' as 'top' | 'bottom',
    assistantPosition: 'right' as 'left' | 'right',
  }

  // Load state from localStorage or use defaults
  const savedSettings = localStorage.getItem('webui-settings')
  const initialState = savedSettings ? { ...defaults, ...JSON.parse(savedSettings) } : defaults

  const isGenerating = ref(false)
  const isEndless = ref(false)
  const isUpscaling = ref(false)
  const isModelSwitching = ref(false)
  const imageUrls = ref<string[]>([])
  const error = ref<string | null>(null)
  const lastParams = ref<any>(null)
  const lastGenerationMode = ref<'txt2img' | 'img2img' | 'inpainting'>('txt2img')

  // State for parameters
  const prompt = ref(initialState.prompt)
  const negativePrompt = ref(initialState.negativePrompt)
  const steps = ref(initialState.steps)
  const seed = ref(-1) // Seed is not persisted
  const lastExplicitSeed = ref(-1) // Track what the user last manually set for NEW generations
  const cfgScale = ref(initialState.cfgScale)
  const strength = ref(initialState.strength)
  const batchCount = ref(initialState.batchCount)
  const sampler = ref(initialState.sampler)
  const samplers = ref(['euler', 'euler_a', 'heun', 'dpm2', 'dpmpp_2s_a', 'dpmpp_2m', 'dpmpp_2mv2', 'ipndm', 'ipndm_v', 'lcm', 'ddim_trailing', 'tcd'])
  const width = ref(initialState.width)
  const height = ref(initialState.height)

  // Capture manual seed changes if we are at the end of history (input mode)
  watch(seed, (newVal) => {
    if (historyIndex.value === -1 || historyIndex.value === history.value.length - 1) {
      lastExplicitSeed.value = newVal
    }
  })

  // Highres-fix State
  const hiresFix = ref(initialState.hiresFix || false)
  const hiresUpscaleModel = ref(initialState.hiresUpscaleModel || '')
  const hiresUpscaleFactor = ref(initialState.hiresUpscaleFactor || 2.0)
  const hiresDenoisingStrength = ref(initialState.hiresDenoisingStrength || 0.5)
  const hiresSteps = ref(initialState.hiresSteps || 20)

  // Upscale State
  const upscaleModel = ref('')
  const upscaleFactor = ref(0)

  // Img2Img State
  const initImage = ref<string | null>(null)
  const maskImage = ref<string | null>(null)
  const referenceImages = ref<string[]>([])

  // UI State
  const isSidebarCollapsed = ref(localStorage.getItem('sidebar-collapsed') === 'true')
  const theme = ref(initialState.theme as 'light' | 'dark' | 'system')
  const saveImages = ref(initialState.saveImages)
  const outputDir = ref('outputs')
  const modelDir = ref('models')
  const setupCompleted = ref(true) // Default true for legacy/existing users
  const actionBarPosition = ref(initialState.actionBarPosition as 'top' | 'bottom')
  const assistantPosition = ref(initialState.assistantPosition as 'left' | 'right')

  // Style Management
  interface Style {
    name: string
    prompt: string
    negative_prompt: string
    preview_path?: string
  }
  const styles = ref<Style[]>([])
  const activeStyleNames = ref<string[]>([])

  // Model Management State
  const models = ref<any[]>([])
  const currentModel = ref<string>('')
  const currentModelMetadata = ref<any>(null)
  const currentLlmModel = ref<string>('')
  const llmContextSize = ref(2048)
  const isModelsLoading = ref(false)
  const isLlmLoading = ref(false)
  const isLlmLoaded = ref(false)
  const isLlmThinking = ref(false)
  
  // Presets State
  const imagePresets = ref<any[]>([])
  const llmPresets = ref<any[]>([])
  const currentImagePresetId = ref<number>(-1)
  const currentLlmPresetId = ref<number>(-1)

  // Prompt History
  const promptHistory = ref<string[]>([initialState.prompt])
  // Rename local historyIndex to promptHistoryIndex to avoid confusion with the main generation history
  const promptHistoryIndex = ref(0)
  const canUndo = computed(() => promptHistoryIndex.value > 0)
  const canRedo = computed(() => promptHistoryIndex.value < promptHistory.value.length - 1)

  function commitPrompt() {
    const current = prompt.value
    // Only commit if different from *current* history pointer
    if (current !== promptHistory.value[promptHistoryIndex.value]) {
      // If we are in the middle of history, discard the "future"
      if (promptHistoryIndex.value < promptHistory.value.length - 1) {
        promptHistory.value = promptHistory.value.slice(0, promptHistoryIndex.value + 1)
      }
      promptHistory.value.push(current)
      promptHistoryIndex.value = promptHistory.value.length - 1
    }
  }

  function undoPrompt() {
    if (canUndo.value) {
      promptHistoryIndex.value--
      prompt.value = promptHistory.value[promptHistoryIndex.value]
    }
  }

  function redoPrompt() {
    if (canRedo.value) {
      promptHistoryIndex.value++
      prompt.value = promptHistory.value[promptHistoryIndex.value]
    }
  }

  // --- Generation History & Queue ---
  
  interface GenerationHistoryItem {
    uuid: string
    status: 'pending' | 'processing' | 'completed' | 'failed'
    params: GenerationParams
    images: string[]
    error?: string
    timestamp: number
    generation_time?: number
  }

  const history = ref<GenerationHistoryItem[]>([])
  const historyIndex = ref(-1) // Points to the currently displayed generation
  
  // Computed helpers
  const currentHistoryItem = computed(() => historyIndex.value >= 0 ? history.value[historyIndex.value] : null)
  const queueCount = computed(() => history.value.filter(h => h.status === 'pending').length)
  const canGoBack = computed(() => historyIndex.value > 0)
  const canGoForward = computed(() => historyIndex.value < history.value.length - 1)

  function generateUUID() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        const r = Math.random() * 16 | 0, v = c == 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
    });
  }

  function seekHistory(index: number) {
      if (index < 0 || index >= history.value.length) return
      
      historyIndex.value = index
      const item = history.value[index]
      
      // Restore Parameters (only if not processing/pending to avoid messing up active queue view?)
      // Actually user might want to see params of queued item.
      // But if we restore params, we overwrite current inputs. 
      // YES, that is the requested feature: "ALL generation parameters are set back to what they were"
      
      const p = item.params
      prompt.value = p.prompt
      negativePrompt.value = p.negative_prompt
      steps.value = p.steps
      // If we are at the latest item, restore the user's explicit seed (e.g. -1), 
      // otherwise restore the seed used for that history item.
      if (index === history.value.length - 1) {
        seed.value = lastExplicitSeed.value
      } else {
        seed.value = p.seed
      }
      
      cfgScale.value = p.cfgScale
      strength.value = p.strength
      batchCount.value = p.batchCount
      
      // Handle sampler casing/normalization if needed, but we store string
      // p.sampler might be lowercase from previous save?
      // Our store expects one of 'samplers' list.
      // Let's try to match it.
      const foundSampler = samplers.value.find(s => s.toLowerCase() === p.sampler.toLowerCase())
      if (foundSampler) sampler.value = foundSampler
      else sampler.value = p.sampler
      
      width.value = p.width
      height.value = p.height
      saveImages.value = p.saveImages
      if (p.initImage) initImage.value = p.initImage
      if (p.maskImage) maskImage.value = p.maskImage
      if (p.referenceImages) referenceImages.value = p.referenceImages
      else referenceImages.value = []
      
      // Update Display
      if (item.status === 'completed') {
          imageUrls.value = item.images
          error.value = null
      } else if (item.status === 'failed') {
          imageUrls.value = []
          error.value = item.error || 'Generation failed'
      } else {
          // Pending or Processing
          imageUrls.value = [] // Or show placeholder?
          error.value = null
      }
      
      // Update lastParams so "Reuse Last Seed" etc work from this context
      lastParams.value = { ...p }
  }

  function goBack() {
      if (canGoBack.value) seekHistory(historyIndex.value - 1)
  }

  function goForward() {
      if (canGoForward.value) seekHistory(historyIndex.value + 1)
  }

  async function processQueue() {
      if (isGenerating.value) return // Already busy
      
      // Find next pending item
      const nextIdx = history.value.findIndex(h => h.status === 'pending')
      if (nextIdx === -1) return // Nothing to do
      
      const item = history.value[nextIdx]
      item.status = 'processing'
      isGenerating.value = true
      
      // Only jump to it if we are at the end of history
      if (historyIndex.value === -1 || historyIndex.value >= nextIdx - 1) {
          seekHistory(nextIdx)
      }
      
      const startTime = Date.now()
      
      try {
          const { urls, seed: usedSeed } = await requestImage(item.params)
          
          item.status = 'completed'
          item.images = urls
          const wasRandom = item.params.seed === -1
          item.params.seed = usedSeed // Record the actual seed used
          item.generation_time = (Date.now() - startTime) / 1000
          
          // If we are still looking at this item, update the view
          if (historyIndex.value === nextIdx) {
              imageUrls.value = urls
              // Update input field ONLY if it wasn't random, otherwise stay on -1
              if (!wasRandom) {
                seed.value = usedSeed
              }
          }
          
      } catch (e: any) {
          console.error("Queue processing error:", e)
          item.status = 'failed'
          item.error = e.message
          if (historyIndex.value === nextIdx) {
              error.value = e.message
          }
      } finally {
          isGenerating.value = false
          
          // Auto-continue if Endless Mode is on
          if (isEndless.value && item.status === 'completed') {
             // Re-queue the SAME params (or current params?)
             // endless mode usually means "do it again".
             // We should use the *current* params from the store, 
             // because user might have tweaked them while waiting.
             setTimeout(() => triggerGeneration(lastGenerationMode.value), 200)
          }
          
          // Process next item
          setTimeout(() => processQueue(), 100)
      }
  }

  // VRAM State
  const vramInfo = ref({
    total: 0,
    free: 0,
    sd: 0,
    llm: 0
  })

  // WebSocket Connection
  let ws: WebSocket | null = null;
  const isWsConnected = ref(false);

  function setupWebSocket() {
    if (ws) {
      ws.close();
    }

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    // The WS port is listen_port + 3. Default is 1234 + 3 = 1237
    // Since we don't know the exact port if user changed it in config, 
    // we assume it is on the same host but we need the port.
    // For now, we assume 1237 or we could fetch it from /v1/config.
    // Better: let the orchestrator provide the WS port in a health check or config.
    // For this prototype, we use the common +3 logic.
    const port = window.location.port || '1234';
    const wsPort = parseInt(port) + 3;
    const wsUrl = `${protocol}//${window.location.hostname}:${wsPort}`;

    console.log(`Connecting to WebSocket: ${wsUrl}`);
    ws = new WebSocket(wsUrl);

    ws.onopen = () => {
      console.log('WebSocket connected');
      isWsConnected.value = true;
    };

    ws.onclose = () => {
      console.log('WebSocket disconnected, retrying in 5s...');
      isWsConnected.value = false;
      setTimeout(setupWebSocket, 5000);
    };

    ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data);
        if (msg.type === 'metrics') {
          vramInfo.value = {
            total: msg.vram_total_gb || 0,
            free: msg.vram_free_gb || 0,
            sd: msg.workers?.sd?.vram_gb || 0,
            llm: msg.workers?.llm?.vram_gb || 0
          }
          if (msg.workers?.llm) {
            isLlmLoaded.value = !!msg.workers.llm.loaded;
            if (msg.workers.llm.model) {
                currentLlmModel.value = msg.workers.llm.model;
            }
          }
        } else if (msg.type === 'progress') {
          handleProgressUpdate(msg.data);
        }
      } catch (e) {
        console.error('WS Message Error:', e);
      }
    };
  }

  // Helper to handle progress updates from WS
  function handleProgressUpdate(data: any) {
    if (data.phase) {
      if (data.phase !== progressPhase.value) {
        stepTimeHistory.value = [];
      }
      progressPhase.value = data.phase;
    }

    if (data.step > lastStepIndex.value) {
      const deltaT = data.time - lastStepTime.value;
      const deltaS = data.step - lastStepIndex.value;
      const timePerStep = deltaT / deltaS;
      
      if (timePerStep > 0) {
        stepTimeHistory.value.push(timePerStep);
        if (stepTimeHistory.value.length > 5) {
          stepTimeHistory.value.shift();
        }
      }
      
      lastStepTime.value = data.time;
      lastStepIndex.value = data.step;
    } else if (data.step < lastStepIndex.value) {
      lastStepTime.value = data.time;
      lastStepIndex.value = data.step;
    }

    progressStep.value = data.step;
    progressSteps.value = data.steps;
    progressTime.value = data.time;
    progressMessage.value = data.message || '';
  }

  // Initialize WebSocket
  setupWebSocket();

  async function fetchConfig() {
    try {
      const response = await fetch('/v1/config')
      const data = await response.json()
      if (data.output_dir) {
        outputDir.value = data.output_dir
      }
      if (data.model_dir) {
        modelDir.value = data.model_dir
      }
      if (data.setup_completed !== undefined) {
        setupCompleted.value = data.setup_completed
      }
    } catch (e) {
      console.error('Failed to fetch config:', e)
    }
  }

  async function updateConfig(markCompleted = false) {
    try {
      const body: any = { 
        output_dir: outputDir.value,
        model_dir: modelDir.value
      }
      if (markCompleted) {
        body.setup_completed = true
        setupCompleted.value = true
      }
      await fetch('/v1/config', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
      })
    } catch (e) {
      console.error('Failed to update config:', e)
    }
  }

  // Watch for sidebar changes and persist
  watch(isSidebarCollapsed, (newVal) => {
    localStorage.setItem('sidebar-collapsed', String(newVal))
  })

  // Progress State
  const progressStep = ref(0)
  const progressSteps = ref(0)
  const progressTime = ref(0)
  const progressPhase = ref('')
  const progressMessage = ref('')
  
  // Helpers for better ETA
  const lastStepTime = ref(0);
  const lastStepIndex = ref(0);
  const stepTimeHistory = ref<number[]>([]);

  const eta = computed(() => {
    if (progressSteps.value === 0 || progressStep.value === 0) return 0;
    
    // Use the average of the last few steps if available for a more stable estimate
    // otherwise fallback to total average
    const history = stepTimeHistory.value;
    const avgStepTime = history.length > 0 
      ? history.reduce((a, b) => a + b, 0) / history.length 
      : progressTime.value / progressStep.value;

    const remainingSteps = progressSteps.value - progressStep.value;
    return Math.round(avgStepTime * remainingSteps);
  });

  async function saveImagePreset(preset: any) {
    try {
      const res = await fetch('/v1/presets/image', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(preset)
      })
      if (!res.ok) throw new Error('Failed to save image preset')
      const data = await res.json()
      await fetchPresets()
      return data.id
    } catch (e: any) {
      console.error('Failed to save image preset:', e)
      error.value = e.message
      return null
    }
  }

  async function saveLlmPreset(preset: any) {
    try {
      const res = await fetch('/v1/presets/llm', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(preset)
      })
      if (!res.ok) throw new Error('Failed to save LLM preset')
      const data = await res.json()
      await fetchPresets()
      return data.id
    } catch (e: any) {
      console.error('Failed to save LLM preset:', e)
      error.value = e.message
      return null
    }
  }

  async function fetchActivePresets() {
    try {
        const res = await fetch('/v1/presets/active')
        if (res.ok) {
            const data = await res.json()
            if (data.image_preset_id > 0) currentImagePresetId.value = data.image_preset_id
            if (data.llm_preset_id > 0) currentLlmPresetId.value = data.llm_preset_id
        }
    } catch(e) { console.error("Failed to fetch active presets", e) }
  }

  async function fetchModels() {
    isModelsLoading.value = true
    fetchConfig() // Also fetch server config
    const presetsPromise = fetchPresets() // Start fetching presets
    try {
      const response = await fetch('/v1/models')
      const data = await response.json()
      models.value = data.data
      
      // Find active SD model
      const activeSdModel = models.value.find(m => (m.type === 'stable-diffusion' || m.type === 'root') && m.active)
      if (activeSdModel) {
        currentModel.value = activeSdModel.id
        await fetchCurrentModelMetadata(activeSdModel.id)
      }

      // Find active LLM model
      const activeLlmModel = models.value.find(m => m.type === 'llm' && m.active)
      if (activeLlmModel) {
        currentLlmModel.value = activeLlmModel.id
        isLlmLoaded.value = !!activeLlmModel.loaded
        // Fetch metadata to get context size
        try {
            const res = await fetch(`/v1/models/metadata/${encodeURIComponent(activeLlmModel.id)}`)
            if (res.ok) {
                const meta = await res.json()
                if (meta && meta.n_ctx) {
                    llmContextSize.value = meta.n_ctx
                }
            }
        } catch(e) { console.error("Error fetching initial LLM metadata", e) }
      }

      // Wait for presets and sync
      await presetsPromise
      await fetchActivePresets()
      syncPresetsWithActiveModels()

    } catch (e) {
      console.error('Failed to fetch models:', e)
    } finally {
      isModelsLoading.value = false
    }
  }

  function syncPresetsWithActiveModels() {
    // Sync Image Preset
    if (currentModel.value && currentImagePresetId.value <= 0) {
      const p = imagePresets.value.find(p => p.unet_path === currentModel.value)
      if (p) {
        currentImagePresetId.value = p.id
      }
    }

    // Sync LLM Preset
    if (currentLlmModel.value && currentLlmPresetId.value <= 0) {
      const p = llmPresets.value.find(p => p.model_path === currentLlmModel.value)
      if (p) {
        currentLlmPresetId.value = p.id
      }
    }
  }
  
  async function fetchPresets() {
    try {
        const resImg = await fetch('/v1/presets/image')
        imagePresets.value = await resImg.json()
        const resLlm = await fetch('/v1/presets/llm')
        llmPresets.value = await resLlm.json()
    } catch(e) { console.error("Failed to fetch presets", e) }
  }
  
  async function loadImagePreset(id: number) {
      isModelSwitching.value = true
      try {
       const res = await fetch('/v1/presets/image/load', {
         method: 'POST',
         headers: { 'Content-Type': 'application/json' },
         body: JSON.stringify({ id })
       })
       if (!res.ok) {
         const err = await res.json()
         throw new Error(err.error || 'Failed to load preset')
       }
       currentImagePresetId.value = id
       
       // Apply preset defaults if available
       const preset = imagePresets.value.find(p => p.id === id)
       if (preset && preset.default_params) {
           if (preset.default_params.width) width.value = preset.default_params.width
           if (preset.default_params.height) height.value = preset.default_params.height
           if (preset.default_params.steps) steps.value = preset.default_params.steps
           if (preset.default_params.cfg_scale) cfgScale.value = preset.default_params.cfg_scale
           if (preset.default_params.sampler) sampler.value = preset.default_params.sampler
       }
       
       // Refresh models to update active status
       await fetchModels()
     } catch(e: any) { error.value = e.message } finally { isModelSwitching.value = false }
  }

  async function loadLlmPreset(id: number) {
      isLlmLoading.value = true
      try {
        const res = await fetch('/v1/presets/llm/load', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ id })
        })
        if (!res.ok) {
             const err = await res.json()
             throw new Error(err.error || 'Failed to load LLM preset')
        }
        
        // Find preset to update local state
        const p = llmPresets.value.find(p => p.id === id)
        if (p) {
            currentLlmPresetId.value = id
            currentLlmModel.value = p.model_path
            isLlmLoaded.value = true
            llmContextSize.value = p.n_ctx || 2048
        }
      } catch(e: any) { error.value = e.message } finally { isLlmLoading.value = false }
  }

  async function fetchCurrentModelMetadata(modelId: string) {
    try {
      const res = await fetch(`/v1/models/metadata/${encodeURIComponent(modelId)}`)
      if (res.ok) {
        currentModelMetadata.value = await res.json()
      } else {
        currentModelMetadata.value = null
      }
    } catch (e) {
      console.error('Failed to fetch current model metadata', e)
      currentModelMetadata.value = null
    }
  }

  function resetToModelDefaults() {
    // 1. Try to use active preset defaults
    if (currentImagePresetId.value > 0) {
        const preset = imagePresets.value.find(p => p.id === currentImagePresetId.value)
        if (preset && preset.default_params) {
            if (preset.default_params.width) width.value = preset.default_params.width
            if (preset.default_params.height) height.value = preset.default_params.height
            if (preset.default_params.steps) steps.value = preset.default_params.steps
            if (preset.default_params.cfg_scale) cfgScale.value = preset.default_params.cfg_scale
            if (preset.default_params.sampler) sampler.value = preset.default_params.sampler
            return // Stop here if preset defaults were applied (partially or fully, we assume preset overrides model meta)
        }
    }

    // 2. Fallback to model metadata
    if (!currentModelMetadata.value || Object.keys(currentModelMetadata.value).length === 0) return
    
    const meta = currentModelMetadata.value
    if (meta.width) width.value = meta.width
    if (meta.height) height.value = meta.height
    if (meta.sample_steps) steps.value = meta.sample_steps
    if (meta.cfg_scale) cfgScale.value = meta.cfg_scale
    if (meta.sampling_method && samplers.value.includes(meta.sampling_method)) {
      sampler.value = meta.sampling_method
    }
  }

  async function loadModel(modelId: string) {
    isModelSwitching.value = true
    try {
      const response = await fetch('/v1/models/load', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ model_id: modelId })
      })
      if (!response.ok) throw new Error('Failed to load model')
      currentModel.value = modelId
      await fetchCurrentModelMetadata(modelId)
      // Refresh models list to update active status
      await fetchModels()
    } catch (e: any) {
      error.value = e.message
    } finally {
      isModelSwitching.value = false
    }
  }

  async function loadLlmModel(modelId: string) {
    isLlmLoading.value = true
    try {
      let mmproj = '';
      let n_ctx = 2048;
      let image_max_tokens = -1;
      if (modelId) {
          try {
              const res = await fetch(`/v1/models/metadata/${encodeURIComponent(modelId)}`)
              if (res.ok) {
                  const meta = await res.json()
                  if (meta) {
                      if (meta.mmproj) mmproj = meta.mmproj;
                      if (meta.n_ctx) n_ctx = meta.n_ctx;
                      if (meta.image_max_tokens) image_max_tokens = meta.image_max_tokens;
                  }
              }
          } catch(e) { console.error("Error fetching LLM metadata:", e) }
      }

      const response = await fetch('/v1/llm/load', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ model_id: modelId, mmproj_id: mmproj, n_ctx: n_ctx, image_max_tokens: image_max_tokens })
      })
      if (!response.ok) throw new Error('Failed to load LLM model')
      currentLlmModel.value = modelId
      llmContextSize.value = n_ctx
    } catch (e: any) {
      error.value = e.message
    } finally {
      isLlmLoading.value = false
    }
  }

  async function unloadLlmModel() {
    try {
      await fetch('/v1/llm/unload', { method: 'POST' })
      currentLlmModel.value = ''
    } catch (e) {
      console.error('Failed to unload LLM:', e)
    }
  }

  async function enhancePrompt() {
    if (!prompt.value || isLlmThinking.value) return
    
    // Save current state before enhancing
    commitPrompt()
    
    isLlmThinking.value = true

    try {
      const response = await fetch('/v1/chat/completions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          messages: [
            { 
              role: 'system', 
              content: 'You are a prompt engineer for Stable Diffusion. Your task is to take a short, simple prompt and expand it into a detailed, descriptive, and artistic prompt. Use vivid adjectives and specify style, lighting, and composition. Keep the output as a single paragraph of descriptive text. Do not include any conversational filler or meta-comments.' 
            },
            { role: 'user', content: `Enhance this prompt: ${prompt.value}` }
          ],
          max_tokens: 1024,
          stream: false
        })
      })
      if (!response.ok) throw new Error('LLM request failed')
      const data = await response.json()
      const message = data.choices[0].message
      let enhanced = message.content ? message.content.trim() : ''
      
      // Fallback for reasoning models that might put content in reasoning_content or if content is empty due to length
      if (!enhanced && message.reasoning_content) {
          enhanced = message.reasoning_content.trim()
      }

      if (enhanced) {
        prompt.value = enhanced
        commitPrompt()
      }
    } catch (e: any) {
      error.value = `Failed to enhance prompt: ${e.message}`
    } finally {
      isLlmThinking.value = false
    }
  }

  async function testLlmCompletion(promptText: string) {
    try {
      const response = await fetch('/v1/chat/completions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          messages: [{ role: 'user', content: promptText }],
          stream: false,
          max_tokens: 1024
        })
      })
      if (!response.ok) throw new Error('LLM request failed')
      const data = await response.json()
      const message = data.choices[0].message
      if (message.content) return message.content
      if (message.reasoning_content) return message.reasoning_content
      return "No content returned (check tokens/model)"
    } catch (e: any) {
      error.value = e.message
      return null
    }
  }

  async function loadUpscaleModel(modelId: string) {
    isModelSwitching.value = true
    try {
      const response = await fetch('/v1/upscale/load', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ model_id: modelId })
      })
      if (!response.ok) throw new Error('Failed to load upscale model')
      upscaleModel.value = modelId
      await fetchModels()
    } catch (e: any) {
      error.value = e.message
    } finally {
      isModelSwitching.value = false
    }
  }

  async function upscaleImage(image: string, name?: string) {
    isUpscaling.value = true
    error.value = null
    try {
      const body: any = {
        upscale_factor: upscaleFactor.value,
        save_image: true
      }
      if (name) {
        body.image_name = name
      } else {
        body.image = image
      }

      const response = await fetch('/v1/images/upscale', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
      })

      if (!response.ok) {
        const err = await response.json()
        throw new Error(err.error || 'Upscaling failed')
      }

      const data = await response.json()
      const newImageUrl = data.url
      
      // If we are showing the upscaled image, maybe add it to the gallery
      imageUrls.value = [newImageUrl]
      return newImageUrl
    } catch (e: any) {
      error.value = e.message
      throw e
    } finally {
      isUpscaling.value = false
    }
  }

  function toggleSidebar() {
    isSidebarCollapsed.value = !isSidebarCollapsed.value
  }

  function applyTheme() {
    let targetTheme = theme.value;
    if (targetTheme === 'system') {
      targetTheme = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    }
    document.documentElement.setAttribute('data-bs-theme', targetTheme)
  }

  function toggleTheme() {
    if (theme.value === 'system') theme.value = 'light';
    else if (theme.value === 'light') theme.value = 'dark';
    else theme.value = 'system';
  }

  // --- Effects ---

  // Watch for theme changes and apply them to the root element
  watch(theme, () => {
    applyTheme();
  }, { immediate: true })

  // Listen for system theme changes
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
    if (theme.value === 'system') {
      applyTheme();
    }
  });


  // Watch for changes in settings and persist them to localStorage
  watch([prompt, negativePrompt, steps, cfgScale, sampler, width, height, theme, saveImages, strength, batchCount, hiresFix, hiresUpscaleModel, hiresUpscaleFactor, hiresDenoisingStrength, hiresSteps, actionBarPosition, assistantPosition], (newValues) => {
    // Note: We do NOT persist referenceImages in local storage as they can be large
    const settingsToSave = {
      prompt: newValues[0],
      negativePrompt: newValues[1],
      steps: newValues[2],
      cfgScale: newValues[3],
      sampler: newValues[4],
      width: newValues[5],
      height: newValues[6],
      theme: newValues[7],
      saveImages: newValues[8],
      strength: newValues[9],
      batchCount: newValues[10],
      hiresFix: newValues[11],
      hiresUpscaleModel: newValues[12],
      hiresUpscaleFactor: newValues[13],
      hiresDenoisingStrength: newValues[14],
      hiresSteps: newValues[15],
      actionBarPosition: newValues[16],
      assistantPosition: newValues[17],
    }
    localStorage.setItem('webui-settings', JSON.stringify(settingsToSave))
  }, { deep: true })


  // --- Actions ---

  interface GenerationParams {
    prompt: string
    negative_prompt: string
    steps: number
    seed: number
    cfgScale: number
    strength: number
    batchCount: number
    sampler: string
    width: number
    height: number
    saveImages: boolean
    initImage?: string | null
    maskImage?: string | null
    referenceImages?: string[]
  }

  function dataURLtoBlob(dataurl: string) {
    const arr = dataurl.split(',')
    const mime = arr[0].match(/:(.*?);/)?.[1]
    const bstr = atob(arr[1])
    let n = bstr.length
    const u8arr = new Uint8Array(n)
    while (n--) {
      u8arr[n] = bstr.charCodeAt(n)
    }
    return new Blob([u8arr], { type: mime })
  }

  async function requestImage(params: GenerationParams, signal?: AbortSignal): Promise<{ urls: string[], seed: number }> {
    let finalPrompt = params.prompt
    let finalNegativePrompt = params.negative_prompt

    if (activeStyleNames.value.length > 0) {
      for (const styleName of activeStyleNames.value) {
          const style = styles.value.find(s => s.name === styleName)
          if (style) {
            // Apply positive prompt style
            if (style.prompt) {
              if (style.prompt.includes('{prompt}')) {
                finalPrompt = style.prompt.replace('{prompt}', finalPrompt)
              } else {
                finalPrompt = finalPrompt + (finalPrompt ? ', ' : '') + style.prompt
              }
            }
            // Apply negative prompt style
            if (style.negative_prompt) {
              finalNegativePrompt = finalNegativePrompt + (finalNegativePrompt ? ', ' : '') + style.negative_prompt
            }
          }
      }
    }

    let response: Response;

    // Check for Edit Mode (Reference Images)
    if (params.referenceImages && params.referenceImages.length > 0) {
      const formData = new FormData();
      formData.append('prompt', finalPrompt);
      if (finalNegativePrompt) {
        // We pack negative prompt into extra_args usually, but let's check if endpoint supports it directly?
        // api_endpoints.cpp: handles prompt, but others via extra_args usually.
        // Actually, SDGenerationParams::from_json_str handles negative_prompt.
      }
      formData.append('n', params.batchCount.toString());
      formData.append('size', `${params.width}x${params.height}`);
      formData.append('output_format', 'png');

      // Append Images
      for (const imgDataUrl of params.referenceImages) {
        const blob = dataURLtoBlob(imgDataUrl);
        formData.append('image[]', blob, 'ref_image.png');
      }

      // Append Mask if exists
      if (params.maskImage) {
        const blob = dataURLtoBlob(params.maskImage);
        formData.append('mask', blob, 'mask.png');
      }

      // Construct Extra Args JSON
      const extraArgs: any = {
        negative_prompt: finalNegativePrompt,
        sample_steps: params.steps,
        cfg_scale: params.cfgScale,
        strength: params.strength,
        sampling_method: params.sampler.toLowerCase().replace(' a', '_a').replace(/\+\+/g, 'pp'),
        seed: params.seed,
        save_image: params.saveImages,
        // Hires fix args if needed, though usually not for edits?
        // But if backend supports it, why not.
        hires_fix: hiresFix.value,
        hires_upscale_model: hiresUpscaleModel.value,
        hires_upscale_factor: hiresUpscaleFactor.value,
        hires_denoising_strength: hiresDenoisingStrength.value,
        hires_steps: hiresSteps.value
      };
      
      formData.append('extra_args', JSON.stringify(extraArgs));

      response = await fetch('/v1/images/edits', {
        method: 'POST',
        body: formData,
        signal,
      });

    } else {
      // Standard Generation
      const body: any = {
        prompt: finalPrompt,
        negative_prompt: finalNegativePrompt,
        sample_steps: params.steps,
        cfg_scale: params.cfgScale,
        strength: params.strength,
        n: params.batchCount,
        sampling_method: params.sampler.toLowerCase().replace(' a', '_a').replace(/\+\+/g, 'pp'),
        seed: params.seed,
        width: params.width,
        height: params.height,
        save_image: params.saveImages,
        hires_fix: hiresFix.value,
        hires_upscale_model: hiresUpscaleModel.value,
        hires_upscale_factor: hiresUpscaleFactor.value,
        hires_denoising_strength: hiresDenoisingStrength.value,
        hires_steps: hiresSteps.value,
        no_base64: true
      }

      if (params.initImage) {
        body.init_image = params.initImage
      }
      
      if (params.maskImage) {
        body.mask_image = params.maskImage
      }

      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
      }

      response = await fetch('/v1/images/generations', {
        method: 'POST',
        headers,
        body: JSON.stringify(body),
        signal,
      })
    }

    if (!response.ok) {
      const errText = await response.text();
      let errMessage = errText || 'An error occurred while generating the image.';
      try {
        const errJson = JSON.parse(errText);
        if (errJson && errJson.error) {
          errMessage = errJson.error;
        }
      } catch (e) {
        // ignore, use text
      }
      throw new Error(errMessage)
    }

    const responseData = await response.json();
    if (!responseData.data || responseData.data.length === 0) {
      throw new Error('Server response did not contain image data.');
    }
    
    const usedSeed = responseData.data[0].seed || params.seed;

    // Update seed with the actual one used from the first image
    if (usedSeed && lastParams.value) {
        lastParams.value.seed = usedSeed
    }

    return {
        urls: responseData.data.map((item: any) => item.url),
        seed: usedSeed
    };
  }

  function reuseLastSeed() {
    if (lastParams.value) {
        seed.value = lastParams.value.seed
    }
  }

  function randomizeSeed() {
    seed.value = -1
  }

  const swapDimensions = () => {
    const temp = width.value
    width.value = height.value
    height.value = temp
  }

  const gcd = (a: number, b: number): number => b ? gcd(b, a % b) : a

  const applyAspectRatio = (ratio: { w: number, h: number }) => {
    // Jump to the smallest valid resolution for this ratio
    // Smallest unit is 16px. We need both dimensions >= 64px.
    const multiplier = Math.ceil(Math.max(4 / ratio.w, 4 / ratio.h))
    
    width.value = ratio.w * multiplier * 16
    height.value = ratio.h * multiplier * 16
  }

  const snapToNext16 = (val: number) => {
    if (!val || val < 64) return 64
    if (val % 16 === 0) return val
    return Math.ceil(val / 16) * 16
  }

  async function fetchStyles() {
    try {
      const response = await fetch('/v1/styles')
      if (response.ok) {
        styles.value = await response.json()
      }
    } catch (e) {
      console.error('Failed to fetch styles:', e)
    }
  }

  async function saveStyle(style: Style) {
    try {
      const response = await fetch('/v1/styles', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(style)
      })
      if (response.ok) {
        await fetchStyles()
      }
    } catch (e) {
      console.error('Failed to save style:', e)
    }
  }

  async function deleteStyle(name: string) {
    try {
      const response = await fetch('/v1/styles', {
        method: 'DELETE',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name })
      })
      if (response.ok) {
        await fetchStyles()
      }
    } catch (e) {
      console.error('Failed to delete style:', e)
    }
  }

  function applyStyle(styleName: string) {
    if (activeStyleNames.value.includes(styleName)) {
        activeStyleNames.value = activeStyleNames.value.filter(s => s !== styleName)
    } else {
        activeStyleNames.value.push(styleName)
    }
  }

  async function extractStylesFromPrompt(promptText: string) {
      if (!promptText) return;
      isLlmThinking.value = true;
      try {
          const response = await fetch('/v1/styles/extract', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ prompt: promptText })
          })
          if (response.ok) {
            styles.value = await response.json()
          } else {
              const err = await response.json()
              throw new Error(err.error || 'Extraction failed')
          }
      } catch(e: any) {
          error.value = e.message
      } finally {
          isLlmThinking.value = false;
      }
  }

  function parseA1111Parameters(text: string) {
    if (!text || !text.includes('Steps: ')) return false;

    try {
      const lines = text.split('\n');
      let positivePrompt = '';
      let negativePromptStr = '';
      let paramsLine = '';

      let mode: 'positive' | 'negative' | 'params' = 'positive';

      for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed) continue;

        if (trimmed.startsWith('Negative prompt:')) {
          negativePromptStr = trimmed.substring(16).trim();
          mode = 'negative';
          continue;
        }

        if (trimmed.includes('Steps: ') && (trimmed.includes('Sampler: ') || trimmed.includes('Seed: '))) {
          paramsLine = trimmed;
          mode = 'params';
          continue;
        }

        if (mode === 'positive') {
          positivePrompt += (positivePrompt ? '\n' : '') + trimmed;
        } else if (mode === 'negative') {
          negativePromptStr += (negativePromptStr ? '\n' : '') + trimmed;
        }
      }

      if (paramsLine) {
        prompt.value = positivePrompt;
        negativePrompt.value = negativePromptStr;

        const parts = paramsLine.split(',');
        for (const part of parts) {
          const colonIdx = part.indexOf(':');
          if (colonIdx === -1) continue;

          const key = part.substring(0, colonIdx).trim();
          const val = part.substring(colonIdx + 1).trim();

          if (key === 'Steps') steps.value = parseInt(val);
          else if (key === 'CFG scale') cfgScale.value = parseFloat(val);
          else if (key === 'Seed') seed.value = parseInt(val);
          else if (key === 'Sampler') {
            const normalizedVal = val.toLowerCase().replace('++', 'pp').replace(/[^a-z0-9]/g, '_');
            const found = samplers.value.find(s => {
              const normalizedS = s.toLowerCase().replace(/[^a-z0-9]/g, '_');
              return normalizedVal.includes(normalizedS) || normalizedS.includes(normalizedVal);
            });
            if (found) sampler.value = found;
          } else if (key === 'Size') {
            const sizeParts = val.split('x');
            if (sizeParts.length === 2) {
              width.value = parseInt(sizeParts[0]);
              height.value = parseInt(sizeParts[1]);
            }
          } else if (key === 'Model') {
            // Find model by ID if possible
            const found = models.value.find(m => m.id === val || m.id.endsWith(val));
            if (found && found.id !== currentModel.value) {
              loadModel(found.id);
            }
          }
        }
        return true;
      }
    } catch (e) {
      console.error('Failed to parse A1111 parameters', e);
    }
    return false;
  }

  async function generateImage(params: GenerationParams) {
    if (isModelSwitching.value) return;
    
    // Create History Item
    const newItem: GenerationHistoryItem = {
        uuid: generateUUID(),
        status: 'pending',
        params: { ...params },
        images: [],
        timestamp: Date.now()
    }
    
    // Add to History
    history.value.push(newItem)
    
    // Jump to the new item ONLY if we were already at the latest item
    if (historyIndex.value === history.value.length - 2 || historyIndex.value === -1) {
        seekHistory(history.value.length - 1)
    }
    
    // Trigger Processing
    processQueue()
  }

  function triggerGeneration(mode: 'txt2img' | 'img2img' | 'inpainting') {
    if (!prompt.value || isModelSwitching.value) return
    // Note: We allow triggering even if isGenerating (to queue)
    
    lastGenerationMode.value = mode
    
    generateImage({
      prompt: prompt.value,
      negative_prompt: negativePrompt.value,
      steps: steps.value,
      seed: seed.value,
      cfgScale: cfgScale.value,
      strength: strength.value,
      batchCount: batchCount.value,
      sampler: sampler.value,
      width: width.value,
      height: height.value,
      saveImages: saveImages.value,
      initImage: (mode === 'img2img' || mode === 'inpainting') ? initImage.value : null,
      maskImage: mode === 'inpainting' ? maskImage.value : null,
      referenceImages: referenceImages.value
    })
  }

  return { 
    isGenerating, isUpscaling, isModelSwitching, isLlmLoading, isLlmLoaded, isEndless,
    imageUrls, error, 
    generateImage, triggerGeneration, requestImage, upscaleImage, parseA1111Parameters, 
    prompt, negativePrompt, steps, seed, cfgScale, strength, batchCount, sampler, samplers, width, height, 
    hiresFix, hiresUpscaleModel, hiresUpscaleFactor, hiresDenoisingStrength, hiresSteps, 
    isSidebarCollapsed, toggleSidebar, theme, toggleTheme, saveImages, initImage, maskImage, referenceImages,
    models, currentModel, currentLlmModel, upscaleModel, upscaleFactor, vramInfo,
    isModelsLoading, fetchModels, loadModel, loadLlmModel, unloadLlmModel, loadUpscaleModel, testLlmCompletion, enhancePrompt,
    progressStep, progressSteps, progressTime, progressPhase, progressMessage, eta, 
    lastParams, outputDir, modelDir, isLlmThinking, 
    promptHistory, promptHistoryIndex, canUndo, canRedo, undoPrompt, redoPrompt, commitPrompt,
    history, historyIndex, currentHistoryItem, queueCount, canGoBack, canGoForward, seekHistory, goBack, goForward,
    updateConfig, reuseLastSeed, randomizeSeed, swapDimensions,
    styles, activeStyleNames, fetchStyles, saveStyle, deleteStyle, applyStyle, extractStylesFromPrompt,
    currentModelMetadata, resetToModelDefaults, applyAspectRatio, snapToNext16,
    imagePresets, llmPresets, currentImagePresetId, currentLlmPresetId, loadImagePreset, loadLlmPreset, fetchPresets, saveImagePreset, saveLlmPreset,
    actionBarPosition, assistantPosition,
    llmContextSize, setupCompleted, fetchConfig
  }
})
