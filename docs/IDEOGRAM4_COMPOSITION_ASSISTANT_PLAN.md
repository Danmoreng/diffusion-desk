# Ideogram 4 Composition and Assistant Plan

## Purpose

Expand the Ideogram 4 workflow in the Compose desktop app from a basic JSON
editor into a visual composition workflow. The first step improves the layout
and creates one shared display surface for the composition and generated image.
Later phases add field-, section-, and object-level LLM actions, followed by an
assistant with controlled tool calls.

The plan primarily affects:

- `composeApp/.../screens/GenerateScreen.kt`
- `composeApp/.../viewmodel/GenerationViewModel.kt`
- `composeApp/.../core/LlmRoleService.kt`
- the Compose core chat and JSON models

The legacy Vue WebUI is only a behavioral reference for the former assistant.
All new product UI belongs in Compose.

## Target Experience

### Left Side: Editor

The left side remains the editing area with three modes:

1. **Controls**: prompt, negative prompt, and generation parameters.
2. **JSON**: raw Ideogram JSON, formatting, and full JSON generation.
3. **Composition**: complete structured editor for the Ideogram document.

The Composition mode no longer contains the visual canvas preview. Instead, it
exposes every JSON value as an individually editable field, not only the object
list. This includes `high_level_description`, every `style_description` field,
`compositional_deconstruction.background`, and every object or text element
field.

Palette and geometry controls stay visual rather than exposing duplicate raw
representations. Global and per-element palettes use the same reusable swatch
editor. A swatch opens color editing, each color can be removed, and an Add
control appends a color up to the schema limit. Do not render an additional
comma-separated palette text field beside the swatches.

Bounding boxes are edited primarily on the right canvas. The element editor
shows a compact read-only geometry summary using `X`, `Y`, `W`, and `H`, derived
from Ideogram's `[y_min, x_min, y_max, x_max]` coordinates. It must not expose
the raw four-value array as a normal text input.

`compositional_deconstruction` is not a text value in the current Ideogram
schema. It is the structural container for `background` and `elements`.
Therefore, the UI represents it as a section with actions for the complete
composition. A separate text input should only be added if a future schema
defines a scalar value for it.

### Right Side: Shared Canvas Host

The right side becomes the central canvas for:

- the composition before generation,
- generation without a large status card replacing the canvas,
- the generated image,
- a composition overlay that is visible by default over the generated image.

The composition and image must use the same calculated display rectangle.
Bounding boxes remain in Ideogram's normalized `0..1000` coordinate system and
are mapped to the visible canvas only while rendering. This keeps the overlay
aligned with the image at every window size, panel width, and output resolution.

The overlay toggle is persistent. Its initial default is enabled. Once the user
changes it, the selection remains across subsequent generations and application
restarts. A new generation must not reset it.

### Action Bar: Compact Progress

Remove the large `ProgressCard` from the right side. During generation, the
Action Bar displays compact overall progress:

- one progress track,
- current phase and percentage,
- optionally the step count and elapsed/remaining time in a short line,
- the existing Stop button.

The detailed stage list can initially be removed or moved into a tooltip or a
later expandable detail view. The existing progress data may remain in the
ViewModel.

## Architecture Decisions

### 1. Separate the Editor and Canvas

`IdeogramLayoutPreview` currently combines visual layout editing and element
form editing. Split it into:

- `IdeogramCompositionCanvas`: renders and edits bounding boxes.
- `IdeogramElementList` / `IdeogramElementEditor`: edits element data on the
  left.
- `PreviewCanvasHost`: calculates the shared image rectangle and layers the
  image and composition overlay.

The selected element ID or index belongs in shared UI state so that selecting an
element on the left highlights it on the right and selecting a box on the right
opens it on the left.

The structured Composition editor uses fixed, collapsible sections:

1. **Overview**: `high_level_description`.
2. **Style**: `aesthetics`, `lighting`, `medium`, exactly one of `photo` or
   `art_style`, and the optional global `color_palette`.
3. **Composition**: the `compositional_deconstruction` container with
   `background` and the element list.
4. **Selected element**: `type`, `bbox`, `desc`, `text` for text elements, and
   the optional `color_palette`.
