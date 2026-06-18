package com.diffusiondesk.desktop.composition

import com.diffusiondesk.desktop.core.DesktopSettings
import com.diffusiondesk.desktop.core.GeneratedImage
import com.diffusiondesk.desktop.core.LlmPreset
import com.diffusiondesk.desktop.core.LlmRoleService
import com.diffusiondesk.desktop.core.LlmRoleSettings
import com.diffusiondesk.desktop.viewmodel.CompositionImproveTarget
import com.diffusiondesk.desktop.viewmodel.CompositionMutation
import com.diffusiondesk.desktop.viewmodel.IdeogramCompositionDocument
import com.diffusiondesk.desktop.viewmodel.IdeogramCompositionElement
import com.diffusiondesk.desktop.viewmodel.IdeogramCompositionPatch
import com.diffusiondesk.desktop.viewmodel.IdeogramElementDocumentPatch
import com.diffusiondesk.desktop.viewmodel.IdeogramStylePatch
import com.diffusiondesk.desktop.viewmodel.IdeogramStyleField
import com.diffusiondesk.desktop.viewmodel.applyMutation
import com.diffusiondesk.desktop.viewmodel.parseIdeogramCompositionDocument
import com.diffusiondesk.desktop.viewmodel.serializeForBackend
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.roundToInt

sealed interface CompositionAction {
    val actionId: String

    data class ImproveField(val target: CompositionImproveTarget) : CompositionAction {
        override val actionId: String = "improve:${target.actionId}"
    }

    data class SuggestPalette(val target: PaletteTarget) : CompositionAction {
        override val actionId: String = when (target) {
            PaletteTarget.Global -> "suggest:style_description.color_palette"
            is PaletteTarget.Element -> "suggest:compositional_deconstruction.elements[${target.index}].color_palette"
        }
    }

    data object ImproveStyle : CompositionAction { override val actionId = "improve:style_description" }
    data object ImproveComposition : CompositionAction { override val actionId = "improve:compositional_deconstruction" }
    data class RegenerateElement(val index: Int) : CompositionAction {
        override val actionId = "regenerate:compositional_deconstruction.elements[$index]"
    }
    data class AddElement(val type: String) : CompositionAction {
        override val actionId = "add:compositional_deconstruction.elements:$type"
    }
    data class DeleteElement(val index: Int) : CompositionAction {
        override val actionId = "delete:compositional_deconstruction.elements[$index]"
    }
    data class CaptureImageDetails(val mode: CaptureImageMode) : CompositionAction {
        override val actionId: String = "capture:image:${mode.name.lowercase()}"
    }
}

sealed interface PaletteTarget {
    data object Global : PaletteTarget
    data class Element(val index: Int) : PaletteTarget
}

enum class CaptureImageMode {
    Merge,
    Replace,
}

data class CaptureImageAvailability(
    val canCapture: Boolean,
    val reason: String?,
)

data class IdeogramCaptureCandidate(
    val sourceId: String,
    val origin: String,
    val type: String,
    val bbox: List<Int>,
    val description: String,
    val text: String?,
    val colorPalette: List<String>,
    val hint: String = "",
    val refinementStatus: String = "pending",
)

data class IdeogramCaptureGlobalResult(
    val document: IdeogramCompositionDocument,
    val candidates: List<IdeogramCaptureCandidate>,
)

