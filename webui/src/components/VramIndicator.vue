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
      <!-- Projected VRAM Overlay -->
      <div class="projected-vram-marker" 
           v-if="store.projectedVram > 0 && store.vramInfo.total > 0 && !store.isSidebarCollapsed"
           :style="{ left: Math.min(98, (store.projectedVram / store.vramInfo.total * 100)) + '%' }"
           title="Projected VRAM for current settings">
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
      <div class="d-flex align-items-center gap-1 x-small mb-1" title="Other processes using GPU">
        <span class="legend-dot other"></span>
        <span class="text-muted">Sys:</span>
        <span class="ms-auto font-monospace">{{ Math.max(0, store.vramInfo.total - store.vramInfo.free - store.vramInfo.sd - store.vramInfo.llm).toFixed(1) }}GB</span>
      </div>
      <div class="d-flex align-items-center gap-1 x-small pt-1 border-top border-secondary border-opacity-25" v-if="store.projectedVram > 0">
        <span class="legend-marker projected"></span>
        <span class="text-muted">Projected:</span>
        <span class="ms-auto font-monospace fw-bold" :class="{'text-danger': store.projectedVram > store.vramInfo.total * 0.9}">
          {{ store.projectedVram.toFixed(1) }}GB
        </span>
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
  position: relative;
}

.vram-bar {
  height: 8px;
  background-color: var(--bs-secondary-bg);
  border-radius: 4px;
  display: flex;
  overflow: hidden;
  border: 1px solid var(--bs-border-color);
}

.projected-vram-marker {
  position: absolute;
  top: 0;
  height: 12px;
  width: 2px;
  background-color: #fff;
  box-shadow: 0 0 4px rgba(0,0,0,0.5);
  z-index: 5;
  pointer-events: none;
  transition: left 0.3s ease-out;
}

.vram-segment {
// ...
.legend-dot.other { background-color: var(--bs-warning); }

.legend-marker {
  width: 8px;
  height: 2px;
  display: inline-block;
}

.legend-marker.projected {
  background-color: #fff;
  height: 8px;
  width: 2px;
  margin: 0 3px;
}

.vram-legend {
  user-select: none;
}
</style>
