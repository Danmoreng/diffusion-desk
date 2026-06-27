# Runtime Memory Notes

**Status:** Current supporting notes for memory and performance work.

DiffusionDesk is now Compose-first. The app favors explicit worker control,
per-preset placement, idle unloads, and visible diagnostics over fully automatic
VRAM arbitration in the legacy C++ orchestrator.

## Current Model

- Compose supervises the image worker and one or more LLM workers.
- Image presets own model component paths and placement-related options such as
  VAE on CPU, parameter offload, and streaming layers.
- LLM presets own CPU/GPU placement and validated advanced llama.cpp arguments.
- Users can explicitly unload image models and LLM models from the System area.
- Idle unload behavior exists for SD and LLM workers.
- Image generation can unload GPU LLM workers before starting a large image job.

## Ideas Worth Reusing

These ideas came from the older VRAM arbitration design and may still be useful
when working on runtime polish:

- **Memory diagnostics:** show which image and LLM models are loaded, where they
  are placed, and why the app chose to unload or keep them.
- **Soft reservation:** avoid starting two expensive jobs that both assume the
  same free VRAM is available.
- **Smart waiting:** when a requested model is loading, queue compatible
  requests behind that load instead of failing or starting duplicate loads.
- **Low-VRAM preset guidance:** make VAE on CPU, parameter offload, and
  streaming layers easier to understand during onboarding and preset editing.
- **Escalation clarity:** prefer explicit user-enabled policies and clear
  notifications before automatically unloading or moving models.

## Current Non-Goals

- Do not restore the legacy orchestrator as the normal Compose control plane.
- Do not silently move models between GPU and CPU without a clear user-facing
  policy.
- Do not add complex automatic VRAM arbitration until profiling shows a specific
  user problem that explicit controls cannot solve.

## Related Work

- Active roadmap: `docs/DEVELOPMENT_PLAN.md`
- LLM worker design: `docs/compose-llm-worker-support-spec.md`
- Ideogram backend benchmark: `docs/ideogram4-performance-benchmark.md`