5. **Additional fields**: a generic path/value editor for existing JSON fields
   that the known schema does not render with a dedicated control.

The raw JSON tab remains an expert view, but it is no longer the only way to
edit certain values. Users can add or remove known optional fields. Switching
between `photo` and `art_style` is one atomic mutation that removes the other
field and preserves the Ideogram rule that exactly one must be present.

Use one shared `PaletteEditor` for `style_description.color_palette` and each
element's `color_palette`. It accepts a maximum count, normalizes values to
uppercase `#RRGGBB`, supports edit/add/remove, and emits one atomic palette
mutation per completed interaction. The global limit is 16 colors; the element
limit is 5.

Treat `bbox` as optional. Elements without a box remain valid and render in the
editor with an `Unplaced` state plus an action to create a sensible default box.
Removing a box is also a supported mutation. Geometry labels convert the
serialized format as follows: `X = x_min`, `Y = y_min`, `W = x_max - x_min`,
and `H = y_max - y_min`.

### 2. Use One Fit Calculation

Do not independently reproduce `ContentScale.Fit` calculations for the image
and overlay. The canvas host calculates one rectangle from the available space
and target aspect ratio. Both the `Image` and bounding boxes render inside that
rectangle with `fillMaxSize()`.

After generation, the target ratio comes from the bitmap's actual dimensions.
Before generation, it comes from the configured `width` and `height`. Batch
generation will be removed from the Compose app, so the host only needs one
current image as an overlay target. The queue for separately initiated
generations remains unaffected.

### 3. Represent JSON Changes as Typed Operations

LLM responses should no longer replace the complete JSON by default. Introduce
small, validated operations such as:

```kotlin
sealed interface CompositionMutation {
    data class UpdateHighLevelDescription(val value: String) : CompositionMutation
    data class UpdateStyleField(val field: StyleField, val value: String) : CompositionMutation
    data class UpdateGlobalPalette(val colors: List<String>) : CompositionMutation
    data class AddElement(val element: IdeogramElement) : CompositionMutation
    data class UpdateElement(val index: Int, val patch: ElementPatch) : CompositionMutation
    data class SetElementBounds(val index: Int, val bounds: IdeogramBounds?) : CompositionMutation
    data class RemoveElement(val index: Int) : CompositionMutation
    data class UpdateBackground(val description: String) : CompositionMutation
    data class UpdateStyle(val patch: StylePatch) : CompositionMutation
    data class UpdateComposition(val patch: CompositionPatch) : CompositionMutation
    data class UpdateExtensionField(val path: JsonPath, val value: JsonElement) : CompositionMutation
}
```

The ViewModel applies these operations to a parsed document, validates the
result, and serializes it back to JSON. The UI, focused LLM actions, and the
assistant all use this single mutation path.

Every semantic field and structured section receives an LLM `Improve` action:

- A text field rewrites only that value.
- A palette updates only its colors.
- A bounding box improves only its placement in the context of other elements.
- An element can be improved as a whole.
- `style_description` can be improved as one coherent `StylePatch`.
- `compositional_deconstruction` can be improved as a `CompositionPatch` that
  considers the background and element layout together.
- An unknown additional field can be improved using its JSON path and current
  JSON type.
- The complete document can still be improved when explicitly requested.

Each action receives enough document context for consistency, but asks the LLM
for the smallest possible patch for its target. Validate and apply the result
immediately, then store it as exactly one undo/redo version.

### 3a. Caption Profiles and Canonical Serialization

The files in `reference/` describe two related but different contracts and must
not be merged implicitly:

- `reference/prompting.md` documents the Ideogram 4 training caption schema.
  This is the canonical document and backend serialization profile.
- `reference/v1.txt` is a focused Magic Prompt system prompt. It emits
  `aspect_ratio`, `high_level_description`, and
  `compositional_deconstruction`, intentionally omitting `style_description`.
  Use its content-planning rules as prompt guidance, or support it through an
  explicit adapter profile; do not treat its output contract as the canonical
  Ideogram document schema.

The canonical serializer must produce deterministic schema order because the
Ideogram verifier and training distribution are order-sensitive:

