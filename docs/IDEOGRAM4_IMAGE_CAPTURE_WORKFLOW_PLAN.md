# Ideogram 4 Image Capture Workflow Plan

## Purpose

Add a workflow that turns a successful generated image into a richer Ideogram 4
composition prompt. The user can generate a small or fast image, choose one that
has desirable details, then ask a local vision-capable LLM to inspect that image
and preserve visible objects, text, layout, materials, colors, and composition as
structured Ideogram JSON with bounding boxes.

This is not a new page. The workflow belongs in the existing Generate screen,
inside the Ideogram composition experience, because it is part of the loop:

1. Generate an image.
2. Pick the image that has the right subject or details.
3. Capture those image details into the structured composition.
4. Edit the captured boxes and descriptions.
5. Generate a larger or more polished similar image.

## Product Shape

Add a new action near the existing Ideogram composition actions:

- **Capture image details**

The action analyzes the selected/generated image with the configured
vision-capable LLM preset. It returns a structured patch or replacement document
that can be inspected and edited in the current Composition tab and canvas.

The action should be available when:

- the active image preset uses Ideogram JSON prompt mode,
- there is a generated image available for the current draft,
- the current draft resolution still matches the generated image, and
- the selected composition LLM preset has vision support through an `mmproj`
  file.

If vision support is missing, show a clear disabled state or actionable error:
"Choose a vision-capable LLM preset with an mmproj file to capture image
details."

## Modes

### Merge Into Current Composition

Default mode. The LLM preserves the current prompt intent and existing
composition, but adds observed detail from the selected generated image.

Use when the user likes the current concept and wants to preserve image-specific
details for a larger regeneration.

Expected behavior:

- Keep the current high-level idea unless the image clearly contradicts it.
- Add visible objects that are missing from the current element list.
- Enrich existing object and text descriptions with image-observed details.
- Add or update bounding boxes from visible layout.
- Preserve literal rendered text exactly when readable.
- Add useful element palettes when visually clear.
- Avoid inventing details not visible in the image or present in the prompt.

### Replace From Image

Secondary mode. The LLM creates a fresh composition from the selected image.

Use when the generated image is now the best source of truth and the original
prompt is less important.

Expected behavior:

- Rebuild `high_level_description`, `background`, and `elements` from the
  image.
- Include `style_description` only if the image provides enough style evidence.
- Detect text elements separately from object elements.
- Return complete, editable Ideogram JSON.

## UX Placement

The Generate screen already has the right two-pane mental model:

- left: prompt and structured composition controls,
- right: generated image and composition overlay.

Recommended UI additions:

- Add **Capture image details** near **Generate composition** and **Improve
  composition** in the Composition controls.
- Add a compact mode selector next to the button:
  - `Merge` (default)
  - `Replace`
- Reuse the existing progress treatment used by staged composition generation.
- After capture, automatically switch to the Composition tab if it is not
  already active.
- Keep the overlay visible so new or changed boxes can be inspected immediately.

Avoid creating a new top-level page. A separate page would make the workflow
feel like an import tool, but the desired loop is iterative generation and
refinement.

## Data Contract

Introduce a capture result contract that is stricter than free-form text but
more flexible than a full assistant tool call.

### Merge Result

The merge action should return:

```json
{
  "high_level_description": "optional updated scene summary",
  "background": "optional updated background",
  "elements": [
    {
      "operation": "update",
      "index": 0,
      "type": "obj",
      "bbox": [120, 80, 760, 620],
      "desc": "richer visual description",
      "text": "only for text elements",
      "color_palette": ["#RRGGBB"]
    },
    {
      "operation": "add",
      "type": "obj",
      "bbox": [680, 110, 900, 360],
      "desc": "newly observed object",
      "color_palette": ["#RRGGBB"]
    }
  ],
  "remove_indexes": []
}
```

Rules:

