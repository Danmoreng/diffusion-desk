import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useImageStore = defineStore('image', () => {
  const prompt = ref('')
  const imageUrl = ref<string | undefined>(undefined)
  const isLoading = ref(false)
  const metadata = ref<{ [key: string]: any } | undefined>(undefined) // Added metadata state
  const error = ref<string | undefined>(undefined)

  // This will eventually make an API call to the sd-server
  async function generateImage(newPrompt: string, steps: number, seed: number, cfgScale: number, sampler: string) {
    isLoading.value = true
    error.value = undefined
    imageUrl.value = undefined // Clear previous image
    metadata.value = undefined // Clear previous metadata

    try {
      prompt.value = newPrompt

      // In a real scenario, this would be an actual API call to your sd-server backend.
      // For now, we'll simulate it.
      const response = await fetch('/api/generate-image', { // Assuming an API endpoint
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ prompt: newPrompt, steps, seed, cfgScale, sampler }),
      });

      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.message || 'Failed to generate image from server.');
      }

      const data = await response.json();
      imageUrl.value = data.imageUrl || 'https://via.placeholder.com/512x512?text=Generated+Image'; // Use actual image URL if available
      metadata.value = data.metadata || { /* default metadata */ }; // Set metadata from response

    } catch (e: any) {
      error.value = e.message || 'An unknown error occurred during image generation.'
    } finally {
      isLoading.value = false
    }
  }

  return {
    prompt,
    imageUrl,
    isLoading,
    metadata, // Expose metadata
    error,
    generateImage,
  }
})
