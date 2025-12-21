<script setup lang="ts">
import Sidebar from './components/Sidebar.vue'
import { useGenerationStore } from '@/stores/generation'

const store = useGenerationStore()
</script>

<template>
  <div id="app" class="d-flex vh-100 overflow-hidden">
    <div id="content-wrapper" class="d-flex w-100 h-100 position-relative">
      <!-- Toggle button moved outside aside to ensure visibility -->
      <button 
        class="btn btn-secondary toggle-btn rounded-circle p-0" 
        :style="{ left: store.isSidebarCollapsed ? '54px' : '230px' }"
        @click="store.toggleSidebar"
      >
        <span class="chevron">{{ store.isSidebarCollapsed ? '›' : '‹' }}</span>
      </button>

      <aside class="sidebar border-end position-relative bg-body d-flex flex-column" 
             :class="{ 'sidebar-collapsed': store.isSidebarCollapsed }">
        <Sidebar />
      </aside>
      
      <main class="main-content flex-grow-1 overflow-auto bg-body-tertiary p-4">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style>
.sidebar {
  width: 240px;
  transition: width 0.25s ease-in-out;
  flex-shrink: 0;
  z-index: 1050; /* Above regular content and most BS elements */
}

.sidebar.sidebar-collapsed {
  width: 64px;
  overflow: visible !important;
}

.toggle-btn {
  position: absolute;
  top: 12px;
  z-index: 1100;
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.25s ease-in-out;
  font-size: 14px;
  line-height: 1;
  border: 1px solid var(--bs-border-color);
  background-color: var(--bs-secondary-bg);
  color: var(--bs-body-color);
}

.toggle-btn:hover {
  background-color: var(--bs-primary);
  color: white;
  border-color: var(--bs-primary);
}

.chevron {
  margin-top: -2px; /* Visual centering adjustment */
}

.main-content {
  height: 100vh;
  position: relative;
  z-index: 1; /* Keep below sidebar and toggle */
  min-width: 0;
}
</style>
