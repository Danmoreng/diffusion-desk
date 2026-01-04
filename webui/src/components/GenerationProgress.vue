<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps<{
  step: number;
  totalSteps: number;
  phase: string;
  timeElapsed: number;
  eta?: number;
  message?: string;
  compact?: boolean;
}>();

const percentage = computed(() => {
  if (props.totalSteps <= 0) return 0;
  return Math.min(100, (props.step / props.totalSteps) * 100);
});
</script>

<template>
  <div class="generation-progress w-100" :class="{ 'compact-mode': compact }">
    <!-- Compact Mode (for Grid/Exploration) -->
    <template v-if="compact">
      <div class="d-flex justify-content-between align-items-center mb-1">
        <span class="phase-text text-truncate">{{ phase }}</span>
        <span class="time-text">{{ timeElapsed.toFixed(0) }}s</span>
      </div>
      
      <div class="progress" style="height: 4px;">
        <div 
          class="progress-bar progress-bar-striped progress-bar-animated bg-info" 
          role="progressbar" 
          :style="{ width: percentage + '%' }"
          :aria-valuenow="step" 
          :aria-valuemin="0" 
          :aria-valuemax="totalSteps"
        ></div>
      </div>
      
      <div class="d-flex justify-content-end mt-1">
         <span class="step-text">{{ step }}/{{ totalSteps }}</span>
      </div>
    </template>

    <!-- Full Mode (for Main Display) -->
    <template v-else>
      <h5 class="text-primary mb-1 text-center">{{ phase }}</h5>
      <p class="mb-3 small text-center">Generating image(s)...</p>
      
      <div v-if="message" class="alert alert-info py-1 px-2 small mb-3 text-center">
        <i class="bi bi-info-circle me-1"></i> {{ message }}
      </div>
      
      <div class="progress mb-2" style="height: 12px;">
        <div 
          class="progress-bar progress-bar-striped progress-bar-animated" 
          role="progressbar" 
          :style="{ width: percentage + '%' }"
          :aria-valuenow="step" 
          :aria-valuemin="0" 
          :aria-valuemax="totalSteps"
        ></div>
      </div>
      
      <div class="d-flex justify-content-between x-small fw-bold">
        <span>Step {{ step }} / {{ totalSteps }}</span>
        <span>
          <span class="me-3"><i class="bi bi-clock"></i> {{ timeElapsed.toFixed(1) }}s</span>
          <span v-if="eta && eta > 0">Remaining: ~{{ eta }}s</span>
        </span>
      </div>
    </template>
  </div>
</template>

<style scoped>
.phase-text {
  font-size: 0.7rem;
  font-weight: bold;
  color: var(--bs-info);
  max-width: 70%;
}

.time-text {
  font-size: 0.65rem;
  color: var(--bs-light);
}

.step-text {
  font-size: 0.65rem;
  color: var(--bs-light);
  opacity: 0.8;
}

.x-small {
  font-size: 0.7rem;
}
</style>