class CompositionActionExecutor(
    private val llmRoleService: LlmRoleService,
) {
    suspend fun execute(
        settings: DesktopSettings,
        presets: List<LlmPreset>,
        roles: LlmRoleSettings,
        action: CompositionAction,
        document: IdeogramCompositionDocument,
        width: Int,
        height: Int,
        image: GeneratedImage?,
    ): Result<CompositionMutation> = runCatching {
        val imageInput = if (compositionPresetSupportsVision(presets, roles)) {
            action.imageInput(document, image)
        } else {
            null
        }
        when (action) {
            is CompositionAction.ImproveField -> improveField(
                settings = settings,
                presets = presets,
                roles = roles,
                target = action.target,
                document = document,
                width = width,
                height = height,
                imageInput = imageInput,
            )
            is CompositionAction.SuggestPalette -> suggestPalette(
                settings = settings,
                presets = presets,
                roles = roles,
                target = action.target,
                document = document,
                width = width,
                height = height,
                imageInput = imageInput,
            )
            CompositionAction.ImproveStyle -> improveStyle(settings, presets, roles, document, width, height, imageInput)
            CompositionAction.ImproveComposition -> improveComposition(settings, presets, roles, document, width, height, imageInput)
            is CompositionAction.RegenerateElement -> regenerateElement(settings, presets, roles, action.index, document, width, height, imageInput)
            is CompositionAction.AddElement -> addElement(action.type)
            is CompositionAction.DeleteElement -> deleteElement(settings, presets, roles, action.index, document, width, height, imageInput)
            is CompositionAction.CaptureImageDetails -> captureImageDetails(settings, presets, roles, action.mode, document, width, height, imageInput)
        }
    }

    fun captureAvailability(
        presets: List<LlmPreset>,
        roles: LlmRoleSettings,
        image: GeneratedImage?,
        resolutionModified: Boolean,
    ): CaptureImageAvailability = when {
        image == null -> CaptureImageAvailability(false, "Generate an image before capturing image details.")
        resolutionModified -> CaptureImageAvailability(false, "Current resolution no longer matches the generated image.")
        !compositionPresetSupportsVision(presets, roles) -> CaptureImageAvailability(
            false,
            "Choose a vision-capable LLM preset with an mmproj file to capture image details.",
        )
        else -> CaptureImageAvailability(true, null)
    }

    suspend fun inspectImageForCaptureReview(
        settings: DesktopSettings,
        presets: List<LlmPreset>,
        roles: LlmRoleSettings,
        document: IdeogramCompositionDocument,
        width: Int,
        height: Int,
        image: GeneratedImage,
    ): Result<IdeogramCaptureGlobalResult> = runCatching {
        require(compositionPresetSupportsVision(presets, roles)) {
            "Choose a vision-capable LLM preset with an mmproj file to capture image details."
        }
        val imageInput = CompositionImageInput(
            dataUri = image.bufferedImage.toPngDataUri(),
            description = "complete selected source image",
        )
        val response = completeAction(
            settings = settings,
            presets = presets,
            roles = roles,
            systemPrompt = CAPTURE_GLOBAL_SYSTEM_PROMPT,
            document = document,
            width = width,
            height = height,
            imageInput = imageInput,
            instruction = buildString {
                appendLine("Inspect the attached source image and return a complete Ideogram 4 JSON document.")
                appendLine("For each element, include an internal source_id such as auto-1, auto-2, and include bbox when visible.")
                appendLine("Current composition context should guide merge intent, but the returned document should describe the image.")
            },
            maxTokens = 4096,
        )
        parseCaptureGlobalResult(response)
    }

    suspend fun refineCaptureCandidate(
        settings: DesktopSettings,
        presets: List<LlmPreset>,
        roles: LlmRoleSettings,
        globalSummary: String,
        candidate: IdeogramCaptureCandidate,
        sourceImage: GeneratedImage,
    ): Result<IdeogramCaptureCandidate> = runCatching {
        require(compositionPresetSupportsVision(presets, roles)) {
            "Choose a vision-capable LLM preset with an mmproj file to capture image details."
        }
        require(candidate.bbox.size == 4) { "Candidate ${candidate.sourceId} needs a valid bounding box." }
        val crop = sourceImage.bufferedImage.cropIdeogramBbox(candidate.bbox, CROP_CONTEXT_FRACTION)
        val response = llmRoleService.completeIdeogramCompositionAction(
            settings = settings,
            presets = presets,
            roles = roles,
            systemPrompt = CAPTURE_REFINE_SYSTEM_PROMPT,
            userPrompt = buildString {
                appendLine("Source id: ${candidate.sourceId}")
                appendLine("Global image summary: $globalSummary")
                appendLine("Original full-image normalized bbox: ${candidate.bbox}")
                appendLine("Candidate type: ${candidate.type}")
                appendLine("Candidate coarse description: ${candidate.description.ifBlank { "(none)" }}")
                candidate.text?.takeIf(String::isNotBlank)?.let { appendLine("Candidate readable text: $it") }
                candidate.hint.takeIf(String::isNotBlank)?.let { appendLine("User hint: $it") }
                append("Return one refined element JSON object only.")
            },
            imageDataUri = crop.toPngDataUri(),
            maxTokens = 1024,
        ).getOrThrow()
        parseCaptureRefinement(response, candidate)
    }

    suspend fun finalizeCapturedComposition(
        settings: DesktopSettings,
        presets: List<LlmPreset>,
        roles: LlmRoleSettings,
        document: IdeogramCompositionDocument,
        width: Int,
        height: Int,
        image: GeneratedImage,
    ): Result<IdeogramCompositionDocument> = runCatching {
        require(compositionPresetSupportsVision(presets, roles)) {
            "Choose a vision-capable LLM preset with an mmproj file to finalize image capture."
        }
        val imageInput = CompositionImageInput(
            dataUri = image.bufferedImage.toPngDataUri(),
            description = "complete selected source image after per-element crop refinement",
        )
        val elementPalettes = document.elements
            .mapIndexedNotNull { index, element ->
                element.colorPalette.takeIf { it.isNotEmpty() }?.let { "element ${index + 1}: $it" }
            }
            .joinToString("\n")
            .ifBlank { "(no element palettes provided)" }
        val response = completeAction(
            settings = settings,
            presets = presets,
            roles = roles,
            systemPrompt = CAPTURE_FINALIZE_SYSTEM_PROMPT,
            document = document,
            width = width,
            height = height,
            imageInput = imageInput,
            instruction = buildString {
                appendLine("Finalize the attached captured composition after crop-level element refinement.")
                appendLine("Rewrite high_level_description as one coherent full-image prompt summary.")
                appendLine("Curate style_description.color_palette from the full image and these element palettes:")
                appendLine(elementPalettes)
                appendLine("Preserve element count, order, type, readable text, bbox coordinates, and element color palettes.")
                append("Return the complete Ideogram 4 JSON document only.")
            },
            maxTokens = 4096,
        )
        val finalized = parseCapturedDocument(response)
        require(finalized.elements.size == document.elements.size) {
            "The final pass changed the element count."
        }
        document.elements.foldIndexed(finalized) { index, currentDocument, element ->
            currentDocument.applyMutation(CompositionMutation.ReplaceElement(index, element)).getOrThrow()
        }
    }

    fun buildCaptureMutation(
        mode: CaptureImageMode,
        currentDocument: IdeogramCompositionDocument,
        globalDocument: IdeogramCompositionDocument,
        candidates: List<IdeogramCaptureCandidate>,
    ): CompositionMutation {
        val refinedDocument = documentFromCandidates(globalDocument, candidates)
        return when (mode) {
            CaptureImageMode.Replace -> CompositionMutation.ReplaceDocument(refinedDocument)
            CaptureImageMode.Merge -> {
                val mutations = mutableListOf<CompositionMutation>()
                if (refinedDocument.highLevelDescription.isNotBlank()) {
                    mutations += CompositionMutation.UpdateHighLevelDescription(refinedDocument.highLevelDescription)
                }
                if (refinedDocument.background.isNotBlank()) {
                    mutations += CompositionMutation.UpdateBackground(refinedDocument.background)
                }
                refinedDocument.elements.forEach { element ->
                    if (currentDocument.elements.none { it.identitySignature() == element.identitySignature() }) {
                        mutations += CompositionMutation.AddGeneratedElement(element)
                    }
                }
                CompositionMutation.Batch(mutations.ifEmpty {
                    listOf(CompositionMutation.UpdateHighLevelDescription(refinedDocument.highLevelDescription))
                })
            }
        }
    }

    private suspend fun improveStyle(
        settings: DesktopSettings,
        presets: List<LlmPreset>,
        roles: LlmRoleSettings,
        document: IdeogramCompositionDocument,
        width: Int,
        height: Int,
        imageInput: CompositionImageInput?,
    ): CompositionMutation {
        val modeKey = if (document.style.photo != null) "photo" else "art_style"
        val response = completeAction(settings, presets, roles, STYLE_SYSTEM_PROMPT, document, width, height, imageInput,
            "Keep the current style mode: $modeKey. Return all style fields and optional color_palette.", 1024)
        return CompositionMutation.ReplaceStyle(parseStylePatch(response, modeKey))
    }

    private suspend fun improveComposition(
        settings: DesktopSettings,
        presets: List<LlmPreset>,
        roles: LlmRoleSettings,
        document: IdeogramCompositionDocument,
        width: Int,
        height: Int,
        imageInput: CompositionImageInput?,
    ): CompositionMutation {
        val response = completeAction(settings, presets, roles, COMPOSITION_SYSTEM_PROMPT, document, width, height, imageInput,
            "Return exactly ${document.elements.size} placements with zero-based indexes in their existing order.", 1536)
        return CompositionMutation.ReplaceComposition(parseCompositionPatch(response, document.elements.size))
    }

    private suspend fun regenerateElement(
        settings: DesktopSettings,
        presets: List<LlmPreset>,
        roles: LlmRoleSettings,
        index: Int,
        document: IdeogramCompositionDocument,
        width: Int,
        height: Int,
        imageInput: CompositionImageInput?,
    ): CompositionMutation {
        val current = document.elements.getOrNull(index) ?: error("Element ${index + 1} is missing.")
        val response = completeAction(settings, presets, roles, REGENERATE_ELEMENT_SYSTEM_PROMPT, document, width, height, imageInput,
            buildString {
                appendLine("Generate element index $index for its existing slot.")
                appendLine("Required type: ${current.type}.")
                appendLine("Current bbox to preserve in the app: ${current.bbox}")
                if (current.description.isBlank()) {
                    appendLine("The current description is empty; create a fitting new element from the surrounding scene and placement.")
                } else {
                    appendLine("Current description that must not be repeated or lightly paraphrased: ${current.description}")
                }
                if (current.colorPalette.isNotEmpty()) {
                    appendLine("Current palette that should be replaced with a different fitting palette: ${current.colorPalette}")
                }
                if (current.type == "text" && !current.text.isNullOrBlank()) {
                    append("Keep this literal text exactly unchanged: ${current.text}")
                } else if (current.type == "text") {
                    append("Create fitting literal text for this text element.")
                }
            }, 1024)
        val element = parseElementPatch(response, current.type)
        return CompositionMutation.ReplaceElement(
            index,
            prepareRegeneratedElement(current, element),
        )
    }

    private fun addElement(
        type: String,
    ): CompositionMutation {
        require(type in setOf("obj", "text")) { "Element type must be obj or text." }
        return CompositionMutation.AddElement(type)
    }

    private suspend fun deleteElement(
        settings: DesktopSettings,
        presets: List<LlmPreset>,
        roles: LlmRoleSettings,
        index: Int,
        document: IdeogramCompositionDocument,
        width: Int,
        height: Int,
        imageInput: CompositionImageInput?,
    ): CompositionMutation {
        document.elements.getOrNull(index) ?: error("Element ${index + 1} is missing.")
        return CompositionMutation.RemoveElement(index)
    }

    private suspend fun captureImageDetails(
        settings: DesktopSettings,
        presets: List<LlmPreset>,
        roles: LlmRoleSettings,
        mode: CaptureImageMode,
        document: IdeogramCompositionDocument,
        width: Int,
        height: Int,
        imageInput: CompositionImageInput?,
    ): CompositionMutation {
        require(imageInput != null) {
            "Choose a vision-capable LLM preset with an mmproj file to capture image details."
        }
        return when (mode) {
            CaptureImageMode.Replace -> {
                val response = completeAction(
                    settings = settings,
                    presets = presets,
                    roles = roles,
                    systemPrompt = CAPTURE_REPLACE_SYSTEM_PROMPT,
                    document = document,
                    width = width,
                    height = height,
                    imageInput = imageInput,
                    instruction = buildString {
                        appendLine("Create a complete editable Ideogram 4 JSON document from the attached generated image.")
                        appendLine("Use the current prompt only as context: ${document.highLevelDescription}")
                    },
                    maxTokens = 4096,
                )
                CompositionMutation.ReplaceDocument(parseCapturedDocument(response))
            }
            CaptureImageMode.Merge -> {
                val response = completeAction(
                    settings = settings,
                    presets = presets,
                    roles = roles,
                    systemPrompt = CAPTURE_MERGE_SYSTEM_PROMPT,
                    document = document,
                    width = width,
                    height = height,
                    imageInput = imageInput,
                    instruction = "Merge visible details from the attached generated image into the current composition.",
                    maxTokens = 4096,
                )
                parseCapturePatch(response, document)
            }
        }
    }

    private suspend fun completeAction(
        settings: DesktopSettings,
        presets: List<LlmPreset>,
        roles: LlmRoleSettings,
        systemPrompt: String,
        document: IdeogramCompositionDocument,
        width: Int,
        height: Int,
        imageInput: CompositionImageInput?,
        instruction: String,
        maxTokens: Int,
    ): String = llmRoleService.completeIdeogramCompositionAction(
        settings = settings,
        presets = presets,
        roles = roles,
        systemPrompt = systemPrompt,
        userPrompt = buildString {
            appendLine(instruction)
            appendLine("Canvas: ${width}x$height")
            appendLine("Reference image: ${imageInput?.description ?: "not provided"}")
            append("Full composition context: ${document.serializeForBackend()}")
        },
        imageDataUri = imageInput?.dataUri,
        maxTokens = maxTokens,
    ).getOrThrow()

    private suspend fun improveField(
        settings: DesktopSettings,
        presets: List<LlmPreset>,
        roles: LlmRoleSettings,
        target: CompositionImproveTarget,
        document: IdeogramCompositionDocument,
        width: Int,
        height: Int,
        imageInput: CompositionImageInput?,
    ): CompositionMutation {
        val currentValue = document.valueFor(target)
        require(currentValue.isNotBlank()) { "The selected composition field is empty." }
        val response = llmRoleService.completeIdeogramCompositionAction(
            settings = settings,
            presets = presets,
            roles = roles,
            systemPrompt = FIELD_IMPROVEMENT_SYSTEM_PROMPT,
            userPrompt = buildString {
                appendLine("Target path: ${target.actionId}")
                appendLine("Canvas: ${width}x$height")
                appendLine("Current value: $currentValue")
                appendLine("Reference image: ${imageInput?.description ?: "not provided"}")
                append("Full composition context: ${document.serializeForBackend()}")
            },
            imageDataUri = imageInput?.dataUri,
            maxTokens = 1024,
        ).getOrThrow()
        val value = parseCompositionFieldPatch(response)
        require(value.isNotBlank()) { "The LLM returned an empty field value." }
        return mutationForImprovedValue(target, value)
    }

    private suspend fun suggestPalette(
        settings: DesktopSettings,
        presets: List<LlmPreset>,
        roles: LlmRoleSettings,
        target: PaletteTarget,
        document: IdeogramCompositionDocument,
        width: Int,
        height: Int,
        imageInput: CompositionImageInput?,
    ): CompositionMutation {
        val maxColors = if (target == PaletteTarget.Global) 16 else 5
        val targetPath = when (target) {
            PaletteTarget.Global -> "style_description.color_palette"
            is PaletteTarget.Element -> "compositional_deconstruction.elements[${target.index}].color_palette"
        }
        val response = llmRoleService.completeIdeogramCompositionAction(
            settings = settings,
            presets = presets,
            roles = roles,
            systemPrompt = PALETTE_SUGGESTION_SYSTEM_PROMPT,
            userPrompt = buildString {
                appendLine("Target path: $targetPath")
                appendLine("Canvas: ${width}x$height")
                appendLine("Maximum colors: $maxColors")
                appendLine("Reference image: ${imageInput?.description ?: "not provided"}")
                append("Full composition context: ${document.serializeForBackend()}")
            },
            imageDataUri = imageInput?.dataUri,
            maxTokens = 512,
        ).getOrThrow()
        val colors = parseCompositionPalettePatch(response, maxColors)
        require(colors.isNotEmpty()) { "The LLM returned an empty palette." }
        return when (target) {
            PaletteTarget.Global -> CompositionMutation.UpdateGlobalPalette(colors)
            is PaletteTarget.Element -> CompositionMutation.UpdateElementPalette(target.index, colors)
        }
    }

    private fun compositionPresetSupportsVision(presets: List<LlmPreset>, roles: LlmRoleSettings): Boolean {
        val presetId = roles.promptEnhancerPresetId.ifBlank { roles.assistantPresetId }
        return presets.firstOrNull { it.id == presetId }?.mmprojPath?.isNotBlank() == true
    }

    private fun CompositionAction.imageInput(
        document: IdeogramCompositionDocument,
        image: GeneratedImage?,
    ): CompositionImageInput? {
        image ?: return null
        val elementIndex = when (this) {
            is CompositionAction.ImproveField -> (target as? CompositionImproveTarget.ElementDescription)?.index
            is CompositionAction.SuggestPalette -> (target as? PaletteTarget.Element)?.index
            is CompositionAction.RegenerateElement -> index
            is CompositionAction.DeleteElement -> index
            is CompositionAction.CaptureImageDetails -> null
            CompositionAction.ImproveStyle,
            CompositionAction.ImproveComposition,
            is CompositionAction.AddElement -> null
        }
        val bbox = elementIndex?.let { document.elements.getOrNull(it)?.bbox }
        return if (bbox?.size == 4) {
            CompositionImageInput(
                dataUri = image.bufferedImage.cropIdeogramBbox(bbox, CROP_CONTEXT_FRACTION).toPngDataUri(),
                description = "cropped target element with surrounding context",
            )
        } else {
            CompositionImageInput(
                dataUri = image.bufferedImage.toPngDataUri(),
                description = "complete current generated image",
            )
        }
    }

    private data class CompositionImageInput(
        val dataUri: String,
        val description: String,
    )

    private companion object {
        private const val CROP_CONTEXT_FRACTION = 0.12
        private const val FIELD_IMPROVEMENT_SYSTEM_PROMPT = """
You improve exactly one field in an Ideogram 4 structured caption. Return only a JSON object with one string property: {"value":"..."}.

Use the full composition and any reference image only as context. Do not return a full caption, patches, explanations, markdown, or additional keys. Preserve the field's purpose and improve visual specificity, clarity, consistency, and useful detail. Do not introduce alternatives or hedging. Keep literal in-image text unchanged because text content is edited separately.
"""
        private const val PALETTE_SUGGESTION_SYSTEM_PROMPT = """
You suggest a color palette for exactly one target in an Ideogram 4 structured caption. Return only a JSON object with one property: {"colors":["#RRGGBB"]}.

Use the composition and any reference image as context. Choose visually useful, distinct colors that match the target and surrounding scene. Use uppercase six-digit hexadecimal colors. Do not return explanations, markdown, names, or additional properties. Never exceed the requested maximum number of colors.
"""
        private const val STYLE_SYSTEM_PROMPT = """
You improve only style_description in an Ideogram 4 caption. Return one JSON object containing aesthetics, lighting, medium, exactly one of photo or art_style, and optional color_palette. Do not return markdown, explanations, or unrelated fields. Use uppercase #RRGGBB palette values and no more than 16 colors.
"""
        private const val COMPOSITION_SYSTEM_PROMPT = """
You improve only the composition background and element placement. Return {"background":"...","placements":[{"index":0,"bbox":[y_min,x_min,y_max,x_max]}]}. Include every existing element exactly once. Do not alter element types, text, descriptions, palettes, or count. Coordinates are integers from 0 to 1000 with positive dimensions.
"""
        private const val REGENERATE_ELEMENT_SYSTEM_PROMPT = """
You generate exactly one Ideogram 4 element for an existing selected slot. Return only the element object. Keep the requested type. The app preserves placement separately, so omit bbox. For non-empty existing descriptions, create a clearly different creative alternative and do not repeat or lightly paraphrase the current description. For empty existing descriptions, create a fitting new element from the full composition context and selected placement. For text elements, preserve a supplied non-empty literal text exactly; otherwise create suitable literal text. Object element schema: {"type":"obj","desc":"...","color_palette":["#RRGGBB"]}. Text element schema: {"type":"text","text":"...","desc":"...","color_palette":["#RRGGBB"]}. Use 1 to 5 uppercase palette colors. Do not return markdown, explanations, or additional fields.
"""
        private const val CAPTURE_REPLACE_SYSTEM_PROMPT = """
You inspect a generated image to preserve visible composition details for a future Ideogram 4 prompt. Return JSON only: one complete Ideogram 4 structured caption document.

Capture visible objects, readable text, layout, materials, colors, spatial relationships, and background details. Bounding boxes use normalized Ideogram coordinates [y_min,x_min,y_max,x_max] from 0 to 1000. Detect text elements separately from object elements and preserve readable text exactly. Include style_description only from visible style evidence. Do not guess hidden or ambiguous details, and do not return markdown or explanations.
"""
        private const val CAPTURE_MERGE_SYSTEM_PROMPT = """
You inspect a generated image to enrich an existing Ideogram 4 structured caption. Return JSON only using this schema: {"high_level_description":"optional updated summary","background":"optional updated background","elements":[{"operation":"update","index":0,"type":"obj","bbox":[y_min,x_min,y_max,x_max],"desc":"...","text":"only for readable text elements","color_palette":["#RRGGBB"]},{"operation":"add","type":"obj","bbox":[y_min,x_min,y_max,x_max],"desc":"...","color_palette":["#RRGGBB"]}],"remove_indexes":[]}.

Keep the current high-level idea unless the image clearly contradicts it. Add visible objects missing from the current element list, enrich existing descriptions from visible detail, add or update boxes from visible layout, preserve readable rendered text exactly, and avoid inventing details not visible in the image or present in the prompt. Bounding boxes use normalized Ideogram coordinates [y_min,x_min,y_max,x_max] from 0 to 1000. Use remove_indexes sparingly.
"""
        private const val CAPTURE_GLOBAL_SYSTEM_PROMPT = """
You inspect a source image to create an editable Ideogram 4 composition. Return JSON only: one complete Ideogram 4 structured caption document.

Required shape: {"high_level_description":"...","style_description":{"aesthetics":"...","lighting":"...","medium":"...","photo":"..." or "art_style":"...","color_palette":["#RRGGBB"]},"compositional_deconstruction":{"background":"...","elements":[{"source_id":"auto-1","type":"obj","bbox":[y_min,x_min,y_max,x_max],"desc":"...","text":"only for readable text elements","color_palette":["#RRGGBB"]}]}}.

Capture the full composition first, then identify distinct important object and text regions. Bounding boxes use full-image normalized Ideogram coordinates [y_min,x_min,y_max,x_max] from 0 to 1000. Preserve readable text exactly. Avoid guessing hidden or ambiguous details. Include source_id for every element. Do not return markdown or explanations.
"""
        private const val CAPTURE_REFINE_SYSTEM_PROMPT = """
You refine one Ideogram 4 element using a crop from the source image. Return JSON only using this shape: {"source_id":"...","type":"obj","bbox":[y_min,x_min,y_max,x_max],"desc":"detailed crop-observed description","text":"only for readable text elements","color_palette":["#RRGGBB"]}.

The bbox must remain in full-image normalized coordinates, not crop-local coordinates. You may slightly correct the bbox if the crop makes the object bounds clearer. Describe only what is visible in this crop plus the provided global context. Preserve readable text exactly. Use uppercase #RRGGBB colors, no more than 5. Do not return markdown or explanations.
"""
        private const val CAPTURE_FINALIZE_SYSTEM_PROMPT = """
You finalize an Ideogram 4 structured caption after source-image analysis. Return JSON only: one complete Ideogram 4 structured caption document.

Improve only the global coherence: high_level_description and style_description, especially style_description.color_palette. The global palette should be curated from the full image and the element palettes, but it must be a concise overall image palette rather than a raw concatenation. Preserve all element count, order, types, readable text, bounding boxes, and element color palettes. Use uppercase #RRGGBB values, no more than 16 global palette colors. Do not return markdown or explanations.
"""
    }
}