1. Top level: `high_level_description`, `style_description`,
   `compositional_deconstruction`.
2. Photo style: `aesthetics`, `lighting`, `photo`, `medium`, then optional
   `color_palette`.
3. Art style: `aesthetics`, `lighting`, `medium`, `art_style`, then optional
   `color_palette`.
4. Composition: `background`, then `elements`.
5. Object element: `type`, optional `bbox`, `desc`, optional `color_palette`.
6. Text element: `type`, optional `bbox`, `text`, `desc`, optional
   `color_palette`.

The backend payload uses compact JSON with literal Unicode characters. Pretty
JSON remains a UI concern for the expert editor. Parsing may preserve unknown
fields for lossless manual editing, but validation must surface that unknown
fields are outside the documented training schema. Canonical backend
serialization needs a deliberate extension policy rather than relying on
arbitrary `JsonObject` insertion order.

`high_level_description` and `style_description` are optional according to the
official schema, although the high-level description is strongly recommended.
`compositional_deconstruction`, `background`, and `elements` are required.
Validation should match that distinction instead of making every recommended
field structurally mandatory.

The LLM layer exposes explicit caption-generation profiles:

- **Canonical Ideogram caption**: generates the full official schema and is the
  default for the structured editor.
- **Slim Magic Prompt adapter**: accepts the `v1.txt` output, removes or consumes
  `aspect_ratio` as generation metadata, and maps the result into the canonical
  document without silently deleting an existing style section.

The semantic rules from `v1.txt` should inform future role prompts: background
is the scene shell, individually placeable subjects are elements, ground and
atmospheric context stay in the background, one coherent subject is one object,
literal text is represented by text elements, bounding boxes are optional and
aspect-ratio-aware, and descriptions commit to concrete choices.

### 4. Maintain Complete Composition History

Version every composition change regardless of its source:

- manual text, description, and color edits,
- adding or removing elements,
- moving and resizing bounding boxes,
- raw JSON edits,
- field-, section-, and object-level LLM actions,
- assistant tool calls.

Apply validated LLM and assistant changes immediately. There is no preview or
confirmation dialog. Safety comes from one reliable undo/redo history with
buttons matching the existing prompt history controls.

Interactions need meaningful transaction boundaries. A complete drag or resize
gesture creates exactly one history entry even though the canvas updates live.
Text inputs commit on focus loss, Enter/Apply, or a suitable debounce rather
than on every keystroke. Each LLM response or assistant tool call is one atomic
mutation. A new mutation discards the redo branch.

### 5. Use Stable Element Identity

Ideogram JSON does not require element IDs. Array indexes can remain as a short
term implementation detail, but undo/redo, concurrent LLM actions, and richer
tool calls should use an internal document model with stable UI IDs. These IDs
do not need to be serialized into the JSON sent to Ideogram.

### 6. Expose Tool Calls Through a Controlled Registry

The assistant must not invoke arbitrary ViewModel functions. A
`CompositionToolRegistry` defines tool names, JSON schemas, execution, and
results. Arguments are parsed and validated strictly. Planned tools include:

- `get_composition_summary`
- `update_high_level_description`
- `update_style_field`
- `add_composition_element`
- `update_composition_element`
- `remove_composition_element`
- `update_composition_background`
- `update_composition_style`
- `update_compositional_deconstruction`
- `update_json_field`
- `update_generation_settings`

The assistant explicitly does not receive a tool for starting image generation.
Only the user may trigger generation. Valid mutating tool calls execute
immediately and are stored as atomic entries in the same composition history.

Build the available tool list for each assistant turn from the active prompt
mode and composition schema. Ideogram-specific tools are available only when an
Ideogram-compatible structured mode is active. A classic image model may still
receive experimental JSON prompts, but that does not automatically enable
Ideogram tools. A future explicit schema or capability profile may enable them
for other models.

## Implementation Phases

## Phase 1: Right-Side Composition and Compact Progress

### UI Changes

- Make `PromptTabContent` show status and the complete structured Ideogram
  editor in Composition mode.
- Add dedicated sections for Overview, Style, Composition/Background, and the
  selected element.
