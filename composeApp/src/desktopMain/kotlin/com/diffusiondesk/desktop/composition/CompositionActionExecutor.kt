package com.diffusiondesk.desktop.composition

import com.diffusiondesk.desktop.core.DesktopSettings
import com.diffusiondesk.desktop.core.GeneratedImage
import com.diffusiondesk.desktop.core.LlmPreset
import com.diffusiondesk.desktop.core.LlmRoleService
import com.diffusiondesk.desktop.core.LlmRoleSettings
import com.diffusiondesk.desktop.viewmodel.CompositionImproveTarget
import com.diffusiondesk.desktop.viewmodel.CompositionMutation
import com.diffusiondesk.desktop.viewmodel.IdeogramCompositionDocument
import com.diffusiondesk.desktop.viewmodel.IdeogramStyleField
import com.diffusiondesk.desktop.viewmodel.serializeForBackend
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        }
    }

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
