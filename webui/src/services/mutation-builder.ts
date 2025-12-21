
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
};

export type MutationResult = {
  params: GenParams;
  label: string;
  url?: string;
  isGenerating?: boolean;
};

export class MutationBuilder {
  private samplers = ['euler', 'euler_a', 'heun', 'dpm2', 'dpmpp_2s_a', 'dpmpp_2m', 'dpmpp_2mv2', 'ipndm', 'ipndm_v', 'lcm', 'ddim_trailing', 'tcd'];

  async generateVariations(centerParams: GenParams, locks: LockedParams): Promise<MutationResult[]> {
    const slots: MutationResult[] = [];
    const unlockedFields = Object.entries(locks)
      .filter(([_, locked]) => !locked)
      .map(([field]) => field);

    // Rule 1: If Seed is unlocked, always reserve exactly 1 slot for 'Random Seed'.
    if (!locks.seed) {
      slots.push({
        params: { ...centerParams, seed: Math.floor(Math.random() * 2147483647) },
        label: 'Random Seed'
      });
    }

    // Rule 2: (Removed Prompt Rewriting)

    // Rule 3: Fill remaining slots
    const maxAttempts = 50;
    let attempts = 0;

    while (slots.length < 8 && attempts < maxAttempts) {
      attempts++;
      
      const candidates: (() => MutationResult | null)[] = [];

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
        // If nothing is unlocked, we just have to break or fill with duplicates (though seed is usually unlocked)
        break;
      }
    }

    // Final fill with random seeds if still not at 8
    while (slots.length < 8) {
       slots.push({
          params: { ...centerParams, seed: Math.floor(Math.random() * 2147483647) },
          label: 'Random Seed'
        });
    }

    return slots.slice(0, 8);
  }
}

export const mutationBuilder = new MutationBuilder();
