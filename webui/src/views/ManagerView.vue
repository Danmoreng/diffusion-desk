<script setup lang="ts">
import { ref, onMounted, computed, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { Modal } from 'bootstrap'

interface TagInfo {
  name: string
  category: string
  count: number
}

const router = useRouter()
const activeTab = ref('tags')
const tags = ref<TagInfo[]>([])
const isLoading = ref(false)
const searchQuery = ref('')

const modalRef = ref<HTMLElement | null>(null)
let modalInstance: Modal | null = null

const filteredTags = computed(() => {
  if (!searchQuery.value) return tags.value
  const query = searchQuery.value.toLowerCase()
  return tags.value.filter(t => t.name.toLowerCase().includes(query))
})

async function fetchTags() {
  isLoading.value = true
  try {
    const res = await fetch('/v1/history/tags')
    if (res.ok) {
      tags.value = await res.json()
    }
  } catch (e) {
    console.error('Failed to fetch tags', e)
  } finally {
    isLoading.value = false
  }
}

function showCleanupModal() {
  if (modalRef.value) {
    modalInstance = new Modal(modalRef.value)
    modalInstance.show()
  }
}

async function confirmCleanup() {
  modalInstance?.hide()
  isLoading.value = true
  try {
    const res = await fetch('/v1/history/tags/cleanup', { method: 'POST' })
    if (res.ok) {
      await fetchTags() // Refresh list
    }
  } catch (e) {
    console.error('Failed to cleanup tags', e)
  } finally {
    isLoading.value = false
  }
}

function navigateToTag(tagName: string) {
  router.push({ path: '/gallery', query: { tags: tagName } })
}

onMounted(() => {
  fetchTags()
})

onUnmounted(() => {
  modalInstance?.dispose()
})
</script>

<template>
  <div class="manager-view h-100 d-flex flex-column">
    <div class="d-flex justify-content-between align-items-center mb-3">
      <h3 class="mb-0">Library Manager</h3>
    </div>

    <!-- Tabs -->
    <ul class="nav nav-tabs mb-3">
      <li class="nav-item">
        <a class="nav-link" :class="{ active: activeTab === 'tags' }" href="#" @click.prevent="activeTab = 'tags'">Tags</a>
      </li>
      <li class="nav-item">
        <a class="nav-link disabled" href="#" title="Coming soon">Styles</a>
      </li>
      <li class="nav-item">
        <a class="nav-link disabled" href="#" title="Coming soon">LoRAs</a>
      </li>
      <li class="nav-item">
        <a class="nav-link disabled" href="#" title="Coming soon">Embeddings</a>
      </li>
    </ul>

    <!-- Tags Content -->
    <div v-if="activeTab === 'tags'" class="flex-grow-1 d-flex flex-column overflow-hidden">
      
      <!-- Toolbar -->
      <div class="d-flex gap-2 mb-3 bg-body-secondary p-2 rounded">
        <input type="text" v-model="searchQuery" class="form-control form-control-sm" placeholder="Search tags..." style="max-width: 250px;">
        <div class="vr mx-1"></div>
        <button class="btn btn-sm btn-outline-danger" @click="showCleanupModal" :disabled="isLoading">
          🧹 Cleanup Unused Tags
        </button>
        <button class="btn btn-sm btn-outline-secondary ms-auto" @click="fetchTags" :disabled="isLoading">
          🔄 Refresh
        </button>
      </div>

      <!-- Table -->
      <div class="flex-grow-1 overflow-auto border rounded bg-body">
        <table class="table table-hover table-striped mb-0">
          <thead class="sticky-top bg-body-tertiary">
            <tr>
              <th scope="col" style="width: 50%">Name</th>
              <th scope="col">Category</th>
              <th scope="col" class="text-end">Usage Count</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="isLoading && tags.length === 0">
              <td colspan="3" class="text-center py-5 text-muted">Loading tags...</td>
            </tr>
            <tr v-else-if="filteredTags.length === 0">
              <td colspan="3" class="text-center py-5 text-muted">No tags found.</td>
            </tr>
            <tr v-for="tag in filteredTags" :key="tag.name">
              <td class="fw-medium">
                <a href="#" @click.prevent="navigateToTag(tag.name)" class="text-decoration-none text-body">
                   {{ tag.name }}
                   <span class="ms-1 small text-primary opacity-50">↗</span>
                </a>
              </td>
              <td><span class="badge bg-secondary bg-opacity-25 text-body-emphasis border">{{ tag.category }}</span></td>
              <td class="text-end font-monospace">{{ tag.count }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      
      <div class="mt-2 text-muted x-small">
        Total tags: {{ tags.length }}
      </div>
    </div>

    <!-- Placeholders for other tabs -->
    <div v-else class="flex-grow-1 d-flex align-items-center justify-content-center text-muted">
      <div class="text-center">
        <h4>🚧 Work in Progress</h4>
        <p>This section is coming soon.</p>
      </div>
    </div>

    <!-- Cleanup Confirmation Modal -->
    <Teleport to="body">
    <div class="modal fade" ref="modalRef" tabindex="-1" aria-hidden="true">
      <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content">
          <div class="modal-header border-bottom-0">
            <h5 class="modal-title text-danger">🧹 Cleanup Tags</h5>
            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
          </div>
          <div class="modal-body text-center py-4">
            <div class="fs-1 mb-3">🗑️</div>
            <p class="mb-0">Are you sure you want to delete all tags that are not assigned to any image?</p>
            <p class="text-muted small mt-2">This action cannot be undone.</p>
          </div>
          <div class="modal-footer border-top-0 justify-content-center pb-4">
            <button type="button" class="btn btn-secondary px-4" data-bs-dismiss="modal">Cancel</button>
            <button type="button" class="btn btn-danger px-4" @click="confirmCleanup">Cleanup</button>
          </div>
        </div>
      </div>
    </div>
    </Teleport>
  </div>
</template>

<style scoped>
.x-small {
  font-size: 0.75rem;
}
</style>