internal fun mutationForImprovedValue(
    target: CompositionImproveTarget,
    value: String,
): CompositionMutation = when (target) {
    CompositionImproveTarget.HighLevelDescription -> CompositionMutation.UpdateHighLevelDescription(value)
    is CompositionImproveTarget.StyleField -> CompositionMutation.UpdateStyleField(target.field, value)
    CompositionImproveTarget.Background -> CompositionMutation.UpdateBackground(value)
    is CompositionImproveTarget.ElementDescription -> CompositionMutation.UpdateElementDescription(target.index, value)
}

internal fun parseCompositionFieldPatch(response: String): String {
    val root = COMPOSITION_ACTION_JSON.parseToJsonElement(response).jsonObject
    require(root.keys == setOf("value")) { "The LLM field patch must contain only the value property." }
    return root["value"]?.jsonPrimitive?.content?.trim().orEmpty()
}

internal fun parseCompositionPalettePatch(response: String, maxColors: Int): List<String> {
    val root = COMPOSITION_ACTION_JSON.parseToJsonElement(response).jsonObject
    require(root.keys == setOf("colors")) { "The LLM palette patch must contain only the colors property." }
    val colors = root["colors"]?.jsonArray?.map { it.jsonPrimitive.content.trim().uppercase() }
        ?: error("The LLM palette patch must contain a colors array.")
    require(colors.size <= maxColors) { "The LLM palette contains more than $maxColors colors." }
    require(colors.all(HEX_COLOR::matches)) { "The LLM palette must use uppercase #RRGGBB colors." }
    return colors.distinct()
}

