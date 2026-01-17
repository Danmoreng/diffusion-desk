<script setup lang="ts">
import GenerationForm from '../components/GenerationForm.vue'
import ImageDisplay from '../components/ImageDisplay.vue'
import InpaintingCanvas from '../components/InpaintingCanvas.vue'
</script>

<template>
  <div class="d-flex flex-column flex-lg-row gap-2 h-100">
    <!-- Generation Parameters (Left) -->
    <section class="d-flex flex-column" style="flex: 1; min-width: 0;">
       <GenerationForm mode="inpainting" />
    </section>

    <!-- Images (Right) -->
    <section class="d-flex flex-column gap-2" style="flex: 1; min-width: 0;">
       <!-- Input Image Island -->
       <div class="flex-fill" style="min-height: 0;">
          <InpaintingCanvas class="h-100" style="overflow-y: auto;" />
       </div>
       
       <!-- Output Image Island -->
       <div class="flex-fill" style="min-height: 0;">
          <ImageDisplay />
       </div>
    </section>
  </div>
</template>