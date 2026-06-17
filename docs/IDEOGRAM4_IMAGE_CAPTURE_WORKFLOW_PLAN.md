# Ideogram 4 Image Analysis Workflow Plan

## Purpose

Turn an arbitrary source image into an editable Ideogram 4 structured composition
that can be reviewed in a dedicated Analyze view and then intentionally applied
to Generate.

The Analyze view owns its own state. It can write intermediate and final analysis
results directly into that state without first asking for confirmation, because
it does not mutate the active Generate draft until the user chooses **Apply to
Generate**.

## Entry Points

The workflow has two supported source paths:

- **Upload**: choose an image file from the Analyze view.
- **Gallery**: click **Analyze** from an existing gallery image.

There is no Generate-screen source selector. Generated images can enter the
workflow after they are in the gallery.

## User Flow

1. Open Analyze directly or jump there from Gallery.
2. Upload or receive the selected gallery image as the analysis source.
3. Run **Analyze image**.
4. Let the local vision-capable LLM pipeline inspect the full image, refine
   detected element crops, and finalize the global prompt and palette.
5. Edit the resulting Ideogram composition in the Analyze view.
6. Apply the analyzed composition to Generate in `Merge` or `Replace` mode.

## Modes

### Replace

Use the analyzed image as the source of truth and replace the Generate
composition with the analyzed Ideogram document.

### Merge

Preserve the current Generate composition where possible, then add analyzed
details that are not already represented. Merge updates the high-level
description and background when the analyzed document provides them, and adds
deduplicated elements.

## Analyze Pipeline

The implementation uses the existing vision-capable LLM role path. No additional
detector runtime is required.

1. **Global inspection**: send the full source image and request a complete
   Ideogram 4 JSON document with coarse element boxes.
2. **Element refinement**: crop each detected element bbox with padding and ask
   the LLM for sharper element description, readable text, palette, and optional
   bbox correction.
3. **Final global pass**: send the full image and refined document so the LLM can
   improve global coherence, high-level description, and global palette while
   preserving element count, order, types, text, bboxes, and element palettes.
4. **Commit to Analyze state**: write the finalized document into the Analyze
   composition editor and keep progress/error details visible.

If a crop refinement fails, keep the coarse global element and continue. If the
final global pass fails, keep the refined element document and surface the error
without discarding useful analysis.

## UI Shape

Analyze has:

- source label and upload button,
- mode selector (`Merge` / `Replace`),
- **Analyze image** action,
- staged progress text and progress bar,
- source-image preview with composition overlay,
- editable Ideogram composition form,
- **Apply to Generate** action.

Manual candidate review is intentionally not part of the first workflow. Users
can edit the resulting composition boxes and rerun selected box analysis from the
normal Analyze element tools.

## Validation

- Upload source can be analyzed without touching Generate state.
- Gallery **Analyze** opens Analyze with the selected image already loaded.
- Progress advances through global inspection, element crop passes, and final
  global pass.
- Failed crop refinement does not discard valid global analysis.
- **Apply to Generate** is the only step that mutates Generate state.
- `.\gradlew :composeApp:compileKotlinDesktop` passes.
