<script setup lang="ts">
import ImageGallery from '../components/ImageGallery.vue'
import { ref, onMounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const galleryRef = ref<any>(null)
const route = useRoute()
const router = useRouter()

// Watch for route changes (deep linking navigation)
watch(() => route.query.tags, (newTags) => {
  if (galleryRef.value) {
    if (Array.isArray(newTags)) {
      galleryRef.value.selectedTags = [...newTags]
    } else if (typeof newTags === 'string') {
      galleryRef.value.selectedTags = [newTags]
    } else {
      galleryRef.value.selectedTags = []
    }
  }
})

// Sync gallery state back to URL
watch(() => galleryRef.value?.selectedTags, (newTags) => {
  if (!newTags) return
  const query = { ...route.query }
  if (newTags.length > 0) {
    query.tags = newTags
  } else {
    delete query.tags
  }
  router.replace({ query })
}, { deep: true })

onMounted(async () => {
  await nextTick()
  if (route.query.tags && galleryRef.value) {
    if (Array.isArray(route.query.tags)) {
      galleryRef.value.selectedTags = [...route.query.tags]
    } else {
      galleryRef.value.selectedTags = [route.query.tags]
    }
  } else if (route.query.tag && galleryRef.value) {
      // Fallback for legacy single tag param
      galleryRef.value.selectedTags = [route.query.tag]
  }
})

function addTagFilter(event: Event) {
  const target = event.target as HTMLSelectElement
  const val = target.value
  if (val && val !== 'all' && galleryRef.value) {
    if (!galleryRef.value.selectedTags.includes(val)) {
      galleryRef.value.selectedTags.push(val)
    }
    target.value = 'all' // Reset dropdown
  }
}

function removeTagFilter(tag: string) {
  if (galleryRef.value) {
    galleryRef.value.selectedTags = galleryRef.value.selectedTags.filter((t: string) => t !== tag)
  }
}
</script>

<template>
  <div class="gallery-view h-100 d-flex flex-column island p-3">
    <div class="row mb-3 align-items-end g-3">
      <div class="col-12 col-xl-auto">
        <h3 class="mb-0">Gallery</h3>
        <div class="x-small text-muted mt-1" v-if="galleryRef">
          Showing {{ galleryRef.filteredCount }} of {{ galleryRef.totalCount }} images
        </div>
      </div>
      
      <!-- Filters & Actions -->
      <div class="col-12 col-xl flex-grow-1">
        <div class="d-flex flex-wrap gap-2 align-items-center bg-body-secondary p-2 px-3 rounded shadow-sm">
          
          <!-- Selection Mode Controls -->
          <template v-if="galleryRef?.isSelectionMode">
             <button class="btn btn-sm btn-outline-secondary" @click="galleryRef.toggleSelectionMode()">Cancel</button>
             <button class="btn btn-sm btn-outline-primary" @click="galleryRef.selectAll()">Select All</button>
             <button 
                class="btn btn-sm btn-danger d-flex align-items-center gap-2" 
                :disabled="galleryRef.selectedCount === 0"
                @click="galleryRef.deleteSelected()"
             >
                <span><i class="bi bi-trash"></i> Delete</span>
                <span class="badge bg-white text-danger rounded-pill">{{ galleryRef.selectedCount }}</span>
             </button>
             <div class="vr mx-1"></div>
          </template>
          
          <template v-else>
             <button class="btn btn-sm btn-outline-secondary" @click="galleryRef?.toggleSelectionMode()">Select</button>
             <div class="vr mx-1"></div>
          </template>

          <!-- Model Filter -->
          <div class="d-flex align-items-center gap-2">
            <span class="small text-muted text-uppercase fw-bold" style="font-size: 0.7rem;">Model:</span>
            <select v-model="galleryRef.selectedModel" class="form-select form-select-sm border-0 bg-body shadow-none" style="width: auto; min-width: 140px;" v-if="galleryRef">
              <option value="all">All Models</option>
              <option v-for="model in galleryRef.availableModels" :key="model" :value="model">{{ model }}</option>
            </select>
          </div>

          <div class="vr mx-1 d-none d-md-block"></div>

          <!-- Tag Filter (Multi-select) -->
          <div class="d-flex align-items-center gap-2" v-if="galleryRef">
            <span class="small text-muted text-uppercase fw-bold" style="font-size: 0.7rem;">Tags:</span>
            
            <!-- Active Tags List -->
            <div class="d-flex gap-1" v-if="galleryRef.selectedTags.length > 0">
               <span 
                 v-for="tag in galleryRef.selectedTags" 
                 :key="tag" 
                 class="badge bg-primary bg-opacity-10 text-primary border border-primary border-opacity-25 d-flex align-items-center gap-1"
               >
                 {{ tag }}
                 <span role="button" @click="removeTagFilter(tag)" class="text-danger small" style="cursor: pointer;"><i class="bi bi-x"></i></span>
               </span>
            </div>

            <!-- Add Tag Dropdown -->
            <select @change="addTagFilter" class="form-select form-select-sm border-0 bg-body shadow-none" style="width: auto; min-width: 100px;">
              <option value="all" selected>+ Add Filter</option>
              <option 
                v-for="tag in galleryRef.availableTags.filter((t: string) => !galleryRef.selectedTags.includes(t))" 
                :key="tag" 
                :value="tag"
              >
                {{ tag }}
              </option>
            </select>
          </div>

          <div class="vr mx-1 d-none d-md-block"></div>

          <!-- Date Preset -->
          <div class="d-flex align-items-center gap-2">
            <span class="small text-muted text-uppercase fw-bold" style="font-size: 0.7rem;">Time:</span>
            <select v-model="galleryRef.selectedDateRange" class="form-select form-select-sm border-0 bg-body shadow-none" style="width: auto;" v-if="galleryRef">
              <option value="all">All Time</option>
              <option value="today">Today</option>
              <option value="yesterday">Yesterday</option>
              <option value="week">Last 7 Days</option>
              <option value="month">Last 30 Days</option>
              <option value="custom">Custom Range...</option>
            </select>
          </div>

          <!-- Custom Date Inputs -->
          <template v-if="galleryRef?.selectedDateRange === 'custom'">
            <div class="d-flex align-items-center gap-1">
              <input type="datetime-local" v-model="galleryRef.startDate" class="form-control form-control-sm border-0 bg-body shadow-none py-0 px-2" style="font-size: 0.75rem;">
              <span class="text-muted">to</span>
              <input type="datetime-local" v-model="galleryRef.endDate" class="form-control form-control-sm border-0 bg-body shadow-none py-0 px-2" style="font-size: 0.75rem;">
            </div>
          </template>

          <!-- Rating Filter -->
          <div class="d-flex align-items-center gap-2">
            <span class="small text-muted text-uppercase fw-bold" style="font-size: 0.7rem;">Rating:</span>
            <select v-model="galleryRef.minRating" class="form-select form-select-sm border-0 bg-body shadow-none" style="width: auto;" v-if="galleryRef">
              <option :value="0">All</option>
              <option :value="1">1+ Stars</option>
              <option :value="2">2+ Stars</option>
              <option :value="3">3+ Stars</option>
              <option :value="4">4+ Stars</option>
              <option :value="5">5 Stars</option>
            </select>
          </div>

          <div class="vr mx-1 d-none d-lg-block"></div>

          <!-- Grid Size slider -->
          <div class="d-flex align-items-center gap-2 ms-auto">
            <span class="small text-muted text-uppercase fw-bold text-nowrap" style="font-size: 0.7rem;">Grid: {{ galleryRef?.columnsPerRow }}</span>
            <input 
              type="range" 
              class="form-range" 
              style="width: 100px;"
              min="2" 
              max="12" 
              step="1"
              :value="galleryRef?.columnsPerRow"
              @input="galleryRef?.setColumns(parseInt(($event.target as HTMLInputElement).value))"
            >
          </div>
        </div>
      </div>
    </div>
    <hr class="mt-0 mb-4 opacity-10">
    <div class="flex-grow-1 overflow-auto">
      <ImageGallery 
        ref="galleryRef" 
        :initial-tags="Array.isArray(route.query.tags) ? (route.query.tags as string[]) : (route.query.tags ? [route.query.tags as string] : [])" 
        @tag-click="(tag) => galleryRef?.toggleFilterTag(tag)"
      />
    </div>
  </div>
</template>

<style scoped>
.form-range {
  height: 1rem;
}

.x-small {
  font-size: 0.75rem;
}

.form-select-sm, .form-control-sm {
  padding-top: 0.15rem;
  padding-bottom: 0.15rem;
}

/* Ensure the range track is visible in both modes */
.form-range::-webkit-slider-runnable-track {
  background-color: var(--bs-border-color);
  height: 0.3rem;
  border-radius: 1rem;
}

.form-range::-moz-range-track {
  background-color: var(--bs-border-color);
  height: 0.3rem;
  border-radius: 1rem;
}

.form-range::-webkit-slider-thumb {
  margin-top: -0.35rem; /* Center the thumb on the track */
}
</style>