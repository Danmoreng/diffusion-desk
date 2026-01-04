export const GENERATION_LIMITS = {
  MAX_STEPS: 150,
  MIN_STEPS: 1,
  
  MAX_RESOLUTION: 4096,
  MIN_RESOLUTION: 64,
  RESOLUTION_STEP: 16,
  
  MAX_BATCH_COUNT: 8,
  MIN_BATCH_COUNT: 1,
  
  MAX_CFG_SCALE: 30,
  MIN_CFG_SCALE: 1,
  
  MAX_DENOISING: 1.0,
  MIN_DENOISING: 0.0,
  
  MAX_HIRES_UPSCALE: 4.0,
  MIN_HIRES_UPSCALE: 1.0,
};

export const DEFAULT_ASPECT_RATIOS = [
  { label: '1:1', w: 1, h: 1 },
  { label: '4:3', w: 4, h: 3 },
  { label: '3:4', w: 3, h: 4 },
  { label: '3:2', w: 3, h: 2 },
  { label: '2:3', w: 2, h: 3 },
  { label: '16:9', w: 16, h: 9 },
  { label: '9:16', w: 9, h: 16 },
  { label: '16:10', w: 16, h: 10 },
  { label: '10:16', w: 10, h: 16 },
  { label: '21:9', w: 21, h: 9 },
];
