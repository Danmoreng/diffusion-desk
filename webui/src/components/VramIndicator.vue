<script setup lang="ts">
import { useGenerationStore } from '@/stores/generation'

const store = useGenerationStore()
</script>

<template>
  <div class="vram-info" v-if="store.vramInfo.total > 0">
    <div class="d-flex justify-content-between align-items-center mb-1" v-if="!store.isSidebarCollapsed">
      <span class="x-small text-uppercase fw-bold text-muted">VRAM Usage</span>
      <span class="x-small font-monospace">{{ (store.vramInfo.total - store.vramInfo.free).toFixed(1) }}/{{ store.vramInfo.total.toFixed(0) }}GB</span>
    </div>
    
    <div 
      class="vram-bar-container" 
      :title="store.isSidebarCollapsed ? `VRAM: ${(store.vramInfo.total - store.vramInfo.free).toFixed(1)}/${store.vramInfo.total.toFixed(0)}GB` : ''"
      data-bs-toggle="tooltip"
      data-bs-placement="right"
    >
      <div class="vram-bar">
        <div 
          class="vram-segment sd" 
          :style="{ width: (store.vramInfo.sd / store.vramInfo.total * 100) + '%' }"
          title="Stable Diffusion"
        ></div>
        <div 
          class="vram-segment llm" 
          :style="{ width: (store.vramInfo.llm / store.vramInfo.total * 100) + '%' }"
          title="LLM"
        ></div>
        <div 
          class="vram-segment other" 
          :style="{ width: ((store.vramInfo.total - store.vramInfo.free - store.vramInfo.sd - store.vramInfo.llm) / store.vramInfo.total * 100) + '%' }"
          title="System/Other"
        ></div>
      </div>
    </div>

    <div class="vram-legend mt-2" v-if="!store.isSidebarCollapsed">
      <div class="d-flex align-items-center gap-1 x-small mb-1" title="SD Worker (Text Encoder + UNet)">
        <span class="legend-dot sd"></span>
        <span class="text-muted">SD:</span>
        <span class="ms-auto font-monospace">{{ store.vramInfo.sd.toFixed(1) }}GB</span>
      </div>
      <div class="d-flex align-items-center gap-1 x-small mb-1" title="LLM Worker">
        <span class="legend-dot llm"></span>
        <span class="text-muted">LLM:</span>
        <span class="ms-auto font-monospace">{{ store.vramInfo.llm.toFixed(1) }}GB</span>
      </div>
      <div class="d-flex align-items-center gap-1 x-small" title="Other processes using GPU">
        <span class="legend-dot other"></span>
        <span class="text-muted">Sys:</span>
        <span class="ms-auto font-monospace">{{ Math.max(0, store.vramInfo.total - store.vramInfo.free - store.vramInfo.sd - store.vramInfo.llm).toFixed(1) }}GB</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.x-small {
  font-size: 0.65rem;
  letter-spacing: 0.05rem;
}

.vram-bar-container {
  padding: 2px 0;
}

.vram-bar {
  height: 8px;
  background-color: var(--bs-secondary-bg);
  border-radius: 4px;
  display: flex;
  overflow: hidden;
  border: 1px solid var(--bs-border-color);
}

.vram-segment {
  height: 100%;
  transition: width 0.5s ease-in-out;
}

.vram-segment.sd {
  background-color: var(--bs-primary);
}

.vram-segment.llm {
  background-color: var(--bs-success);
}

.vram-segment.other {
  background-color: var(--bs-warning);
}

.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}

.legend-dot.sd { background-color: var(--bs-primary); }
.legend-dot.llm { background-color: var(--bs-success); }
.legend-dot.other { background-color: var(--bs-warning); }

.vram-legend {
  user-select: none;
}
</style>
