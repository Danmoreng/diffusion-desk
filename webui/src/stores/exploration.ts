import { defineStore } from 'pinia';
import { ref, reactive, watch } from 'vue';
import { mutationBuilder } from '../services/mutation-builder';
import type { GenParams, LockedParams, MutationResult } from '../services/mutation-builder';
import { useGenerationStore } from './generation';

export const useExplorationStore = defineStore('exploration', () => {
  const generationStore = useGenerationStore();

  const centerParams = ref<GenParams>({
    prompt: generationStore.prompt,
    negative_prompt: generationStore.negativePrompt,
    seed: generationStore.seed,
    steps: generationStore.steps,
    guidanceScale: generationStore.cfgScale,
    scheduler: generationStore.sampler,
    width: generationStore.width,
    height: generationStore.height,
  });

  const locks = reactive<LockedParams>({
    steps: true,
    guidance: true,
    scheduler: true,
    seed: false,
    prompt: true,
  });

  const neighborCells = ref<MutationResult[]>([]);
  const centerUrl = ref<string | null>(null);
  const isGeneratingVariations = ref(false);
  const isAnchorGenerating = ref(false);
  let isInternalChange = false;
  let currentAbortController: AbortController | null = null;

  function cancelOngoingRequests() {
    if (currentAbortController) {
      console.log('Cancelling ongoing variation requests...');
      currentAbortController.abort();
      currentAbortController = null;
    }
    isAnchorGenerating.value = false;
    neighborCells.value.forEach(c => c.isGenerating = false);
  }

  // Sync initial state if empty
  function syncFromGenerationStore() {
    isInternalChange = true;
    centerParams.value = {
      prompt: generationStore.prompt,
      negative_prompt: generationStore.negativePrompt,
      seed: generationStore.seed,
      steps: generationStore.steps,
      guidanceScale: generationStore.cfgScale,
      scheduler: generationStore.sampler,
      width: generationStore.width,
      height: generationStore.height,
    };
    if (generationStore.imageUrls.length > 0) {
      centerUrl.value = generationStore.imageUrls[0];
    } else {
      centerUrl.value = null;
    }
    isInternalChange = false;
  }

  // Watch for manual parameter changes to clear the anchor image
  watch(centerParams, () => {
    if (!isInternalChange) {
      console.log('Manual param change detected, clearing centerUrl');
      centerUrl.value = null;
    }
  }, { deep: true, flush: 'sync' });

  async function refreshVariations() {
    cancelOngoingRequests();
    currentAbortController = new AbortController();
    const signal = currentAbortController.signal;

    isGeneratingVariations.value = true
    try {
      // Reset URLs so they show the loading spinner
      neighborCells.value.forEach(c => c.url = undefined);
      
      neighborCells.value = await mutationBuilder.generateVariations(centerParams.value, locks);
      await generateAll(signal);
    } finally {
      // Only clear if it's the same controller that started this batch
      if (currentAbortController?.signal === signal) {
        isGeneratingVariations.value = false;
        currentAbortController = null;
      }
    }
  }

  async function generateAll(signal: AbortSignal) {
    // 1. Generate center image if missing
    if (!centerUrl.value) {
      isAnchorGenerating.value = true;
      try {
        console.log('Generating missing anchor image...');
        const { urls: centerResults } = await generationStore.requestImage({
          ...centerParams.value,
          cfgScale: centerParams.value.guidanceScale,
          sampler: centerParams.value.scheduler,
          negative_prompt: centerParams.value.negative_prompt,
          strength: 0.75,
          batchCount: 1,
          saveImages: false,
        }, signal);
        centerUrl.value = centerResults[0];
      } catch (e: any) {
        if (e.name === 'AbortError') return;
        console.error('Failed to generate center image', e);
      } finally {
        isAnchorGenerating.value = false;
      }
    }

    // 2. Generate neighbors
    for (const cell of neighborCells.value) {
      if (signal.aborted) break;
      if (cell.url) continue;
      cell.isGenerating = true;
      try {
        const { urls: results } = await generationStore.requestImage({
          ...cell.params,
          cfgScale: cell.params.guidanceScale,
          sampler: cell.params.scheduler,
          negative_prompt: cell.params.negative_prompt,
          strength: 0.75,
          batchCount: 1,
          saveImages: false,
        }, signal);
        cell.url = results[0];
      } catch (e: any) {
        if (e.name === 'AbortError') return;
        console.error(`Failed to generate variation: ${cell.label}`, e);
      } finally {
        cell.isGenerating = false;
      }
    }
  }

  function promoteToCenter(cell: MutationResult) {
    console.log('Promoting to center:', cell.label);
    isInternalChange = true;
    
    // Update center params and URL
    centerParams.value = JSON.parse(JSON.stringify(cell.params));
    centerUrl.value = cell.url || null;
    
    // Sync back to main store
    generationStore.prompt = cell.params.prompt;
    generationStore.negativePrompt = cell.params.negative_prompt;
    generationStore.seed = cell.params.seed;
    generationStore.steps = cell.params.steps;
    generationStore.cfgScale = cell.params.guidanceScale;
    generationStore.sampler = cell.params.scheduler;
    generationStore.width = cell.params.width;
    generationStore.height = cell.params.height;
    if (cell.url) {
      generationStore.imageUrls = [cell.url];
    }

    isInternalChange = false;
    refreshVariations();
  }

  function toggleLock(field: keyof LockedParams) {
    locks[field] = !locks[field];
  }

  return {
    centerParams,
    locks,
    neighborCells,
    centerUrl,
    isGeneratingVariations,
    isAnchorGenerating,
    syncFromGenerationStore,
    refreshVariations,
    promoteToCenter,
    toggleLock,
  };
});
