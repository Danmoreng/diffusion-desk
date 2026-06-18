package com.diffusiondesk.desktop.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

data class AssistantToolContext(
    val promptMode: String,
    val hasStructuredComposition: Boolean,
    val hasSelectedCompositionElement: Boolean,
)

object AssistantToolRegistry {
    fun toolsFor(context: AssistantToolContext): JsonArray {
        val baseTools = mutableListOf(
            setPrompt,
            enhancePrompt,
            setNegativePrompt,
            clearNegativePrompt,
            inspectLatestImage,
            setImageSize,
            setPortrait,
            setLandscape,
            setSquare,
            setSteps,
            setCfgScale,
            setSeed,
            randomizeSeed,
            setSampler,
            updateGenerationSettings,
        )

        if (context.promptMode.equals("JSON", ignoreCase = true)) {
            if (context.hasStructuredComposition) {
                baseTools += replaceStructuredPrompt
                baseTools += updateHighLevelDescription
                baseTools += updateStyleField
                baseTools += updateCompositionBackground
                baseTools += updateJsonField
                baseTools += improveHighLevel
                baseTools += improveStyle
                baseTools += improveComposition
                baseTools += improveBackground
                baseTools += suggestGlobalPalette
                baseTools += addObject
                baseTools += addText
                if (context.hasSelectedCompositionElement) {
                    baseTools += updateSelectedElementDescription
                    baseTools += updateSelectedElementText
                    baseTools += updateSelectedElementBbox
                    baseTools += improveSelectedElement
                    baseTools += suggestSelectedElementPalette
                    baseTools += regenerateSelectedElement
                    baseTools += deleteSelectedElement
                }
            } else {
                baseTools += generateStructuredPrompt
            }
        }

        return JsonArray(baseTools)
    }

