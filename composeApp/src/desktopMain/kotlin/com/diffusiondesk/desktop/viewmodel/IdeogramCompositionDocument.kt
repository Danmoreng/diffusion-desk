package com.diffusiondesk.desktop.viewmodel

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class IdeogramStyleDescription(
    val aesthetics: String,
    val lighting: String,
    val medium: String,
    val photo: String?,
    val artStyle: String?,
    val colorPalette: List<String>,
)

data class IdeogramCompositionElement(
    val type: String,
    val bbox: List<Int>,
    val description: String,
    val text: String?,
    val colorPalette: List<String>,
)

data class IdeogramAdditionalField(
    val path: String,
    val jsonValue: String,
)

data class IdeogramCompositionDocument(
    val highLevelDescription: String,
    val style: IdeogramStyleDescription,
    val background: String,
    val elements: List<IdeogramCompositionElement>,
    val additionalFields: List<IdeogramAdditionalField>,
    internal val source: JsonObject,
)

enum class IdeogramStyleField(val jsonKey: String) {
    Aesthetics("aesthetics"),
    Lighting("lighting"),
    Medium("medium"),
    Photo("photo"),
    ArtStyle("art_style"),
}

sealed interface CompositionMutation {
    data class UpdateHighLevelDescription(val value: String) : CompositionMutation
    data class UpdateStyleField(val field: IdeogramStyleField, val value: String) : CompositionMutation
    data class UpdateGlobalPalette(val colors: List<String>) : CompositionMutation
    data class UpdateBackground(val value: String) : CompositionMutation
    data class UpdateElementType(val index: Int, val value: String) : CompositionMutation
    data class UpdateElementBbox(val index: Int, val value: List<Int>?) : CompositionMutation
    data class UpdateElementDescription(val index: Int, val value: String) : CompositionMutation
    data class UpdateElementText(val index: Int, val value: String) : CompositionMutation
    data class UpdateElementPalette(val index: Int, val colors: List<String>) : CompositionMutation
    data class AddElement(val type: String) : CompositionMutation
    data class RemoveElement(val index: Int) : CompositionMutation
    data class UpdateAdditionalField(val path: String, val jsonValue: String) : CompositionMutation
    data class AddAdditionalField(val path: String, val jsonValue: String) : CompositionMutation
    data class RemoveAdditionalField(val path: String) : CompositionMutation
}

private val compositionJson = Json { ignoreUnknownKeys = true }

@OptIn(ExperimentalSerializationApi::class)
private val compositionJsonPretty = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
}

internal fun parseIdeogramCompositionDocument(value: String): Result<IdeogramCompositionDocument> = runCatching {
    val root = compositionJson.parseToJsonElement(value).jsonObject
    val style = root["style_description"]?.asObject() ?: JsonObject(emptyMap())
    val composition = root["compositional_deconstruction"]?.asObject()
        ?: error("compositional_deconstruction is required.")
    val elements = composition["elements"]?.asArray()
        ?: error("compositional_deconstruction.elements is required.")

    IdeogramCompositionDocument(
        highLevelDescription = root.string("high_level_description"),
        style = IdeogramStyleDescription(
            aesthetics = style.string("aesthetics"),
            lighting = style.string("lighting"),
            medium = style.string("medium"),
            photo = style.optionalString("photo"),
            artStyle = style.optionalString("art_style"),
            colorPalette = style.stringList("color_palette"),
        ),
        background = composition.string("background"),
        elements = elements.mapIndexed { index, element ->
            val obj = element.asObject() ?: error("Element ${index + 1} must be an object.")
            IdeogramCompositionElement(
                type = obj.string("type"),
                bbox = obj.intList("bbox"),
                description = obj.string("desc"),
                text = obj.optionalString("text"),
                colorPalette = obj.stringList("color_palette"),
            )
        },
        additionalFields = collectAdditionalFields(root),
        source = root,
    )
}

