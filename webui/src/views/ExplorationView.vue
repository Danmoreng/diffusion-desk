<script setup lang="ts">
import { onMounted, computed } from 'vue';
import { useExplorationStore } from '@/stores/exploration';
import { useGenerationStore } from '@/stores/generation';
import CondensedGenerationForm from '@/components/CondensedGenerationForm.vue';

const explorationStore = useExplorationStore();
const generationStore = useGenerationStore();

onMounted(() => {
  explorationStore.syncFromGenerationStore();
});

const gridCells = computed(() => {
  // Map neighbor cells to include their original data
  const cells = explorationStore.neighborCells.map(c => ({
    ...c, 
    isCenter: false, 
    activeGenerating: c.isGenerating
  }));
  
  const centerCell = {
    params: explorationStore.centerParams,
    label: 'Anchor',
    url: explorationStore.centerUrl,
    isCenter: true,
    activeGenerating: explorationStore.isAnchorGenerating
  };
  
  // Insert center at index 4
  cells.splice(4, 0, centerCell as any);
  return cells;
});

const handleCellClick = (cell: any) => {
  if (cell.isCenter) return;
  explorationStore.promoteToCenter(cell);
};

const dynamicAspectRatio = computed(() => {
  const w = explorationStore.centerParams.width || 1024;
  const h = explorationStore.centerParams.height || 1024;
  return w / h;
});

</script>

<template>
  <div class="exploration-view-wrapper">
    <div class="row g-0 h-100">
      <!-- Sidebar -->
            <aside class="col-md-3 border-end h-100 d-flex flex-column bg-body-tertiary">
              <div class="p-3 flex-grow-1 overflow-y-auto">
                <h5 class="mb-3 d-flex align-items-center">
                  <i class="bi bi-sliders me-2"></i> Anchor Params
                </h5>
                <CondensedGenerationForm />
                <hr class="my-3">
              </div>

        <div class="p-3 border-top bg-body">
          <button 
            class="btn btn-primary w-100 py-2 d-flex align-items-center justify-content-center gap-2" 
            @click="explorationStore.refreshVariations()" 
            :disabled="explorationStore.isGeneratingVariations"
          >
            <span v-if="explorationStore.isGeneratingVariations" class="spinner-border spinner-border-sm"></span>
            <span v-else><i class="bi bi-arrow-repeat"></i></span>
            Refresh Variations
          </button>
        </div>
      </aside>

      <!-- Main Grid -->
      <main class="col-md-9 h-100 overflow-hidden d-flex flex-column">
        <div class="flex-grow-1 p-3 d-flex align-items-center justify-content-center overflow-hidden">
          <div class="exploration-grid-container" :style="{ maxWidth: `calc((100vh - 120px) * ${dynamicAspectRatio})` }">
            <div class="row g-2">
              <div v-for="(cell, index) in gridCells" 
                   :key="index + '-' + cell.label + (cell.url ? '-loaded' : '-loading')" 
                   class="col-4">
                <div 
                  class="card exploration-card border-0 shadow-sm bg-dark overflow-hidden"
                  :class="{ 'anchor-border': cell.isCenter, 'clickable': !cell.isCenter }"
                  :style="{ aspectRatio: dynamicAspectRatio }"
                  @click="handleCellClick(cell)"
                >
                  <!-- Badge -->
                  <div class="position-absolute top-0 start-0 m-1 z-1">
                    <span class="badge" :class="cell.isCenter ? 'bg-primary' : 'bg-black bg-opacity-50'">
                      {{ cell.label }}
                    </span>
                  </div>

                  <!-- Image Wrapper (Absolute to fill aspect-ratio card) -->
                  <div class="position-absolute top-0 start-0 w-100 h-100 d-flex align-items-center justify-content-center">
                    <img v-if="cell.url" :src="cell.url" class="img-fluid grid-img" />
                    <div v-else class="text-center p-2 w-100">
                      <div class="spinner-border spinner-border-sm text-light mb-2" role="status"></div>
                      <div class="text-light tiny-text mb-2">
                        {{ cell.activeGenerating ? 'Generating...' : 'Waiting...' }}
                      </div>
                      
                      <!-- Cell Progress (Only for active) -->
                      <div v-if="cell.activeGenerating && generationStore.progressSteps > 0" class="px-3">
                        <div class="tiny-text text-info mb-1 fw-bold">{{ generationStore.progressPhase }}</div>
                        <div class="progress bg-secondary mb-1" style="height: 4px;">
                          <div 
                            class="progress-bar bg-info progress-bar-striped progress-bar-animated" 
                            role="progressbar" 
                            :style="{ width: (generationStore.progressStep / generationStore.progressSteps * 100) + '%' }"
                          ></div>
                        </div>
                        <div class="tiny-text text-light d-flex justify-content-between">
                          <span>{{ generationStore.progressStep }}/{{ generationStore.progressSteps }}</span>
                          <span>{{ generationStore.progressTime.toFixed(0) }}s</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>
  </div>
</template>

<style scoped>
.exploration-view-wrapper {
  height: calc(100vh - 4rem); /* Viewport - padding */
  margin: 0;
}

.exploration-grid-container {
  width: 100%;
  max-width: 1200px; /* Limit ultra-wide growth */
  height: auto;
}

.exploration-card {
  transition: transform 0.15s ease-in-out, z-index 0.15s;
  background-color: #1a1a1a !important;
  position: relative; /* For absolute child */
}

.exploration-card.clickable:hover {
  transform: scale(1.05);
  z-index: 10;
  cursor: pointer;
  box-shadow: 0 0.5rem 1.5rem rgba(0, 0, 0, 0.5) !important;
}

.anchor-border {
  outline: 3px solid var(--bs-primary);
  outline-offset: -3px;
  z-index: 5;
}

.grid-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.tiny-text {
  font-size: 0.65rem;
}

.z-1 {
  z-index: 1;
}

/* Scrollbar styling for sidebar */
aside::-webkit-scrollbar {
  width: 4px;
}
aside::-webkit-scrollbar-thumb {
  background: var(--bs-border-color);
  border-radius: 4px;
}
</style>