- Give each known editable field and structured section a contextual `Improve`
  action.
- Keep unknown existing JSON fields editable through an Additional Fields
  editor.
- Extend `PreviewPanel` into `PreviewCanvasHost`.
- Show the composition in the right host while Composition mode is active.
- When an image exists, show it and provide a `Show composition` overlay toggle.
- Store the overlay toggle in desktop settings with a default of enabled. A
  generation event must not change it.
- Synchronize selection between the left element list and right canvas.
- Preserve bounding-box dragging and resizing.
- Show a quiet empty state for empty or invalid JSON.

### Remove Batch Generation

- Remove `batchCount` from the Compose UI, `GenerationUiState`, persisted
  generation settings, and request construction.
- Normalize preset and gallery parameter reuse to one image.
- Do not remove the queue for separately started generations or Endless
  Generation; these are separate features.
- Send a fixed request value of `1` if the native API field must remain for
  compatibility.

### Progress

- Remove `ProgressCard`, `ProgressStageList`, and the large right-side progress
  view from the normal generation flow.
- Add a flexible compact progress area to `ActionBar` while generation runs.
- At narrow widths, reduce detail text first while keeping Generate and Stop
  accessible.
- Keep the last image or composition visible during generation instead of
  replacing it with a progress card.

### Acceptance Criteria

- The composition uses the configured output aspect ratio.
- The overlay and single image have identical bounds at every window size.
- Width and height changes update the pre-generation composition immediately.
- Box selection, movement, and resizing work on the right canvas.
- Every Ideogram JSON value is individually editable outside the raw JSON tab.
  Known fields have dedicated controls; unknown fields use the Additional
  Fields editor.
- Global and element palettes use the same swatch editor with edit, add, and
  remove actions and no duplicate text list.
- Bounding boxes are optional, displayed as `X/Y/W/H`, and primarily edited on
  the right canvas rather than through raw coordinate text.
- JSON sent to Ideogram follows canonical schema key order and compact Unicode-
  preserving serialization.
- Every field, section, and element can be improved through the LLM without
  regenerating unrelated document areas.
- Progress works with both top and bottom Action Bar placement.
- The overlay is enabled on first use and then preserves the user's choice
  across generations and application restarts.
- The Compose app no longer offers batch count. Queue and Endless Generation
  continue to work.

## Phase 2: Typed Composition Document and Undo/Redo

- Parse Ideogram JSON into a typed `IdeogramCompositionDocument`.
- Define a clear source-of-truth relationship between raw JSON and document
  state.
- Add controls for `high_level_description`, every known style field, global
  palette, background, and all element fields.
- Replace duplicate palette representations with the shared swatch editor,
  including add/remove behavior and schema-specific limits.
- Remove raw bbox text editing from the structured editor. Show `X/Y/W/H`, edit
  on the canvas, and support optional add/remove-box mutations.
- Route all field editors and the Additional Fields editor through
  `CompositionMutation`.
- Add a canonical order-aware serializer and a separate compact backend
  serializer with literal Unicode output.
- Add explicit canonical and slim Magic Prompt caption profiles/adapters.
- Centralize validation for bounding boxes, element types, text fields, and
  palettes.
- Add one undo/redo history for all manual, LLM, and assistant mutations.
- Define history transactions for drag/resize and text editing so one coherent
  user action creates one entry.
- Add Composition undo/redo buttons matching the prompt history controls.
- Preserve valid unknown JSON fields instead of losing them through overly
  narrow serialization.

Complete this phase before adding extensive LLM tools. Otherwise, the app would
have multiple fragile paths for rewriting JSON strings.

## Phase 3: Field- and Object-Level LLM Actions

Add focused actions throughout the structured editor:

- [x] **Improve field**: improve only the selected text or structured field.
- [x] **Improve style**: return only a `StylePatch`.
- [x] **Improve composition**: return a `CompositionPatch` for the background and
  element arrangement.
- [x] **Enhance description**: increase only the visual detail of `desc`.
- [x] **Regenerate element**: regenerate one element while retaining its type and,
  optionally, bounding box.