    private val setPrompt = functionTool(
        name = "set_prompt",
        description = "Replace the normal Prompt field.",
        properties = mapOf("prompt" to stringSchema("The complete image prompt to put into the Prompt field.")),
        required = listOf("prompt"),
    )
    private val enhancePrompt = functionTool("enhance_prompt", "Improve the current normal Prompt field using the app's prompt enhancer.")
    private val setNegativePrompt = functionTool(
        name = "set_negative_prompt",
        description = "Replace the Negative Prompt field.",
        properties = mapOf("negative_prompt" to stringSchema("The negative prompt text.")),
        required = listOf("negative_prompt"),
    )
    private val clearNegativePrompt = functionTool("clear_negative_prompt", "Clear the Negative Prompt field.")
    private val inspectLatestImage = functionTool("inspect_latest_image", "Inspect the latest generated image in the assistant chat.")
    private val generateStructuredPrompt = functionTool(
        name = "generate_structured_prompt",
        description = "Create the first full structured Ideogram prompt from the current normal prompt. Available only when no structured composition exists yet.",
    )
    private val replaceStructuredPrompt = functionTool(
        name = "replace_structured_prompt",
        description = "Replace the existing structured Ideogram prompt because the user is intentionally switching to a completely different image concept. Do not use for incremental edits.",
    )
    private val updateHighLevelDescription = functionTool(
        name = "update_high_level_description",
        description = "Set the high-level description field to an exact value without regenerating the composition.",
        properties = mapOf("value" to stringSchema("The new high-level description.")),
        required = listOf("value"),
    )
    private val updateStyleField = functionTool(
        name = "update_style_field",
        description = "Set one style_description text field exactly. Use for targeted style edits instead of regenerating the full composition.",
        properties = mapOf(
            "field" to enumSchema("Style field to update.", listOf("aesthetics", "lighting", "medium", "photo", "art_style")),
            "value" to stringSchema("The new field value."),
        ),
        required = listOf("field", "value"),
    )
    private val updateCompositionBackground = functionTool(
        name = "update_composition_background",
        description = "Set compositional_deconstruction.background exactly without changing elements.",
        properties = mapOf("value" to stringSchema("The new background description.")),
        required = listOf("value"),
    )
    private val updateSelectedElementDescription = functionTool(
        name = "update_selected_element_description",
        description = "Set the currently selected element desc field exactly.",
        properties = mapOf("value" to stringSchema("The new selected element description.")),
        required = listOf("value"),
    )
    private val updateSelectedElementText = functionTool(
        name = "update_selected_element_text",
        description = "Set the currently selected text element's literal text.",
        properties = mapOf("value" to stringSchema("The new literal text.")),
        required = listOf("value"),
    )
    private val updateSelectedElementBbox = functionTool(
        name = "update_selected_element_bbox",
        description = "Set the currently selected element bbox using Ideogram normalized [y_min, x_min, y_max, x_max] coordinates from 0 to 1000.",
        properties = mapOf(
            "y_min" to integerSchema("Top coordinate from 0 to 1000."),
            "x_min" to integerSchema("Left coordinate from 0 to 1000."),
            "y_max" to integerSchema("Bottom coordinate from 0 to 1000."),
            "x_max" to integerSchema("Right coordinate from 0 to 1000."),
        ),
        required = listOf("y_min", "x_min", "y_max", "x_max"),
    )
    private val updateJsonField = functionTool(
        name = "update_json_field",
        description = "Update an existing JSON field by path for uncommon schema fields. Prefer targeted tools for known fields.",
        properties = mapOf(
            "path" to stringSchema("JSON path such as style_description.aesthetics or compositional_deconstruction.elements[0].desc."),
            "json_value" to stringSchema("A valid JSON value, for example \"text\", 12, true, null, [\"#FFFFFF\"], or {\"x\":1}."),
        ),
        required = listOf("path", "json_value"),
    )
    private val improveHighLevel = functionTool("improve_high_level", "Improve the high-level description field.")
    private val improveStyle = functionTool("improve_style", "Improve the style fields.")
    private val improveComposition = functionTool("improve_composition", "Improve background and element placements.")
    private val improveBackground = functionTool("improve_background", "Improve only the background field.")
    private val improveSelectedElement = functionTool("improve_selected_element", "Improve the currently selected element description.")
    private val suggestGlobalPalette = functionTool("suggest_global_palette", "Suggest the global style palette.")
    private val suggestSelectedElementPalette = functionTool("suggest_selected_element_palette", "Suggest palette for the selected element.")
    private val regenerateSelectedElement = functionTool("regenerate_selected_element", "Create a variant of the selected element.")
    private val addObject = functionTool(
        name = "add_object",
        description = "Add one empty object element to the structured composition. The user can edit it or generate details for the selected element afterwards.",
    )
    private val addText = functionTool(
        name = "add_text",
        description = "Add one empty text element to the structured composition. The user can enter text or generate details for the selected element afterwards.",
    )
    private val deleteSelectedElement = functionTool("delete_selected_element", "Delete the currently selected element.")
    private val setImageSize = functionTool(
        name = "set_image_size",
        description = "Set exact image width and height.",
        properties = mapOf(
            "width" to integerSchema("Image width in pixels."),
            "height" to integerSchema("Image height in pixels."),
        ),
        required = listOf("width", "height"),
    )
    private val setPortrait = functionTool("set_portrait", "Make the current image size portrait/tall.")
    private val setLandscape = functionTool("set_landscape", "Make the current image size landscape/wide.")
    private val setSquare = functionTool("set_square", "Make the current image size square.")
    private val setSteps = functionTool(
        name = "set_steps",
        description = "Set sampling steps.",
        properties = mapOf("steps" to integerSchema("Sampling steps.")),
        required = listOf("steps"),
    )
    private val setCfgScale = functionTool(
        name = "set_cfg_scale",
        description = "Set CFG scale.",
        properties = mapOf("cfg_scale" to numberSchema("CFG scale.")),
        required = listOf("cfg_scale"),
    )
    private val setSeed = functionTool(
        name = "set_seed",
        description = "Set seed.",
        properties = mapOf("seed" to integerSchema("Seed value.")),
        required = listOf("seed"),
    )
    private val randomizeSeed = functionTool("randomize_seed", "Set seed to random.")
    private val setSampler = functionTool(
        name = "set_sampler",
        description = "Set sampler.",
        properties = mapOf("sampler" to stringSchema("Sampler name.")),
        required = listOf("sampler"),
    )
    private val updateGenerationSettings = functionTool(
        name = "update_generation_settings",
        description = "Update one or more generation settings in a single tool call.",
        properties = mapOf(
            "width" to integerSchema("Image width in pixels."),
            "height" to integerSchema("Image height in pixels."),
            "steps" to integerSchema("Sampling steps."),
            "cfg_scale" to numberSchema("CFG scale."),
            "seed" to integerSchema("Seed value."),
            "sampler" to stringSchema("Sampler name."),
        ),
    )
}

private fun functionTool(
    name: String,
    description: String,
    properties: Map<String, JsonObject> = emptyMap(),
    required: List<String> = emptyList(),
): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("function"))
    put(
        "function",
        buildJsonObject {
            put("name", JsonPrimitive(name))
            put("description", JsonPrimitive(description))
            put(
                "parameters",
                buildJsonObject {
                    put("type", JsonPrimitive("object"))
                    put("properties", JsonObject(properties))
                    put("required", JsonArray(required.map { JsonPrimitive(it) }))
                },
            )
        },
    )
}

private fun stringSchema(description: String): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("string"))
    put("description", JsonPrimitive(description))
}

private fun enumSchema(description: String, values: List<String>): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("string"))
    put("description", JsonPrimitive(description))
    put("enum", JsonArray(values.map { JsonPrimitive(it) }))
}

private fun integerSchema(description: String): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("integer"))
    put("description", JsonPrimitive(description))
}

private fun numberSchema(description: String): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("number"))
    put("description", JsonPrimitive(description))
}
