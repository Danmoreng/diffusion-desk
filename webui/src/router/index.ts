import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'Text-to-Image',
      component: () => import('../views/TextToImageView.vue')
    },
    {
      path: '/exploration',
      name: 'Exploration',
      component: () => import('../views/ExplorationView.vue')
    },
    {
      path: '/img2img',
      name: 'Image-to-Image',
      component: () => import('../views/ImageToImageView.vue')
    },
    {
      path: '/inpainting',
      name: 'Inpainting',
      component: () => import('../views/InpaintingView.vue')
    },
    {
      path: '/upscale',
      name: 'Upscale',
      component: () => import('../views/UpscaleView.vue')
    },
    {
      path: '/manager/:section?/:subsection?',
      name: 'manager',
      component: () => import('../views/ManagerView.vue')
    },
    {
      path: '/settings',
      name: 'Settings',
      component: () => import('../views/SettingsView.vue')
    },
    {
      path: '/gallery',
      name: 'Gallery',
      component: () => import('../views/GalleryView.vue')
    }
  ]
})

export default router