- [x] **Suggest palette**: generate only the global or element palette.
- [x] **Add object** / **Add text**: create exactly one new element from a short
  user description.

Phase 3 infrastructure status:

- [x] Route UI actions through typed `CompositionAction` commands and a shared
  `CompositionActionExecutor` that can also back future assistant tool calls.
- [x] Validate action responses and convert them into exactly one atomic,
  undoable `CompositionMutation`.
- [x] Use a separate composition completion request without changing the image
  tagging vision request.
- [x] Supply the current generated image to vision-capable composition presets.
- [x] Use the complete image for global actions and an element bounding-box crop
  with surrounding context for element-focused actions.
- [x] Fall back to text-only composition context when no current image or vision
  preset is available.

Each request includes:

- compact composition context,
- the canvas aspect ratio,
- the target JSON path, section, or element,
- an explicit JSON schema for exactly the expected patch.

Depending on the target, the LLM returns a scalar field value, `StylePatch`,
`CompositionPatch`, `ElementPatch`, or one `IdeogramElement`, never the complete
document by default. Validate and apply the result immediately as one atomic,
undoable history version.

Do not generate every object without context. The LLM should edit one target at
a time while receiving a compact list of the other elements, background, style,
and bounding boxes. This increases detail without losing composition and visual
consistency.

## Phase 4: Incremental Full Composition Generation

Keep the existing one-shot generation as a fast mode and add an optional staged
mode:

1. Generate the high-level description and style.
2. Generate the background.
3. Generate a concise element plan.
4. Detail each element individually with full context.
5. Validate bounding boxes and overlaps.
6. Validate and format the complete document.

The pipeline should expose a visible draft rather than waiting until every step
finishes. Failed steps must be retryable without regenerating already accepted
elements.

Benchmark whether several short structured calls actually outperform one large
prompt on small local models. Use a compact evaluation set covering different
scenes, typography requirements, and element counts.

## Phase 5: Compose Assistant with Composition Tools

- Add a dedicated `AssistantViewModel` and Compose panel.
- Use the existing assistant LLM role assignment from System settings.
- Represent chat history, active tool calls, failures, and cancellation.
- Implement the tool-call loop in the core: read the LLM response, execute local
  tools, return tool results, and display the final response.
- Provide a compact snapshot of the composition, generation parameters, and
  selected element.
- Display tool activity in the UI rather than applying silent side effects.
- Filter the tool list for each turn using the active prompt mode and schema
  profile.
- Do not register a `generate_image` tool. Generate remains exclusively a user
  interface action.

Use `webui/src/stores/assistant.ts` only as a behavioral reference. Do not port
it directly. The Compose implementation must use typed tool arguments,
controlled mutations, and undo/redo.

## State and Service Extensions

### `GenerationUiState` / `IdeogramUiState`

Expected additions include:

```kotlin
val selectedCompositionElementId: String? = null
val compositionDocument: IdeogramCompositionDocument? = null
val compositionParseError: String? = null
val activeCompositionAction: CompositionActionState? = null
val canUndoComposition: Boolean = false
val canRedoComposition: Boolean = false
```

Selection must not exist only as local canvas `remember` state because the left
editor, right canvas, and assistant all need it.

Store `showCompositionOverlay` as a persistent desktop preference with a
default of `true`, not as transient state reset for each generation.

### `LlmRoleService`

Keep `generateIdeogramJsonPrompt` initially and add smaller use cases:

- `generateCompositionSkeleton(...)`
- `generateCompositionElement(...)`
- `enhanceCompositionElement(...)`
- `enhanceIdeogramField(...)`
- `enhanceStyleDescription(...)`
- `enhanceCompositionalDeconstruction(...)`
- `generateElementPalette(...)`
- `runCompositionAssistantTurn(...)`

Prompts, token budgets, thinking mode, and JSON schemas should be configurable
per action. Internal defaults are sufficient initially; later, expose them as
profiles managed per LLM role. Tool availability is a separate concern and is
determined by the active prompt mode or schema/capability profile.

### Chat Client

The current chat path primarily returns text. The assistant needs structured
responses containing:

- assistant text,
- `tool_calls` with ID, name, and arguments,
- tool messages with matching call IDs,
- optionally token usage and finish reason.

Keep this extension compatible with simple text completion calls.

## Validation and Tests

### Unit Tests

- Canvas fit calculations for landscape, portrait, and square formats.
- Mapping between normalized `0..1000` bounding boxes and pixel rectangles.
- Move/resize behavior, including grid snapping and boundary constraints.
- JSON parsing, round trips, and preservation of unknown fields.
- Canonical key order for photo, art-style, object, and text captions.
- Compact backend serialization preserves literal non-ASCII text.
- Slim Magic Prompt output adapts into the canonical document without confusing
  `aspect_ratio` with an Ideogram caption field.
- Editing every known top-level, style, background, and element field.
- Adding/removing optional fields and switching between `photo` and `art_style`
  while maintaining a valid schema.
- Adding, editing, and removing global and element palette colors at their
  respective limits.
- Adding and removing optional bounding boxes and converting
  `[y_min, x_min, y_max, x_max]` to `X/Y/W/H` correctly.
- Generic editing of unknown Additional Fields by JSON path.
- Every `CompositionMutation`, including invalid indexes and patches.
- History behavior for manual, LLM, and assistant mutations, including dropping
  the redo branch after a new change.
- One drag or resize gesture creates exactly one history entry.
- Tool argument parsing and rejection of invalid tool calls.
- Tool registry filtering for incompatible prompt modes.
- Field-level LLM responses can only modify the requested path or patch scope.
- Staged LLM generation with fake responses and partial failures.

### Compose and Integration Tests

- Selection synchronization between the element list and canvas.
- Every known Ideogram field is accessible in the structured editor and stays
  bidirectionally synchronized with the raw JSON tab.
- `Improve` at field, style, composition, and element level creates exactly one
  history entry.
- Overlay persistence across multiple generations and ViewModel/settings-store
  restarts.
- Dimension changes resize the canvas without changing normalized boxes.
- Action Bar progress at top and bottom placement and narrow widths.
- Manual edits and assistant tool calls apply immediately and remain navigable
  in the correct order through undo/redo.

### Manual Verification

- Test 1:1, 16:9, 9:16, and an unusual custom aspect ratio.
- Generate several single images with the persistent overlay enabled and
  disabled.
- Test JSON containing object, text, and invalid elements.
- Test small and large local LLMs with and without native tool-call support.

## Recommended Pull Request Order

1. Extract and unit-test canvas geometry.
2. Remove batch count from the Compose app.
3. Move the Composition canvas right, keep the editor left, synchronize
   selection, and add the persistent overlay toggle.
4. Move compact progress into the Action Bar.
5. Add the typed document, complete mutation history, and undo/redo.
6. Add focused field, section, and element Improve/Add actions.
7. Add optional staged JSON generation.
8. Add the Assistant panel, filtered tool registry, and role-based prompt
   profiles.

The first four items form a useful standalone UI feature. Later work builds on
the stabilized mutation layer and can be iterated independently.

## Confirmed Product Decisions

1. The Composition overlay is enabled by default on first use. Its toggle is a
   persistent user preference and is not reset by generation.
2. Remove batch generation completely from the Compose app. Keep Queue and
   Endless Generation.
3. Apply LLM and assistant mutations immediately. Every user, LLM, and assistant
   change enters one shared undo/redo history.
4. The assistant cannot start image generation and receives no corresponding
   tool.
5. Manage prompt profiles per LLM role.
6. Offer Ideogram-specific tools only for a compatible active prompt mode or
   schema profile. Experimental JSON prompting for classic models remains
   possible but does not implicitly enable these tools.
7. The Composition tab represents the complete Ideogram JSON as a structured
   editor. Every field is individually editable and can be improved by the LLM;
   unknown fields remain accessible through a generic editor.

## Recommended Initial Delivery

Implement batch removal, Phase 1, and the selection/history foundations from
Phase 2 together. This immediately delivers the additional editing space,
correct persistent overlay, less dominant progress UI, and a reliable undo/redo
foundation without prematurely coupling the later LLM architecture to the UI
layout work.
