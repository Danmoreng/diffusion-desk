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
import com.diffusiondesk.desktop.viewmodel.serializeForBackend
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
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
    data class ImprovePlacement(val index: Int) : CompositionAction {
        override val actionId = "improve:compositional_deconstruction.elements[$index].bbox"
    }
    data class AddElement(val type: String, val description: String) : CompositionAction {
        override val actionId = "add:compositional_deconstruction.elements:$type"
    }
    data class DeleteElement(val index: Int) : CompositionAction {
        override val actionId = "delete:compositional_deconstruction.elements[$index]"
    }
}

sealed interface PaletteTarget {
    data object Global : PaletteTarget
    data class Element(val index: Int) : PaletteTarget
}

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
            is CompositionAction.ImprovePlacement -> improvePlacement(settings, presets, roles, action.index, document, width, height, imageInput)
            is CompositionAction.AddElement -> addElement(settings, presets, roles, action.type, action.description, document, width, height, imageInput)
            is CompositionAction.DeleteElement -> deleteElement(settings, presets, roles, action.index, document, width, height, imageInput)
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
                appendLine("Regenerate element index $index as a genuinely different visual alternative.")
                appendLine("Required type: ${current.type}.")
                appendLine("Current description that must not be repeated or lightly paraphrased: ${current.description}")
                appendLine("Current palette that should be replaced with a different fitting palette: ${current.colorPalette}")
                if (current.type == "text") append("Keep this literal text exactly unchanged: ${current.text.orEmpty()}")
            }, 1024)
        val patch = parseElementDocumentPatch(response, current.type)
        requireUpdatedHighLevel(document.highLevelDescription, patch.highLevelDescription)
        return CompositionMutation.ReplaceElementAndHighLevel(
            index,
            patch.copy(element = prepareRegeneratedElement(current, patch.element)),
        )
    }

    private suspend fun improvePlacement(
        settings: DesktopSettings,
        presets: List<LlmPreset>,
        roles: LlmRoleSettings,
        index: Int,
        document: IdeogramCompositionDocument,
        width: Int,
        height: Int,
        imageInput: CompositionImageInput?,
    ): CompositionMutation {
        require(index in document.elements.indices) { "Element ${index + 1} is missing." }
        val response = completeAction(settings, presets, roles, PLACEMENT_SYSTEM_PROMPT, document, width, height, imageInput,
            "Improve only the bbox for element index $index.", 512)
        return CompositionMutation.UpdateElementBbox(index, parsePlacementPatch(response))
    }

    private suspend fun addElement(
        settings: DesktopSettings,
        presets: List<LlmPreset>,
        roles: LlmRoleSettings,
        type: String,
        description: String,
        document: IdeogramCompositionDocument,
        width: Int,
        height: Int,
        imageInput: CompositionImageInput?,
    ): CompositionMutation {
        require(type in setOf("obj", "text")) { "Element type must be obj or text." }
        require(description.isNotBlank()) { "Describe the new element first." }
        val response = completeAction(settings, presets, roles, ELEMENT_SYSTEM_PROMPT, document, width, height, imageInput,
            "Create exactly one new $type element from this request: ${description.trim()}", 1024)
        val patch = parseElementDocumentPatch(response, type)
        requireUpdatedHighLevel(document.highLevelDescription, patch.highLevelDescription)
        return CompositionMutation.AddElementAndHighLevel(patch)
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
        val removed = document.elements.getOrNull(index) ?: error("Element ${index + 1} is missing.")
        val response = completeAction(settings, presets, roles, DELETE_ELEMENT_SYSTEM_PROMPT, document, width, height, imageInput,
            "Remove element index $index from the scene summary. Removed element: ${removed.description}", 512)
        val highLevelDescription = parseHighLevelPatch(response)
        requireUpdatedHighLevel(document.highLevelDescription, highLevelDescription)
        return CompositionMutation.RemoveElementAndUpdateHighLevel(index, highLevelDescription)
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
            is CompositionAction.ImprovePlacement -> index
            is CompositionAction.DeleteElement -> index
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
        private const val ELEMENT_SYSTEM_PROMPT = """
You create exactly one Ideogram 4 element and update the scene summary to include it. Return only {"high_level_description":"...","element":{...}}. The high-level description must accurately summarize the complete scene after adding the element. Object element schema: {"type":"obj","bbox":[y_min,x_min,y_max,x_max],"desc":"...","color_palette":["#RRGGBB"]}. Text element schema: {"type":"text","bbox":[y_min,x_min,y_max,x_max],"text":"...","desc":"...","color_palette":["#RRGGBB"]}. bbox and color_palette are optional. Use no more than 5 uppercase palette colors. Do not return markdown, explanations, or additional fields.
"""
        private const val REGENERATE_ELEMENT_SYSTEM_PROMPT = """
You regenerate exactly one Ideogram 4 element as a clearly different creative alternative and update the scene summary accordingly. Return only {"high_level_description":"...","element":{...}}. The high-level description must accurately summarize the complete scene after replacing the element. Keep the requested type and, for text elements, preserve the supplied literal text exactly. Change the subject treatment, visual details, pose, shape, materials, viewpoint, or other defining characteristics substantially; do not repeat or lightly paraphrase the current description. Replace the current palette with a different fitting palette. Placement is not part of this action, so omit bbox. Object element schema: {"type":"obj","desc":"...","color_palette":["#RRGGBB"]}. Text element schema: {"type":"text","text":"...","desc":"...","color_palette":["#RRGGBB"]}. Use no more than 5 uppercase palette colors. Do not return markdown, explanations, or additional fields.
"""
        private const val DELETE_ELEMENT_SYSTEM_PROMPT = """
You remove one element from an Ideogram 4 composition summary. Return only {"high_level_description":"..."}. Rewrite the high-level description so it accurately summarizes the remaining complete scene without mentioning the removed element. Preserve unrelated scene intent and do not return markdown, explanations, or additional fields.
"""
        private const val PLACEMENT_SYSTEM_PROMPT = """
You improve exactly one Ideogram element placement. Return only {"bbox":[y_min,x_min,y_max,x_max]}. Coordinates are integers from 0 to 1000 with positive dimensions. Consider the canvas aspect ratio, other elements, and any reference image. Do not return explanations or additional fields.
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

internal fun parsePlacementPatch(response: String): List<Int> {
    val root = COMPOSITION_ACTION_JSON.parseToJsonElement(response).jsonObject
    require(root.keys == setOf("bbox")) { "The LLM placement patch must contain only bbox." }
    return parseBbox(root["bbox"])
}

private fun parseBbox(value: kotlinx.serialization.json.JsonElement?): List<Int> {
    val bbox = value?.jsonArray?.map { it.jsonPrimitive.content.toIntOrNull() ?: error("Bounding box values must be integers.") }
        ?: error("Bounding box is required.")
    require(bbox.size == 4) { "Bounding box must contain four values." }
    require(bbox.all { it in 0..1000 }) { "Bounding box values must be between 0 and 1000." }
    require(bbox[0] < bbox[2] && bbox[1] < bbox[3]) { "Bounding box must have positive dimensions." }
    return bbox
}

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

private val COMPOSITION_ACTION_JSON = kotlinx.serialization.json.Json { ignoreUnknownKeys = false }
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
    require(candidate.description.normalizedForComparison() != current.description.normalizedForComparison()) {
        "The LLM returned the same element description. Try Regenerate again."
    }
    require(candidate.colorPalette.isNotEmpty() && candidate.colorPalette != current.colorPalette) {
        "The LLM returned the same or an empty element palette. Try Regenerate again."
    }
    return candidate.copy(
        bbox = current.bbox,
        text = if (current.type == "text") current.text else null,
    )
}
