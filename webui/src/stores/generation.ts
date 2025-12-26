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
  }

  // Load state from localStorage or use defaults
  const savedSettings = localStorage.getItem('webui-settings')
  const initialState = savedSettings ? { ...defaults, ...JSON.parse(savedSettings) } : defaults

  const isGenerating = ref(false)
  const isUpscaling = ref(false)
  const isModelSwitching = ref(false)
  const imageUrls = ref<string[]>([])
  const error = ref<string | null>(null)
  const lastParams = ref<any>(null)

  // State for parameters
  const prompt = ref(initialState.prompt)
  const negativePrompt = ref(initialState.negativePrompt)
  const steps = ref(initialState.steps)
  const seed = ref(-1) // Seed is not persisted
  const cfgScale = ref(initialState.cfgScale)
  const strength = ref(initialState.strength)
  const batchCount = ref(initialState.batchCount)
  const sampler = ref(initialState.sampler)
  const samplers = ref(['euler', 'euler_a', 'heun', 'dpm2', 'dpmpp_2s_a', 'dpmpp_2m', 'dpmpp_2mv2', 'ipndm', 'ipndm_v', 'lcm', 'ddim_trailing', 'tcd'])
  const width = ref(initialState.width)
  const height = ref(initialState.height)

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

  // UI State
  const isSidebarCollapsed = ref(localStorage.getItem('sidebar-collapsed') === 'true')
  const theme = ref(initialState.theme as 'light' | 'dark' | 'system')
  const saveImages = ref(initialState.saveImages)
  const outputDir = ref('outputs')
  const modelDir = ref('models')

  // Model Management State
  const models = ref<any[]>([])
  const currentModel = ref<string>('')
  const currentLlmModel = ref<string>('')
  const isModelsLoading = ref(false)
  const isLlmLoading = ref(false)
  const isLlmLoaded = ref(false)
  const isLlmThinking = ref(false)

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
    } catch (e) {
      console.error('Failed to fetch config:', e)
    }
  }

  async function updateConfig() {
    try {
      await fetch('/v1/config', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ 
          output_dir: outputDir.value,
          model_dir: modelDir.value
        })
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

  async function fetchModels() {
    isModelsLoading.value = true
    fetchConfig() // Also fetch server config
    try {
      const response = await fetch('/v1/models')
      const data = await response.json()
      models.value = data.data
      
      // Find active SD model
      const activeSdModel = models.value.find(m => (m.type === 'stable-diffusion' || m.type === 'root') && m.active)
      if (activeSdModel) {
        currentModel.value = activeSdModel.id
      }

      // Find active LLM model
      const activeLlmModel = models.value.find(m => m.type === 'llm' && m.active)
      if (activeLlmModel) {
        currentLlmModel.value = activeLlmModel.id
        isLlmLoaded.value = !!activeLlmModel.loaded
      }
    } catch (e) {
      console.error('Failed to fetch models:', e)
    } finally {
      isModelsLoading.value = false
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
      const response = await fetch('/v1/llm/load', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ model_id: modelId })
      })
      if (!response.ok) throw new Error('Failed to load LLM model')
      currentLlmModel.value = modelId
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
      const newImageUrl = `data:image/png;base64,${data.b64_json}`
      
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
  watch([prompt, negativePrompt, steps, cfgScale, sampler, width, height, theme, saveImages, strength, batchCount, hiresFix, hiresUpscaleModel, hiresUpscaleFactor, hiresDenoisingStrength, hiresSteps], (newValues) => {
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
  }

  async function requestImage(params: GenerationParams, signal?: AbortSignal): Promise<string[]> {
    const body: any = {
      prompt: params.prompt,
      negative_prompt: params.negative_prompt,
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
    }

    if (params.initImage) {
      body.init_image = params.initImage
    }
    
    if (params.maskImage) {
      body.mask_image = params.maskImage
    }

    const response = await fetch('/v1/images/generations', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(body),
      signal,
    })

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
    
    // Update seed with the actual one used from the first image
    if (responseData.data[0].seed) {
        lastParams.value.seed = responseData.data[0].seed
    }

    return responseData.data.map((item: any) => `data:image/png;base64,${item.b64_json}`);
  }

  function reuseLastSeed() {
    if (lastParams.value) {
        seed.value = lastParams.value.seed
    }
  }

  function randomizeSeed() {
    seed.value = -1
  }

  function swapDimensions() {
    const temp = width.value
    width.value = height.value
    height.value = temp
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
    isGenerating.value = true
    imageUrls.value = []
    error.value = null
    const startTime = Date.now();
    lastParams.value = { ...params }
    
    // Progress is now handled automatically via WebSocket

    try {
      imageUrls.value = await requestImage(params);
      const endTime = Date.now();
      lastParams.value.total_generation_time = (endTime - startTime) / 1000;
    } catch (e: any) {
      error.value = e.message
      console.error(e)
    } finally {
      isGenerating.value = false
    }
  }

  return { 
    isGenerating, isUpscaling, isModelSwitching, isLlmLoading, isLlmLoaded,
    imageUrls, error, 
    generateImage, requestImage, upscaleImage, parseA1111Parameters, 
    prompt, negativePrompt, steps, seed, cfgScale, strength, batchCount, sampler, samplers, width, height, 
    hiresFix, hiresUpscaleModel, hiresUpscaleFactor, hiresDenoisingStrength, hiresSteps, 
    isSidebarCollapsed, toggleSidebar, theme, toggleTheme, saveImages, initImage, maskImage,
    models, currentModel, currentLlmModel, upscaleModel, upscaleFactor, vramInfo,
    isModelsLoading, fetchModels, loadModel, loadLlmModel, unloadLlmModel, loadUpscaleModel, testLlmCompletion, enhancePrompt,
    progressStep, progressSteps, progressTime, progressPhase, progressMessage, eta, 
    lastParams, outputDir, modelDir, isLlmThinking,
    updateConfig, reuseLastSeed, randomizeSeed, swapDimensions 
  }
})
