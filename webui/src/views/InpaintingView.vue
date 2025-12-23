<script setup lang="ts">
import GenerationForm from '../components/GenerationForm.vue'
import ImageDisplay from '../components/ImageDisplay.vue'
import InpaintingCanvas from '../components/InpaintingCanvas.vue'
</script>

<template>
  <div class="row g-3">
    <section class="col-lg-7">
      <InpaintingCanvas />
      <div class="mt-3">
          <ImageDisplay />
      </div>
    </section>
    <section class="col-lg-5">
      <GenerationForm mode="inpainting" />
    </section>
  </div>
</template>