internal fun IdeogramCompositionDocument.applyMutation(mutation: CompositionMutation): Result<IdeogramCompositionDocument> = runCatching {
    val root = source.toMutableMap()
    when (mutation) {
        is CompositionMutation.UpdateHighLevelDescription -> root["high_level_description"] = JsonPrimitive(mutation.value)
        is CompositionMutation.UpdateStyleField -> {
            val style = root["style_description"]?.asObject()?.toMutableMap() ?: mutableMapOf()
            if (mutation.field == IdeogramStyleField.Photo) style.remove("art_style")
            if (mutation.field == IdeogramStyleField.ArtStyle) style.remove("photo")
            style[mutation.field.jsonKey] = JsonPrimitive(mutation.value)
            root["style_description"] = JsonObject(style)
        }
        is CompositionMutation.UpdateGlobalPalette -> updatePalette(root, "style_description", mutation.colors)
        is CompositionMutation.UpdateBackground -> {
            val composition = requiredObject(root, "compositional_deconstruction").toMutableMap()
            composition["background"] = JsonPrimitive(mutation.value)
            root["compositional_deconstruction"] = JsonObject(composition)
        }
        is CompositionMutation.UpdateElementType -> updateElement(root, mutation.index) { element ->
            require(mutation.value in setOf("obj", "text")) { "Element type must be obj or text." }
            element["type"] = JsonPrimitive(mutation.value)
            if (mutation.value == "obj") element.remove("text") else element.putIfAbsent("text", JsonPrimitive("Text"))
        }
        is CompositionMutation.UpdateElementBbox -> updateElement(root, mutation.index) { element ->
            if (mutation.value == null) element.remove("bbox")
            else element["bbox"] = JsonArray(mutation.value.map(::JsonPrimitive))
        }
        is CompositionMutation.UpdateElementDescription -> updateElement(root, mutation.index) { element ->
            element["desc"] = JsonPrimitive(mutation.value)
        }
        is CompositionMutation.UpdateElementText -> updateElement(root, mutation.index) { element ->
            element["text"] = JsonPrimitive(mutation.value)
        }
        is CompositionMutation.UpdateElementPalette -> updateElement(root, mutation.index) { element ->
            setPalette(element, mutation.colors)
        }
        is CompositionMutation.AddElement -> {
            require(mutation.type in setOf("obj", "text")) { "Element type must be obj or text." }
            val composition = requiredObject(root, "compositional_deconstruction").toMutableMap()
            val elements = composition["elements"]?.asArray()?.toMutableList() ?: mutableListOf()
            elements += JsonObject(linkedMapOf<String, JsonElement>().apply {
                put("type", JsonPrimitive(mutation.type))
                if (mutation.type == "text") put("text", JsonPrimitive("New text"))
                put("desc", JsonPrimitive(if (mutation.type == "text") "New text element." else "New object."))
            })
            composition["elements"] = JsonArray(elements)
            root["compositional_deconstruction"] = JsonObject(composition)
        }
        is CompositionMutation.RemoveElement -> {
            val composition = requiredObject(root, "compositional_deconstruction").toMutableMap()
            val elements = composition["elements"]?.asArray()?.toMutableList()
                ?: error("compositional_deconstruction.elements is required.")
            require(mutation.index in elements.indices) { "Element ${mutation.index + 1} is missing." }
            elements.removeAt(mutation.index)
            composition["elements"] = JsonArray(elements)
            root["compositional_deconstruction"] = JsonObject(composition)
        }
        is CompositionMutation.UpdateAdditionalField -> {
            val value = compositionJson.parseToJsonElement(mutation.jsonValue)
            val updated = updateJsonPath(JsonObject(root), parseJsonPath(mutation.path), value)
            root.clear()
            root.putAll(updated.asObject() ?: error("The root composition must remain an object."))
        }
        is CompositionMutation.AddAdditionalField -> {
            val value = compositionJson.parseToJsonElement(mutation.jsonValue)
            val updated = setJsonPath(JsonObject(root), parseJsonPath(mutation.path), value)
            root.clear()
            root.putAll(updated.asObject() ?: error("The root composition must remain an object."))
        }
        is CompositionMutation.RemoveAdditionalField -> {
            val updated = removeJsonPath(JsonObject(root), parseJsonPath(mutation.path))
            root.clear()
            root.putAll(updated.asObject() ?: error("The root composition must remain an object."))
        }
    }
    parseIdeogramCompositionDocument(serializeIdeogramCompositionDocument(JsonObject(root))).getOrThrow()
}

internal fun IdeogramCompositionDocument.serialize(): String = serializeIdeogramCompositionDocument(source)

internal fun IdeogramCompositionDocument.serializeForBackend(): String =
    compositionJson.encodeToString(JsonElement.serializer(), canonicalizeIdeogramRoot(source))

private fun serializeIdeogramCompositionDocument(root: JsonObject): String =
    compositionJsonPretty.encodeToString(JsonElement.serializer(), canonicalizeIdeogramRoot(root))