internal fun parseStylePatch(response: String, modeKey: String): IdeogramStylePatch {
    require(modeKey in setOf("photo", "art_style")) { "Unsupported style mode." }
    val root = COMPOSITION_ACTION_JSON.parseToJsonElement(response).jsonObject
    val allowed = setOf("aesthetics", "lighting", "medium", modeKey, "color_palette")
    require(root.keys.all { it in allowed }) { "The LLM style patch contains unsupported properties." }
    require(root.keys.containsAll(setOf("aesthetics", "lighting", "medium", modeKey))) { "The LLM style patch is incomplete." }
    val colors = root["color_palette"]?.jsonArray?.map { it.jsonPrimitive.content.trim().uppercase() } ?: emptyList()
    require(colors.size <= 16 && colors.all(HEX_COLOR::matches)) { "The LLM style palette is invalid." }
    fun required(key: String) = root[key]?.jsonPrimitive?.content?.trim().orEmpty().also {
        require(it.isNotBlank()) { "The LLM style patch has an empty $key value." }
    }
    return IdeogramStylePatch(
        aesthetics = required("aesthetics"),
        lighting = required("lighting"),
        medium = required("medium"),
        photo = required(modeKey).takeIf { modeKey == "photo" },
        artStyle = required(modeKey).takeIf { modeKey == "art_style" },
        colorPalette = colors.distinct(),
    )
}

