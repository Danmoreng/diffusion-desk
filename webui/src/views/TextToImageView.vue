<script setup lang="ts">
import GenerationForm from '../components/GenerationForm.vue'
import ImageDisplay from '../components/ImageDisplay.vue'
</script>

<template>
  <div class="d-flex flex-column flex-lg-row gap-2 h-100">
    <section class="" style="flex: 1; min-width: 0;">
      <GenerationForm mode="txt2img" />
    </section>
    <section class="" style="flex: 1; min-width: 0;">
      <ImageDisplay />
    </section>
  </div>
</template>