private fun canonicalizeIdeogramRoot(root: JsonObject): JsonObject = JsonObject(linkedMapOf<String, JsonElement>().apply {
    root["high_level_description"]?.let { put("high_level_description", it) }
    root["style_description"]?.asObject()?.let { put("style_description", canonicalizeStyle(it)) }
    root["compositional_deconstruction"]?.asObject()?.let { put("compositional_deconstruction", canonicalizeComposition(it)) }
    root.filterKeys { it !in knownRootFields }.forEach { (key, value) -> put(key, value) }
})

private fun canonicalizeStyle(style: JsonObject): JsonObject = JsonObject(linkedMapOf<String, JsonElement>().apply {
    style["aesthetics"]?.let { put("aesthetics", it) }
    style["lighting"]?.let { put("lighting", it) }
    if (style.containsKey("photo")) {
        style["photo"]?.let { put("photo", it) }
        style["medium"]?.let { put("medium", it) }
    } else {
        style["medium"]?.let { put("medium", it) }
        style["art_style"]?.let { put("art_style", it) }
    }
    style.filterKeys { it !in knownStyleFields }.forEach { (key, value) -> put(key, value) }
    style["color_palette"]?.let { put("color_palette", it) }
})

private fun canonicalizeComposition(composition: JsonObject): JsonObject = JsonObject(linkedMapOf<String, JsonElement>().apply {
    composition["background"]?.let { put("background", it) }
    composition.filterKeys { it !in knownCompositionFields }.forEach { (key, value) -> put(key, value) }
    composition["elements"]?.asArray()?.let { elements ->
        put("elements", JsonArray(elements.map { element ->
            element.asObject()?.let(::canonicalizeElement) ?: element
        }))
    }
})

private fun canonicalizeElement(element: JsonObject): JsonObject = JsonObject(linkedMapOf<String, JsonElement>().apply {
    element["type"]?.let { put("type", it) }
    element["bbox"]?.let { put("bbox", it) }
    if (element.optionalString("type") == "text") element["text"]?.let { put("text", it) }
    element["desc"]?.let { put("desc", it) }
    element.filterKeys { it !in knownElementFields }.forEach { (key, value) -> put(key, value) }
    element["color_palette"]?.let { put("color_palette", it) }
})

private fun updatePalette(root: MutableMap<String, JsonElement>, objectKey: String, colors: List<String>) {
    val target = root[objectKey]?.asObject()?.toMutableMap() ?: mutableMapOf()
    setPalette(target, colors)
    root[objectKey] = JsonObject(target)
}

private fun setPalette(target: MutableMap<String, JsonElement>, colors: List<String>) {
    if (colors.isEmpty()) target.remove("color_palette")
    else target["color_palette"] = JsonArray(colors.map(::JsonPrimitive))
}

private fun updateElement(
    root: MutableMap<String, JsonElement>,
    index: Int,
    transform: (MutableMap<String, JsonElement>) -> Unit,
) {
    val composition = requiredObject(root, "compositional_deconstruction").toMutableMap()
    val elements = composition["elements"]?.asArray()?.toMutableList()
        ?: error("compositional_deconstruction.elements is required.")
    val element = elements.getOrNull(index)?.asObject()?.toMutableMap()
        ?: error("Element ${index + 1} is missing.")
    transform(element)
    elements[index] = JsonObject(element)
    composition["elements"] = JsonArray(elements)
    root["compositional_deconstruction"] = JsonObject(composition)
}

private fun requiredObject(root: Map<String, JsonElement>, key: String): JsonObject =
    root[key]?.asObject() ?: error("$key is required.")

