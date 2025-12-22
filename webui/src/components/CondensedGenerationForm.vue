<script setup lang="ts">
import { useExplorationStore } from '@/stores/exploration';
import { useGenerationStore } from '@/stores/generation';

const explorationStore = useExplorationStore();
const generationStore = useGenerationStore();

const updateParams = () => {
  generationStore.prompt = explorationStore.centerParams.prompt;
  generationStore.negativePrompt = explorationStore.centerParams.negative_prompt;
  generationStore.steps = explorationStore.centerParams.steps;
  generationStore.seed = explorationStore.centerParams.seed;
  generationStore.cfgScale = explorationStore.centerParams.guidanceScale;
  generationStore.sampler = explorationStore.centerParams.scheduler;
  generationStore.width = explorationStore.centerParams.width;
  generationStore.height = explorationStore.centerParams.height;
};
</script>

<template>
  <div class="condensed-form overflow-y-auto pe-2" style="max-height: 75vh;">
    <!-- Prompt -->
    <div class="mb-2">
      <div class="d-flex justify-content-between align-items-center mb-1">
        <label class="form-label small mb-0">Prompt</label>
        <button 
          class="btn btn-xs p-0 border-0 shadow-none" 
          @click="explorationStore.toggleLock('prompt')"
          title="Unlock to allow AI prompt variations"
        >
          {{ explorationStore.locks.prompt ? '🔒' : '🔓' }}
        </button>
      </div>
      <textarea 
        v-model="explorationStore.centerParams.prompt" 
        @change="updateParams"
        class="form-control form-control-sm" 
        rows="3"
      ></textarea>
    </div>

    <!-- Negative Prompt -->
    <div class="mb-2">
      <label class="form-label small mb-1">Negative Prompt</label>
      <textarea 
        v-model="explorationStore.centerParams.negative_prompt" 
        @change="updateParams"
        class="form-control form-control-sm" 
        rows="2"
      ></textarea>
    </div>

    <div class="row g-2 mb-2">
      <!-- Steps -->
      <div class="col-6">
        <div class="d-flex justify-content-between align-items-center mb-1">
          <label class="form-label small mb-0">Steps</label>
          <button 
            class="btn btn-xs p-0 border-0 shadow-none" 
            @click="explorationStore.toggleLock('steps')"
          >
            {{ explorationStore.locks.steps ? '🔒' : '🔓' }}
          </button>
        </div>
        <input 
          type="number" 
          v-model.number="explorationStore.centerParams.steps" 
          @change="updateParams"
          class="form-control form-control-sm" 
          min="1"
        />
      </div>
      <!-- Guidance -->
      <div class="col-6">
        <div class="d-flex justify-content-between align-items-center mb-1">
          <label class="form-label small mb-0">Guidance</label>
          <button 
            class="btn btn-xs p-0 border-0 shadow-none" 
            @click="explorationStore.toggleLock('guidance')"
          >
            {{ explorationStore.locks.guidance ? '🔒' : '🔓' }}
          </button>
        </div>
        <input 
          type="number" 
          v-model.number="explorationStore.centerParams.guidanceScale" 
          @change="updateParams"
          class="form-control form-control-sm" 
          step="0.1"
        />
      </div>
    </div>

    <!-- Sampler / Scheduler -->
    <div class="mb-2">
      <div class="d-flex justify-content-between align-items-center mb-1">
        <label class="form-label small mb-0">Sampler</label>
        <button 
          class="btn btn-xs p-0 border-0 shadow-none" 
          @click="explorationStore.toggleLock('scheduler')"
        >
          {{ explorationStore.locks.scheduler ? '🔒' : '🔓' }}
        </button>
      </div>
      <select 
        v-model="explorationStore.centerParams.scheduler" 
        @change="updateParams"
        class="form-select form-select-sm"
      >
        <option v-for="s in generationStore.samplers" :key="s" :value="s">{{ s }}</option>
      </select>
    </div>

    <!-- Seed -->
    <div class="mb-2">
      <div class="d-flex justify-content-between align-items-center mb-1">
        <label class="form-label small mb-0">Seed</label>
        <button 
          class="btn btn-xs p-0 border-0 shadow-none" 
          @click="explorationStore.toggleLock('seed')"
        >
          {{ explorationStore.locks.seed ? '🔒' : '🔓' }}
        </button>
      </div>
      <input 
        type="number" 
        v-model.number="explorationStore.centerParams.seed" 
        @change="updateParams"
        class="form-control form-control-sm"
      />
    </div>

    <!-- Size (Dimensions usually locked together) -->
    <div class="row g-2 mb-3">
      <div class="col-6">
        <label class="form-label small mb-1">Width</label>
        <input type="number" v-model.number="explorationStore.centerParams.width" @change="updateParams" class="form-control form-control-sm" step="64" />
      </div>
      <div class="col-6">
        <label class="form-label small mb-1">Height</label>
        <input type="number" v-model.number="explorationStore.centerParams.height" @change="updateParams" class="form-control form-control-sm" step="64" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.condensed-form {
  scrollbar-width: thin;
}
.form-label {
  font-weight: 600;
  color: var(--bs-secondary-color);
  font-size: 0.75rem;
}
.btn-xs {
  font-size: 0.7rem;
  line-height: 1;
}
</style>