- `operation` is `update` or `add`.
- `index` is required for `update` and forbidden for `add`.
- `type` is `obj` or `text`.
- `bbox` is optional but must be `[y_min, x_min, y_max, x_max]` in `0..1000`.
- `text` is required for text elements when readable.
- `color_palette` is optional and capped at 5 colors per element.
- `remove_indexes` should be used sparingly and only in Replace-like situations
  where an existing element clearly does not exist in the image.

The application applies the merge result through normal composition mutations so
undo/redo continues to work.

### Replace Result

The replace action should return a complete Ideogram document:

```json
{
  "high_level_description": "...",
  "style_description": {
    "aesthetics": "...",
    "lighting": "...",
    "photo": "...",
    "medium": "photograph",
    "color_palette": ["#RRGGBB"]
  },
  "compositional_deconstruction": {
    "background": "...",
    "elements": [
      {
        "type": "obj",
        "bbox": [120, 80, 760, 620],
        "desc": "...",
        "color_palette": ["#RRGGBB"]
      }
    ]
  }
}
```

The existing parser and canonical serializer should normalize the final
document before it becomes the active prompt.

## LLM Prompting Strategy

Use the existing `LlmRoleService` vision path first. Do not add a Florence-2
runtime in the first implementation.

The system prompt should tell the VLLM:

- It is inspecting a generated image to preserve visible composition details for
  a future Ideogram 4 prompt.
- It must return JSON only.
- Bounding boxes use normalized Ideogram coordinates:
  `[y_min, x_min, y_max, x_max]`.
- It should capture visible objects, readable text, materials, colors, spatial
  relationships, and background details.
- It should avoid guessing hidden or ambiguous details.
- It should prefer concrete object elements over vague grouped regions when
  objects are distinct and important.

For Merge mode, include:

- current complete Ideogram composition,
- current prompt,
- target canvas width and height,
- selected/generated image.

For Replace mode, include:

- current prompt as context only,
- target canvas width and height,
- selected/generated image.

## Validation And Repair

Use the same defensive posture as staged composition generation:

- parse JSON,
- validate allowed keys,
- validate bbox shape and bounds,
- validate colors,
- validate text element requirements,
- repair once or twice with a focused repair prompt,
- fail with a human-readable error if the model still returns invalid output.

Potential shared helper:

- Add a tolerant JSON extraction helper that can extract the first balanced
  object from model output and strip code fences.
- Consider truncation repair later if local VLLMs frequently cut off output.

Do not silently accept malformed boxes or lowercase palettes. Coerce only when
safe and predictable:

- clamp bbox coordinates to `0..1000`,
- swap min/max if the model reverses them,
- uppercase valid hex colors,
- drop invalid colors.

## Milestones

### Milestone 1: Capture Action Skeleton

Goal: Add the workflow entry point without changing generation behavior.

Tasks:

- Add a `CompositionAction.CaptureImageDetails` or a parallel capture action
  type.
- Add `CaptureImageMode.Merge` and `CaptureImageMode.Replace`.
- Add UI controls in the Composition tab:
  - mode selector,
  - **Capture image details** button,
  - loading/error state.
- Enable the action only when a current generated image is available.
- Surface a clear message when the selected composition LLM cannot accept
  images.

Validation:

- Compose desktop compile passes.
- Button appears only in Ideogram JSON mode.
- Disabled/error state is understandable without reading logs.

### Milestone 2: Replace From Image

Goal: Implement the simpler complete-document path first.

Tasks:

- Add a `captureIdeogramDocumentFromImage(...)` method in the composition/LLM
  layer.
- Prompt the VLLM for a complete Ideogram JSON document from the selected image.
- Parse with `parseIdeogramCompositionDocument`.
- Canonicalize and set `ideogram.jsonPrompt`.
- Switch to the Composition tab and select the first element.
- Add focused tests for parser/validation behavior.

Validation:

- A generated image can become a complete editable composition.
- Invalid model output produces a useful error.
- Existing manual composition editing still works.

### Milestone 3: Merge Into Current Composition

