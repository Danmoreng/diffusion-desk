
export type GenParams = {
  prompt: string;
  negative_prompt: string;
  seed: number;
  steps: number;
  guidanceScale: number;
  scheduler: string;
  width: number;
  height: number;
};

export type LockedParams = {
  steps: boolean;
  guidance: boolean;
  scheduler: boolean;
  seed: boolean;
  prompt: boolean;
};

export type MutationResult = {
  params: GenParams;
  label: string;
  url?: string;
  isGenerating?: boolean;
};

export class MutationBuilder {
  private samplers = ['euler', 'euler_a', 'heun', 'dpm2', 'dpmpp_2s_a', 'dpmpp_2m', 'dpmpp_2mv2', 'ipndm', 'ipndm_v', 'lcm', 'ddim_trailing', 'tcd'];

  private async fetchPromptVariation(originalPrompt: string): Promise<string> {
    try {
      const response = await fetch('/v1/chat/completions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          messages: [
            {
              role: "system", 
              content: "You are a creative assistant. Rewrite the user's prompt to create a single, subtle variation. Maintain the core subject and style but change one or two adjectives or details. Keep it concise. Do not add any conversational text or prefixes. Return ONLY the new prompt text."
            },
            { role: "user", content: originalPrompt }
          ],
          stream: false,
          temperature: 0.9, // Higher temperature for diversity across parallel calls
          max_tokens: 300
        })
      });

      if (!response.ok) throw new Error('LLM request failed');
      const data = await response.json();
      const content = data.choices?.[0]?.message?.content;
      return content ? content.trim().replace(/^["']|["']$/g, '') : originalPrompt;
    } catch (e) {
      console.warn('Failed to fetch prompt variation:', e);
      return originalPrompt;
    }
  }

  async generateVariations(centerParamsInput: GenParams, locks: LockedParams): Promise<MutationResult[]> {
    // Ensure all numeric params are actually numbers to prevent string concatenation bugs
    const centerParams: GenParams = {
      ...centerParamsInput,
      steps: Number(centerParamsInput.steps),
      guidanceScale: Number(centerParamsInput.guidanceScale),
      width: Number(centerParamsInput.width),
      height: Number(centerParamsInput.height),
      seed: Number(centerParamsInput.seed),
    };

    const slots: MutationResult[] = [];

    // Rule 1: If Seed is unlocked, always reserve exactly 1 slot for 'Random Seed'.
    if (!locks.seed) {
      slots.push({
        params: { ...centerParams, seed: Math.floor(Math.random() * 2147483647) },
        label: 'Random Seed'
      });
    }

    // Pre-fetch prompt variations if unlocked
    // We launch multiple parallel requests to get diverse variations
    let promptVariants: string[] = [];
    if (!locks.prompt) {
      // Request 8 variations in parallel
      const promises = Array.from({ length: 8 }, () => this.fetchPromptVariation(centerParams.prompt));
      const results = await Promise.all(promises);
      
      // Filter out failures (which return original prompt) and duplicates
      promptVariants = [...new Set(results)].filter(p => p !== centerParams.prompt && p.length > 0);
    }

    // Rule 3: Fill remaining slots
    const maxAttempts = 50;
    let attempts = 0;

    while (slots.length < 8 && attempts < maxAttempts) {
      attempts++;
      
      const candidates: (() => MutationResult | null)[] = [];

      // Prompt Variations
      if (!locks.prompt && promptVariants.length > 0) {
        candidates.push(() => {
          // Pick a random variant from our pool
          const variant = promptVariants[Math.floor(Math.random() * promptVariants.length)];
          if (variant === centerParams.prompt) return null;
          return {
            params: { ...centerParams, prompt: variant },
            label: 'Prompt Var'
          };
        });
      }

      if (!locks.steps) {
        // Try ±1, ±2, ±3 etc based on slots already filled
        const stepChanges = [1, -1, 2, -2, 3, -3, 4, -4];
        candidates.push(() => {
          const change = stepChanges[Math.floor(Math.random() * stepChanges.length)];
          const newSteps = Math.max(3, centerParams.steps + change);
          if (newSteps === centerParams.steps) return null;
          return {
            params: { ...centerParams, seed: centerParams.seed, steps: newSteps },
            label: `Steps ${change > 0 ? '+' : ''}${change}`
          };
        });
      }

      if (!locks.guidance) {
        const guidanceChanges = [0.1, -0.1, 0.2, -0.2, 0.5, -0.5, 1.0, -1.0];
        candidates.push(() => {
          const change = guidanceChanges[Math.floor(Math.random() * guidanceChanges.length)];
          const newGuidance = Math.max(1.0, centerParams.guidanceScale + change);
          if (Math.abs(newGuidance - centerParams.guidanceScale) < 0.01) return null;
          return {
            params: { ...centerParams, seed: centerParams.seed, guidanceScale: Number(newGuidance.toFixed(1)) },
            label: `Guidance ${change > 0 ? '+' : ''}${change.toFixed(1)}`
          };
        });
      }

      if (!locks.scheduler) {
        candidates.push(() => {
          const currentIndex = this.samplers.indexOf(centerParams.scheduler);
          const offset = Math.floor(Math.random() * (this.samplers.length - 1)) + 1;
          const nextIndex = (currentIndex + offset) % this.samplers.length;
          const nextSampler = this.samplers[nextIndex];
          return {
            params: { ...centerParams, seed: centerParams.seed, scheduler: nextSampler },
            label: `Sampler: ${nextSampler}`
          };
        });
      }

      if (!locks.seed) {
        candidates.push(() => ({
          params: { ...centerParams, seed: Math.floor(Math.random() * 2147483647) },
          label: 'Random Seed'
        }));
      }

      if (candidates.length > 0) {
        const mutation = candidates[Math.floor(Math.random() * candidates.length)]();
        if (mutation) {
          // Check for duplicates
          const isDuplicate = slots.some(s => 
            s.params.prompt === mutation.params.prompt &&
            s.params.seed === mutation.params.seed &&
            s.params.steps === mutation.params.steps &&
            s.params.guidanceScale === mutation.params.guidanceScale &&
            s.params.scheduler === mutation.params.scheduler
          );
          if (!isDuplicate) {
            slots.push(mutation);
          }
        }
      } else {
        // If nothing is unlocked or no candidates generated
        break;
      }
    }

    // Final fill
    while (slots.length < 8) {
       if (!locks.seed) {
         slots.push({
            params: { ...centerParams, seed: Math.floor(Math.random() * 2147483647) },
            label: 'Random Seed'
          });
       } else {
         // Fallback if everything else is locked or exhausted: duplicate center
         // If we still have unused prompt variants, we could try to force them here,
         // but the loop above should have picked them.
         // If we are here, it means we failed to find 8 distinct valid mutations.
         slots.push({
            params: { ...centerParams },
            label: 'No Change'
         });
       }
    }

    return slots.slice(0, 8);
  }
}

export const mutationBuilder = new MutationBuilder();
