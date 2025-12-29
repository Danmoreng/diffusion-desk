<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { Modal } from 'bootstrap'

const props = defineProps<{
  imageUrl?: string
  count?: number
}>()

const emit = defineEmits<{
  (e: 'confirm', payload: { deleteFile: boolean }): void
  (e: 'cancel'): void
}>()

const modalRef = ref<HTMLElement | null>(null)
let modalInstance: Modal | null = null
const deleteFile = ref(false)

onMounted(() => {
  if (modalRef.value) {
    modalInstance = new Modal(modalRef.value, { backdrop: 'static', keyboard: false })
    modalInstance.show()
  }
})

onUnmounted(() => {
  modalInstance?.dispose()
})

function confirm() {
  emit('confirm', { deleteFile: deleteFile.value })
  modalInstance?.hide()
}

function cancel() {
  emit('cancel')
  modalInstance?.hide()
}
</script>

<template>
  <Teleport to="body">
    <div class="modal fade" ref="modalRef" tabindex="-1" aria-hidden="true" style="z-index: 1070;">
      <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content">
          <div class="modal-header border-bottom-0">
            <h5 class="modal-title text-danger">🗑️ Delete {{ count && count > 1 ? `${count} Images` : 'Image' }}</h5>
            <button type="button" class="btn-close" @click="cancel"></button>
          </div>
          <div class="modal-body text-center">
            <p class="mb-3">
              Are you sure you want to delete 
              {{ count && count > 1 ? `these ${count} images` : 'this image' }} 
              from your history?
            </p>
            
            <div v-if="imageUrl && (!count || count <= 1)" class="mb-3">
              <img :src="imageUrl" class="img-fluid rounded shadow-sm" style="max-height: 150px; opacity: 0.8;" />
            </div>

            <div class="form-check form-switch d-inline-block text-start bg-body-tertiary px-4 py-2 rounded border">
              <input class="form-check-input" type="checkbox" role="switch" id="deleteFileSwitch" v-model="deleteFile">
              <label class="form-check-label ms-2" for="deleteFileSwitch">
                Also delete {{ count && count > 1 ? 'files' : 'file' }} from disk
                <div class="x-small text-muted">Cannot be undone.</div>
              </label>
            </div>
          </div>
          <div class="modal-footer border-top-0 justify-content-center pb-4">
            <button type="button" class="btn btn-secondary px-4" @click="cancel">Cancel</button>
            <button type="button" class="btn btn-danger px-4" @click="confirm">Delete</button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.x-small {
  font-size: 0.75rem;
}
</style>
