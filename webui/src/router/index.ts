import { createRouter, createWebHistory } from 'vue-router'
import TextToImageView from '../views/TextToImageView.vue'
import ImageToImageView from '../views/ImageToImageView.vue'
import ExplorationView from '../views/ExplorationView.vue'
import SettingsView from '../views/SettingsView.vue'
import HistoryView from '../views/HistoryView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'Text-to-Image',
      component: TextToImageView
    },
    {
      path: '/exploration',
      name: 'Exploration',
      component: ExplorationView
    },
    {
      path: '/img2img',
      name: 'Image-to-Image',
      component: ImageToImageView
    },
    {
      path: '/settings',
      name: 'Settings',
      component: SettingsView
    },
    {
      path: '/history',
      name: 'History',
      component: HistoryView
    }
  ]
})

export default router