internal fun parseCompositionPatch(response: String, elementCount: Int): IdeogramCompositionPatch {
    val root = COMPOSITION_ACTION_JSON.parseToJsonElement(response).jsonObject
    require(root.keys == setOf("background", "placements")) { "The LLM composition patch has invalid properties." }
    val background = root["background"]?.jsonPrimitive?.content?.trim().orEmpty()
    require(background.isNotBlank()) { "The LLM composition background is empty." }
    val placements = root["placements"]?.jsonArray ?: error("The LLM composition patch requires placements.")
    require(placements.size == elementCount) { "The LLM composition patch must place every element exactly once." }
    val byIndex = placements.associate { entry ->
        val obj = entry.jsonObject
        require(obj.keys == setOf("index", "bbox")) { "Each placement must contain only index and bbox." }
        val index = obj["index"]?.jsonPrimitive?.content?.toIntOrNull() ?: error("Placement index must be an integer.")
        index to parseBbox(obj["bbox"])
    }
    require(byIndex.size == elementCount && byIndex.keys == (0 until elementCount).toSet()) { "Placement indexes must be unique and complete." }
    return IdeogramCompositionPatch(background, (0 until elementCount).map { byIndex.getValue(it) })
}

internal fun parseElementPatch(response: String, expectedType: String): IdeogramCompositionElement {
    require(expectedType in setOf("obj", "text")) { "Unsupported element type." }
    val root = COMPOSITION_ACTION_JSON.parseToJsonElement(response).jsonObject
    val allowed = if (expectedType == "text") setOf("type", "bbox", "text", "desc", "color_palette")
        else setOf("type", "bbox", "desc", "color_palette")
    require(root.keys.all { it in allowed }) { "The LLM element contains unsupported properties." }
    require(root["type"]?.jsonPrimitive?.content == expectedType) { "The LLM changed the requested element type." }
    val description = root["desc"]?.jsonPrimitive?.content?.trim().orEmpty()
    require(description.isNotBlank()) { "The LLM element description is empty." }
    val text = root["text"]?.jsonPrimitive?.content?.trim()
    if (expectedType == "text") require(!text.isNullOrBlank()) { "The LLM text element requires literal text." }
    val colors = root["color_palette"]?.jsonArray?.map { it.jsonPrimitive.content.trim().uppercase() } ?: emptyList()
    require(colors.size <= 5 && colors.all(HEX_COLOR::matches)) { "The LLM element palette is invalid." }
    return IdeogramCompositionElement(
        type = expectedType,
        bbox = root["bbox"]?.let(::parseBbox) ?: emptyList(),
        description = description,
        text = text,
        colorPalette = colors.distinct(),
    )
}

