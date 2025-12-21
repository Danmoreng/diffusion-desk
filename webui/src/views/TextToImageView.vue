<script setup lang="ts">
import GenerationForm from '../components/GenerationForm.vue'
import ImageDisplay from '../components/ImageDisplay.vue'
</script>

<template>
  <div class="row g-3">
    <section class="col-lg-6">
      <GenerationForm mode="txt2img" />
    </section>
    <section class="col-lg-6">
      <ImageDisplay />
    </section>
  </div>
</template>
