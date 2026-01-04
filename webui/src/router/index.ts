import { createRouter, createWebHistory } from 'vue-router'
import TextToImageView from '../views/TextToImageView.vue'
import ImageToImageView from '../views/ImageToImageView.vue'
import InpaintingView from '../views/InpaintingView.vue'
import UpscaleView from '../views/UpscaleView.vue'
import ExplorationView from '../views/ExplorationView.vue'
import SettingsView from '../views/SettingsView.vue'
import GalleryView from '../views/GalleryView.vue'
import ManagerView from '../views/ManagerView.vue'
import VisionDebugView from '../views/VisionDebugView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'Text-to-Image',
      component: TextToImageView
    },
    {
      path: '/vision',
      name: 'Vision Debugger',
      component: VisionDebugView
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
      path: '/inpainting',
      name: 'Inpainting',
      component: InpaintingView
    },
    {
      path: '/upscale',
      name: 'Upscale',
      component: UpscaleView
    },
    {
      path: '/manager/:section?/:subsection?',
      name: 'manager',
      component: () => import('../views/ManagerView.vue')
    },
    {
      path: '/settings',
      name: 'Settings',
      component: SettingsView
    },
    {
      path: '/gallery',
      name: 'Gallery',
      component: GalleryView
    }
  ]
})

export default router