internal fun parseElementDocumentPatch(response: String, expectedType: String): IdeogramElementDocumentPatch {
    val root = COMPOSITION_ACTION_JSON.parseToJsonElement(response).jsonObject
    require(root.keys == setOf("high_level_description", "element")) {
        "The LLM element document patch must contain only high_level_description and element."
    }
    val highLevelDescription = root["high_level_description"]?.jsonPrimitive?.content?.trim().orEmpty()
    require(highLevelDescription.isNotBlank()) { "The LLM returned an empty high-level description." }
    val elementJson = root["element"]?.let {
        COMPOSITION_ACTION_JSON.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), it)
    } ?: error("The LLM element document patch requires an element.")
    return IdeogramElementDocumentPatch(highLevelDescription, parseElementPatch(elementJson, expectedType))
}

internal fun parseHighLevelPatch(response: String): String {
    val root = COMPOSITION_ACTION_JSON.parseToJsonElement(response).jsonObject
    require(root.keys == setOf("high_level_description")) {
        "The LLM high-level patch must contain only high_level_description."
    }
    return root["high_level_description"]?.jsonPrimitive?.content?.trim().orEmpty().also {
        require(it.isNotBlank()) { "The LLM returned an empty high-level description." }
    }
}

internal fun parseCapturedDocument(response: String): IdeogramCompositionDocument {
    val json = extractJsonObject(response) ?: error("The LLM did not return a JSON object.")
    val root = COMPOSITION_ACTION_JSON.parseToJsonElement(json).jsonObject
    val normalized = normalizeCapturedRoot(root)
    return parseIdeogramCompositionDocument(
        CAPTURE_JSON_PRETTY.encodeToString(JsonElement.serializer(), normalized),
    ).getOrThrow()
}

internal fun parseCaptureGlobalResult(response: String): IdeogramCaptureGlobalResult {
    val json = extractJsonObject(response) ?: error("The LLM did not return a JSON object.")
    val root = COMPOSITION_ACTION_JSON.parseToJsonElement(json).jsonObject
    val composition = root["compositional_deconstruction"] as? JsonObject
    val elements = composition?.get("elements") as? JsonArray
    val candidates = elements
        ?.mapIndexedNotNull { index, element ->
            parseCaptureCandidate(element as? JsonObject ?: return@mapIndexedNotNull null, fallbackId = "auto-${index + 1}", origin = "auto")
        }
        .orEmpty()
    val normalized = normalizeCapturedRoot(root)
    val document = parseIdeogramCompositionDocument(
        CAPTURE_JSON_PRETTY.encodeToString(JsonElement.serializer(), normalized),
    ).getOrThrow()
    return IdeogramCaptureGlobalResult(document, candidates)
}

internal fun parseCaptureRefinement(
    response: String,
    fallback: IdeogramCaptureCandidate,
): IdeogramCaptureCandidate {
    val json = extractJsonObject(response) ?: error("The LLM did not return a JSON object.")
    val root = COMPOSITION_ACTION_JSON.parseToJsonElement(json).jsonObject
    return parseCaptureCandidate(root, fallback.sourceId, fallback.origin)?.copy(
        hint = fallback.hint,
        refinementStatus = "refined",
    ) ?: fallback.copy(refinementStatus = "failed")
}

internal fun parseCapturePatch(response: String, document: IdeogramCompositionDocument): CompositionMutation {
    val json = extractJsonObject(response) ?: error("The LLM did not return a JSON object.")
    val root = COMPOSITION_ACTION_JSON.parseToJsonElement(json).jsonObject
    val allowed = setOf("high_level_description", "background", "elements", "remove_indexes")
    require(root.keys.all { it in allowed }) { "The LLM capture patch contains unsupported properties." }

    val mutations = mutableListOf<CompositionMutation>()
    root["high_level_description"]?.jsonPrimitive?.content?.trim()
        ?.takeIf(String::isNotBlank)
        ?.let { mutations += CompositionMutation.UpdateHighLevelDescription(it) }
    root["background"]?.jsonPrimitive?.content?.trim()
        ?.takeIf(String::isNotBlank)
        ?.let { mutations += CompositionMutation.UpdateBackground(it) }

    val elements = root["elements"]?.jsonArray ?: JsonArray(emptyList())
    elements.forEach { entry ->
        val obj = entry.jsonObject
        val operation = obj["operation"]?.jsonPrimitive?.content?.trim()
            ?: error("Capture element operations require an operation.")
        val type = normalizeCaptureElementType(
            obj["type"]?.jsonPrimitive?.content?.trim(),
            obj["text"]?.jsonPrimitive?.content?.trim(),
        )
        val bbox = obj["bbox"]?.let(::parseOptionalCaptureBbox).orEmpty()
        val description = obj["desc"]?.jsonPrimitive?.content?.trim().orEmpty()
        val text = obj["text"]?.jsonPrimitive?.content?.trim()
        val palette = obj["color_palette"]?.let { parseCapturePalette(it, maxColors = 5) }.orEmpty()
        when (operation) {
            "update" -> {
                val index = obj["index"]?.jsonPrimitive?.content?.toIntOrNull()
                    ?: error("Capture update operations require index.")
                require(index in document.elements.indices) { "Capture update index ${index + 1} is missing." }
                mutations += CompositionMutation.UpdateElementType(index, type)
                if (bbox.isNotEmpty()) mutations += CompositionMutation.UpdateElementBbox(index, bbox)
                if (description.isNotBlank()) mutations += CompositionMutation.UpdateElementDescription(index, description)
                if (type == "text" && !text.isNullOrBlank()) mutations += CompositionMutation.UpdateElementText(index, text)
                if (palette.isNotEmpty()) mutations += CompositionMutation.UpdateElementPalette(index, palette)
            }
            "add" -> {
                require(!obj.containsKey("index")) { "Capture add operations must not include index." }
                require(description.isNotBlank()) { "Capture add operations require desc." }
                if (type == "text") require(!text.isNullOrBlank()) { "Capture text additions require readable text." }
                mutations += CompositionMutation.AddGeneratedElement(
                    IdeogramCompositionElement(
                        type = type,
                        bbox = bbox,
                        description = description,
                        text = text,
                        colorPalette = palette,
                    ),
                )
            }
            else -> error("Capture operation must be update or add.")
        }
    }

    val removeIndexes = root["remove_indexes"]?.jsonArray
        ?.mapNotNull { it.jsonPrimitive.content.toIntOrNull() }
        .orEmpty()
        .distinct()
        .sortedDescending()
    removeIndexes.forEach { index ->
        require(index in document.elements.indices) { "Capture remove index ${index + 1} is missing." }
        mutations += CompositionMutation.RemoveElement(index)
    }
    require(mutations.isNotEmpty()) { "The LLM did not return any capture changes." }
    return CompositionMutation.Batch(mutations)
}

