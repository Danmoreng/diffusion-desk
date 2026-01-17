<script setup lang="ts">
import Sidebar from './components/Sidebar.vue'
import FloatingActionBar from './components/FloatingActionBar.vue'
import AssistantPanel from './components/AssistantPanel.vue'
import SetupWizard from './components/SetupWizard.vue'
import { useGenerationStore } from '@/stores/generation'
import { useAssistantStore } from '@/stores/assistant'

const store = useGenerationStore()
const assistantStore = useAssistantStore()

store.fetchConfig()
</script>

<template>
  <div id="app" class="d-flex vh-100 overflow-hidden">
    <SetupWizard v-if="!store.setupCompleted" @completed="store.fetchConfig" />
    <div id="content-wrapper" class="d-flex w-100 h-100 position-relative p-2 gap-2">
      <!-- Toggle button moved outside aside to ensure visibility -->
      <button 
        class="btn btn-secondary toggle-btn rounded-circle p-0" 
        :style="{ left: store.isSidebarCollapsed ? '62px' : '238px' }"
        @click="store.toggleSidebar"
      >
        <i :class="store.isSidebarCollapsed ? 'bi bi-chevron-right' : 'bi bi-chevron-left'"></i>
      </button>

      <aside class="sidebar island position-relative d-flex flex-column" 
             :class="{ 'sidebar-collapsed': store.isSidebarCollapsed }">
        <Sidebar />
      </aside>
      
      <!-- Assistant Panel (Left) -->
      <AssistantPanel v-if="assistantStore.isOpen && store.assistantPosition === 'left'" />

      <div class="d-flex flex-column flex-grow-1 overflow-hidden gap-2">
        <FloatingActionBar v-if="store.actionBarPosition === 'top'" />
        
        <main class="main-content flex-grow-1 overflow-auto">
          <div class="h-100">
            <router-view />
          </div>
        </main>

        <FloatingActionBar v-if="store.actionBarPosition === 'bottom'" />
      </div>

      <!-- Assistant Panel (Right) -->
      <AssistantPanel v-if="assistantStore.isOpen && store.assistantPosition === 'right'" />
    </div>
  </div>
</template>

<style>
.sidebar {
  width: 240px;
  transition: width 0.25s ease-in-out;
  flex-shrink: 0;
  z-index: 1050; /* Above regular content and most BS elements */
  overflow: visible !important;
}

.sidebar.sidebar-collapsed {
  width: 64px;
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

.main-content {
  height: 100vh;
  position: relative;
  z-index: 1; /* Keep below sidebar and toggle */
  min-width: 0;
}
</style>