private fun JsonElement.asObject(): JsonObject? = this as? JsonObject
private fun JsonElement.asArray(): JsonArray? = this as? JsonArray
private fun JsonObject.string(key: String): String = optionalString(key).orEmpty()
private fun JsonObject.optionalString(key: String): String? = (get(key) as? JsonPrimitive)?.content
private fun JsonObject.stringList(key: String): List<String> =
    (get(key) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()
private fun JsonObject.intList(key: String): List<Int> =
    (get(key) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content?.toIntOrNull() } ?: emptyList()

private val knownRootFields = setOf("high_level_description", "style_description", "compositional_deconstruction")
private val knownStyleFields = setOf("aesthetics", "lighting", "medium", "photo", "art_style", "color_palette")
private val knownCompositionFields = setOf("background", "elements")
private val knownElementFields = setOf("type", "bbox", "desc", "text", "color_palette")

private fun collectAdditionalFields(root: JsonObject): List<IdeogramAdditionalField> = buildList {
    root.filterKeys { it !in knownRootFields }.forEach { (key, value) -> addAdditional(key, value) }
    root["style_description"]?.asObject()
        ?.filterKeys { it !in knownStyleFields }
        ?.forEach { (key, value) -> addAdditional("style_description.$key", value) }
    val composition = root["compositional_deconstruction"]?.asObject()
    composition?.filterKeys { it !in knownCompositionFields }
        ?.forEach { (key, value) -> addAdditional("compositional_deconstruction.$key", value) }
    composition?.get("elements")?.asArray()?.forEachIndexed { index, element ->
        element.asObject()?.filterKeys { it !in knownElementFields }?.forEach { (key, value) ->
            addAdditional("compositional_deconstruction.elements[$index].$key", value)
        }
    }
}

private fun MutableList<IdeogramAdditionalField>.addAdditional(path: String, value: JsonElement) {
    add(IdeogramAdditionalField(path, compositionJson.encodeToString(JsonElement.serializer(), value)))
}

private sealed interface JsonPathToken {
    data class Key(val value: String) : JsonPathToken
    data class Index(val value: Int) : JsonPathToken
}

private fun parseJsonPath(path: String): List<JsonPathToken> {
    val tokens = Regex("([^.\\[\\]]+)|\\[(\\d+)]").findAll(path).map { match ->
        match.groups[2]?.value?.toIntOrNull()?.let(JsonPathToken::Index)
            ?: JsonPathToken.Key(match.groups[1]?.value ?: error("Invalid JSON path: $path"))
    }.toList()
    require(tokens.isNotEmpty()) { "JSON path is empty." }
    return tokens
}

private fun updateJsonPath(current: JsonElement, path: List<JsonPathToken>, value: JsonElement): JsonElement {
    if (path.isEmpty()) return value
    return when (val token = path.first()) {
        is JsonPathToken.Key -> {
            val obj = current.asObject()?.toMutableMap() ?: error("${token.value} is not inside an object.")
            val child = obj[token.value] ?: error("${token.value} does not exist.")
            obj[token.value] = updateJsonPath(child, path.drop(1), value)
            JsonObject(obj)
        }
        is JsonPathToken.Index -> {
            val array = current.asArray()?.toMutableList() ?: error("[${token.value}] is not inside an array.")
            val child = array.getOrNull(token.value) ?: error("Array index ${token.value} does not exist.")
            array[token.value] = updateJsonPath(child, path.drop(1), value)
            JsonArray(array)
        }
    }
}

private fun setJsonPath(current: JsonElement, path: List<JsonPathToken>, value: JsonElement): JsonElement {
    if (path.isEmpty()) return value
    return when (val token = path.first()) {
        is JsonPathToken.Key -> {
            val obj = current.asObject()?.toMutableMap() ?: error("${token.value} is not inside an object.")
            if (path.size == 1) obj[token.value] = value
            else {
                val child = obj[token.value] ?: error("${token.value} does not exist.")
                obj[token.value] = setJsonPath(child, path.drop(1), value)
            }
            JsonObject(obj)
        }
        is JsonPathToken.Index -> {
            val array = current.asArray()?.toMutableList() ?: error("[${token.value}] is not inside an array.")
            val child = array.getOrNull(token.value) ?: error("Array index ${token.value} does not exist.")
            array[token.value] = setJsonPath(child, path.drop(1), value)
            JsonArray(array)
        }
    }
}

private fun removeJsonPath(current: JsonElement, path: List<JsonPathToken>): JsonElement {
    require(path.isNotEmpty()) { "JSON path is empty." }
    return when (val token = path.first()) {
        is JsonPathToken.Key -> {
            val obj = current.asObject()?.toMutableMap() ?: error("${token.value} is not inside an object.")
            if (path.size == 1) require(obj.remove(token.value) != null) { "${token.value} does not exist." }
            else {
                val child = obj[token.value] ?: error("${token.value} does not exist.")
                obj[token.value] = removeJsonPath(child, path.drop(1))
            }
            JsonObject(obj)
        }
        is JsonPathToken.Index -> error("Removing array items through Additional Fields is not supported.")
    }
}