private fun documentFromCandidates(
    globalDocument: IdeogramCompositionDocument,
    candidates: List<IdeogramCaptureCandidate>,
): IdeogramCompositionDocument {
    val elements = candidates
        .filter { it.bbox.size == 4 || it.description.isNotBlank() || it.type == "text" }
        .map {
            IdeogramCompositionElement(
                type = it.type,
                bbox = it.bbox,
                description = it.description.ifBlank { it.hint.ifBlank { "Visible ${it.type} element." } },
                text = it.text,
                colorPalette = it.colorPalette,
            )
        }
    val root = globalDocument.source.toMutableMap()
    val composition = root["compositional_deconstruction"]?.jsonObject?.toMutableMap() ?: mutableMapOf()
    composition["elements"] = JsonArray(elements.map(::elementToJsonObject))
    root["compositional_deconstruction"] = JsonObject(composition)
    return parseIdeogramCompositionDocument(
        CAPTURE_JSON_PRETTY.encodeToString(JsonElement.serializer(), JsonObject(root)),
    ).getOrThrow()
}

private fun elementToJsonObject(element: IdeogramCompositionElement): JsonObject = JsonObject(
    linkedMapOf<String, JsonElement>().apply {
        put("type", JsonPrimitive(element.type))
        if (element.bbox.isNotEmpty()) put("bbox", JsonArray(element.bbox.map(::JsonPrimitive)))
        if (element.type == "text") put("text", JsonPrimitive(element.text.orEmpty()))
        put("desc", JsonPrimitive(element.description))
        if (element.colorPalette.isNotEmpty()) put("color_palette", JsonArray(element.colorPalette.map(::JsonPrimitive)))
    },
)

private fun parseCaptureCandidate(
    obj: JsonObject,
    fallbackId: String,
    origin: String,
): IdeogramCaptureCandidate? {
    val text = obj["text"]?.jsonPrimitive?.content?.trim()
    val type = normalizeCaptureElementType(obj["type"]?.jsonPrimitive?.content?.trim(), text)
    val bbox = obj["bbox"]?.let(::parseOptionalCaptureBbox).orEmpty()
    if (bbox.isEmpty()) return null
    val sourceId = obj["source_id"]?.jsonPrimitive?.content?.trim()?.takeIf(String::isNotBlank) ?: fallbackId
    val description = obj["desc"]?.jsonPrimitive?.content?.trim().orEmpty()
    val palette = obj["color_palette"]?.let { parseCapturePalette(it, 5) }.orEmpty()
    return IdeogramCaptureCandidate(
        sourceId = sourceId,
        origin = origin,
        type = type,
        bbox = bbox,
        description = description,
        text = text,
        colorPalette = palette,
        refinementStatus = "pending",
    )
}

private fun parseBbox(value: kotlinx.serialization.json.JsonElement?): List<Int> {
    val bbox = value?.jsonArray?.map { it.jsonPrimitive.content.toIntOrNull() ?: error("Bounding box values must be integers.") }
        ?: error("Bounding box is required.")
    require(bbox.size == 4) { "Bounding box must contain four values." }
    require(bbox.all { it in 0..1000 }) { "Bounding box values must be between 0 and 1000." }
    require(bbox[0] < bbox[2] && bbox[1] < bbox[3]) { "Bounding box must have positive dimensions." }
    return bbox
}

private fun normalizeCapturedRoot(root: JsonObject): JsonObject {
    val map = root.toMutableMap()
    (map["style_description"] as? JsonObject)?.let { style ->
        val styleMap = style.toMutableMap()
        styleMap["color_palette"]?.let { palette ->
            styleMap["color_palette"] = JsonArray(parseCapturePalette(palette, 16).map(::JsonPrimitive))
        }
        map["style_description"] = JsonObject(styleMap)
    }
    (map["compositional_deconstruction"] as? JsonObject)?.let { composition ->
        val compositionMap = composition.toMutableMap()
        (compositionMap["background"] as? JsonObject)?.let { background ->
            listOf("desc", "description", "text", "prompt")
                .firstNotNullOfOrNull { key -> (background[key] as? JsonPrimitive)?.content?.trim()?.takeIf(String::isNotBlank) }
                ?.let { compositionMap["background"] = JsonPrimitive(it) }
        }
        val elements = (compositionMap["elements"] as? JsonArray)?.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val elementMap = obj.toMutableMap()
            elementMap.remove("source_id")
            val normalizedType = normalizeCaptureElementType(
                (elementMap["type"] as? JsonPrimitive)?.content?.trim(),
                (elementMap["text"] as? JsonPrimitive)?.content?.trim(),
            )
            elementMap["type"] = JsonPrimitive(normalizedType)
            if (normalizedType != "text") elementMap.remove("text")
            elementMap["bbox"]?.let { bbox ->
                val normalized = parseOptionalCaptureBbox(bbox)
                if (normalized.isEmpty()) elementMap.remove("bbox")
                else elementMap["bbox"] = JsonArray(normalized.map(::JsonPrimitive))
            }
            elementMap["color_palette"]?.let { palette ->
                val normalized = parseCapturePalette(palette, 5)
                if (normalized.isEmpty()) elementMap.remove("color_palette")
                else elementMap["color_palette"] = JsonArray(normalized.map(::JsonPrimitive))
            }
            JsonObject(elementMap)
        }
        if (elements != null) compositionMap["elements"] = JsonArray(elements)
        map["compositional_deconstruction"] = JsonObject(compositionMap)
    }
    return JsonObject(map)
}