Goal: Preserve the current creative direction while adding image-observed
details.

Tasks:

- Define `IdeogramImageCapturePatch` data classes.
- Prompt the VLLM for update/add/remove operations.
- Apply patch through `CompositionMutation` or a new batch mutation.
- Preserve undo/redo history as one logical capture action.
- Do not remove existing elements by default.
- Select the first added or changed element after applying the patch.

Validation:

- Existing elements can receive richer descriptions and bboxes.
- New visible details can be added as elements.
- Current prompt intent remains recognizable.
- Undo returns to the pre-capture composition.

### Milestone 4: Bounding Box Review And Confidence UX

Goal: Make imperfect VLLM boxes easy to inspect and correct.

Tasks:

- Highlight added/updated boxes after capture.
- Show a short summary:
  - number of elements added,
  - number updated,
  - number with readable text detected,
  - number without bboxes.
- Reuse the grid overlay added to the composition canvas.
- Consider marking newly added elements in the element list until the user edits
  or selects them.

Validation:

- User can quickly see what the VLLM changed.
- Bad boxes are easy to adjust on the canvas.

### Milestone 5: Prompt Refinement For Larger Regeneration

Goal: Make the captured composition directly useful for larger follow-up
generation.

Tasks:

- After capture, keep the normal text prompt synchronized with the richer
  structured composition only when needed.
- Add a small affordance to increase resolution after capture if the user wants
  to regenerate larger.
- Ensure the selected generated image is not used as a reference when the user
  changes resolution, matching current reference-image safety behavior.
- Store captured JSON in gallery metadata like other Ideogram prompts.

Validation:

- Small image -> capture -> larger generation is a smooth path.
- Resolution changes do not accidentally send stale image-reference context.

### Milestone 6: Optional Detector Backend

Goal: Add Florence-2-style object/OCR detection only if VLLM boxes are not good
enough.

Tasks:

- Prototype a local analysis endpoint or worker using Florence-2.
- Return:
  - caption,
  - background,
  - dominant palette,
  - object boxes,
  - OCR text boxes.
- Merge detector output with VLLM descriptions, using detector boxes as layout
  anchors and the VLLM for richer semantics.
- Keep this optional because it adds another model runtime and dependency set.

Validation:

- Detector backend improves box precision or OCR enough to justify its cost.
- The default workflow still works with only the existing vision LLM stack.

## Files Likely To Change

- `composeApp/src/desktopMain/kotlin/com/diffusiondesk/desktop/screens/GenerateScreen.kt`
- `composeApp/src/desktopMain/kotlin/com/diffusiondesk/desktop/viewmodel/GenerationViewModel.kt`
- `composeApp/src/desktopMain/kotlin/com/diffusiondesk/desktop/composition/CompositionActionExecutor.kt`
- `composeApp/src/desktopMain/kotlin/com/diffusiondesk/desktop/composition/StagedIdeogramGenerator.kt`
- `composeApp/src/desktopMain/kotlin/com/diffusiondesk/desktop/core/LlmRoleService.kt`
- `composeApp/src/desktopMain/kotlin/com/diffusiondesk/desktop/viewmodel/IdeogramCompositionDocument.kt`
- focused tests under `composeApp/src/desktopTest/kotlin/...`

## Open Questions

- Should Merge mode ever remove elements automatically, or should removals be
  proposed but left unchecked by default?
- Should the capture action use the first generated image, selected gallery
  image, or a future explicit image selection control?
- Should element density be exposed as `Normal` / `High` in this workflow too?
- Should captured elements include a confidence or source note internally, even
  though Ideogram JSON itself does not need it?

## Initial Recommendation

Build milestones 1 through 3 first using the existing local VLLM path. That gives
the core user value with the fewest new dependencies: a liked generated image can
be converted into a richer, editable Ideogram composition for larger follow-up
generation. Add Florence-2 or another detector only after real use shows that
VLLM-generated boxes are not precise enough.