private fun normalizeCaptureElementType(rawType: String?, text: String?): String =
    if (rawType.equals("text", ignoreCase = true) && !text.isNullOrBlank()) "text" else "obj"

private fun parseOptionalCaptureBbox(value: JsonElement): List<Int> {
    val raw = runCatching {
        value.jsonArray.map { it.jsonPrimitive.content.toIntOrNull() ?: error("invalid") }
    }.getOrDefault(emptyList())
    if (raw.size != 4) return emptyList()
    val y1 = raw[0].coerceIn(0, 1000)
    val x1 = raw[1].coerceIn(0, 1000)
    val y2 = raw[2].coerceIn(0, 1000)
    val x2 = raw[3].coerceIn(0, 1000)
    val yMin = minOf(y1, y2)
    val yMax = maxOf(y1, y2)
    val xMin = minOf(x1, x2)
    val xMax = maxOf(x1, x2)
    if (yMin == yMax || xMin == xMax) return emptyList()
    return listOf(yMin, xMin, yMax, xMax)
}

private fun parseCapturePalette(value: JsonElement, maxColors: Int): List<String> =
    runCatching {
        value.jsonArray.mapNotNull { entry ->
            entry.jsonPrimitive.content.trim().uppercase().takeIf(HEX_COLOR::matches)
        }.distinct().take(maxColors)
    }.getOrDefault(emptyList())

private fun extractJsonObject(value: String): String? {
    val trimmed = value.trim()
    if (trimmed.startsWith("{") && trimmed.endsWith("}") && isJsonObject(trimmed)) return trimmed
    Regex("```(?:json)?\\s*(\\{[\\s\\S]*?})\\s*```")
        .find(trimmed)
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf(::isJsonObject)
        ?.let { return it }

    var start = -1
    var depth = 0
    var inString = false
    var escaped = false
    trimmed.forEachIndexed { index, char ->
        if (inString) {
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> inString = false
            }
            return@forEachIndexed
        }
        when (char) {
            '"' -> inString = true
            '{' -> {
                if (depth == 0) start = index
                depth++
            }
            '}' -> {
                if (depth > 0) {
                    depth--
                    if (depth == 0 && start >= 0) {
                        val candidate = trimmed.substring(start, index + 1)
                        if (isJsonObject(candidate)) return candidate
                    }
                }
            }
        }
    }
    return null
}

private fun isJsonObject(value: String): Boolean =
    runCatching { COMPOSITION_ACTION_JSON.parseToJsonElement(value).jsonObject }.isSuccess

internal fun BufferedImage.cropIdeogramBbox(bbox: List<Int>, marginFraction: Double): BufferedImage {
    require(bbox.size == 4) { "Ideogram bbox must contain four values." }
    val yMin = bbox[0].coerceIn(0, 1000) / 1000.0
    val xMin = bbox[1].coerceIn(0, 1000) / 1000.0
    val yMax = bbox[2].coerceIn(0, 1000) / 1000.0
    val xMax = bbox[3].coerceIn(0, 1000) / 1000.0
    require(yMin < yMax && xMin < xMax) { "Ideogram bbox must have positive dimensions." }
    val marginX = (xMax - xMin) * marginFraction
    val marginY = (yMax - yMin) * marginFraction
    val left = ((xMin - marginX).coerceAtLeast(0.0) * width).roundToInt().coerceIn(0, width - 1)
    val top = ((yMin - marginY).coerceAtLeast(0.0) * height).roundToInt().coerceIn(0, height - 1)
    val right = ((xMax + marginX).coerceAtMost(1.0) * width).roundToInt().coerceIn(left + 1, width)
    val bottom = ((yMax + marginY).coerceAtMost(1.0) * height).roundToInt().coerceIn(top + 1, height)
    return getSubimage(left, top, right - left, bottom - top)
}

private fun BufferedImage.toPngDataUri(): String {
    val output = ByteArrayOutputStream()
    check(ImageIO.write(this, "png", output)) { "Failed to encode composition reference image." }
    return "data:image/png;base64,${Base64.getEncoder().encodeToString(output.toByteArray())}"
}

private fun IdeogramCompositionDocument.valueFor(target: CompositionImproveTarget): String = when (target) {
    CompositionImproveTarget.HighLevelDescription -> highLevelDescription
    is CompositionImproveTarget.StyleField -> when (target.field) {
        IdeogramStyleField.Aesthetics -> style.aesthetics
        IdeogramStyleField.Lighting -> style.lighting
        IdeogramStyleField.Medium -> style.medium
        IdeogramStyleField.Photo -> style.photo.orEmpty()
        IdeogramStyleField.ArtStyle -> style.artStyle.orEmpty()
    }
    CompositionImproveTarget.Background -> background
    is CompositionImproveTarget.ElementDescription -> elements.getOrNull(target.index)?.description.orEmpty()
}

private fun IdeogramCompositionElement.identitySignature(): String =
    listOf(type, text.orEmpty(), description, bbox.joinToString(","), colorPalette.joinToString(",")).joinToString("|")

private val COMPOSITION_ACTION_JSON = kotlinx.serialization.json.Json { ignoreUnknownKeys = false }
@OptIn(ExperimentalSerializationApi::class)
private val CAPTURE_JSON_PRETTY = Json { prettyPrint = true; prettyPrintIndent = "  " }
private val HEX_COLOR = Regex("^#[0-9A-F]{6}$")

private fun String.normalizedForComparison(): String = lowercase().filter(Char::isLetterOrDigit)

internal fun requireUpdatedHighLevel(current: String, updated: String) {
    require(updated.normalizedForComparison() != current.normalizedForComparison()) {
        "The LLM did not update the high-level description. Try the action again."
    }
}

internal fun prepareRegeneratedElement(
    current: IdeogramCompositionElement,
    candidate: IdeogramCompositionElement,
): IdeogramCompositionElement {
    require(
        current.description.isBlank() ||
            candidate.description.normalizedForComparison() != current.description.normalizedForComparison(),
    ) {
        "The LLM returned the same element description. Try Regenerate again."
    }
    require(candidate.colorPalette.isNotEmpty() && (current.colorPalette.isEmpty() || candidate.colorPalette != current.colorPalette)) {
        "The LLM returned the same or an empty element palette. Try Regenerate again."
    }
    return candidate.copy(
        bbox = current.bbox,
        text = if (current.type == "text" && !current.text.isNullOrBlank()) current.text else candidate.text,
    )
}
