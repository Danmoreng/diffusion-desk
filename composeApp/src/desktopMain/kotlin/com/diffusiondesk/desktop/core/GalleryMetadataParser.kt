package com.diffusiondesk.desktop.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.InflaterInputStream
import javax.imageio.ImageIO

object GalleryMetadataParser {
    private val supportedImageExtensions = setOf("png", "jpg", "jpeg", "webp", "bmp")

    fun isSupportedImage(file: File): Boolean {
        return file.isFile && file.extension.lowercase(Locale.US) in supportedImageExtensions
    }

    fun parse(imageFile: File): ParsedImageMetadata {
        val sidecar = imageFile.resolveSibling("${imageFile.nameWithoutExtension}.txt")
        val sidecarMetadata = if (sidecar.isFile) {
            parseGenerationText(sidecar.readText(Charsets.UTF_8))
        } else {
            ParsedImageMetadata()
        }

        val pngMetadata = if (imageFile.extension.equals("png", ignoreCase = true)) {
            parsePngMetadata(imageFile)
        } else {
            ParsedImageMetadata()
        }

        val dimensions = readDimensions(imageFile)
        return sidecarMetadata.mergeMissingFrom(pngMetadata).let { metadata ->
            metadata.copy(
                width = metadata.width ?: dimensions?.first,
                height = metadata.height ?: dimensions?.second,
            )
        }
    }

    fun parseGenerationText(text: String): ParsedImageMetadata {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (normalized.isBlank()) return ParsedImageMetadata()

        val lines = normalized.lines()
        val stepsIndex = lines.indexOfFirst { it.trimStart().startsWith("Steps:", ignoreCase = true) }
        val negativeIndex = lines.indexOfFirst { it.trimStart().startsWith("Negative prompt:", ignoreCase = true) }
        val promptEnd = listOf(negativeIndex, stepsIndex).filter { it >= 0 }.minOrNull() ?: lines.size
        val prompt = lines.take(promptEnd).joinToString("\n").trim()

        val negativePrompt = if (negativeIndex >= 0) {
            val end = if (stepsIndex > negativeIndex) stepsIndex else lines.size
            lines.subList(negativeIndex, end).joinToString("\n")
                .trim()
                .removePrefixIgnoringCase("Negative prompt:")
                .trim()
        } else {
            ""
        }

        val paramsLine = if (stepsIndex >= 0) lines.drop(stepsIndex).joinToString(", ") else ""
        val params = parseKeyValueParams(paramsLine)
        val size = params["size"].orEmpty()
        val sizeParts = Regex("""(\d+)\s*x\s*(\d+)""", RegexOption.IGNORE_CASE).find(size)

        return ParsedImageMetadata(
            prompt = prompt,
            negativePrompt = negativePrompt,
            seed = params["seed"]?.toLongOrNull(),
            width = sizeParts?.groupValues?.getOrNull(1)?.toIntOrNull(),
            height = sizeParts?.groupValues?.getOrNull(2)?.toIntOrNull(),
            steps = params["steps"]?.toIntOrNull(),
            cfgScale = params["cfg scale"]?.toDoubleOrNull(),
            sampler = params["sampler"].orEmpty(),
            modelId = params["model"].orEmpty(),
            generationTime = params["time"]?.removeSuffix("s")?.trim()?.toDoubleOrNull(),
            metadataText = normalized,
        )
    }

    private fun parsePngMetadata(file: File): ParsedImageMetadata {
        val chunks = readPngTextChunks(file)
        if (chunks.isEmpty()) return ParsedImageMetadata()

        val parameters = chunks.firstValue("parameters")
            ?: chunks.firstValue("Description")
            ?: chunks.firstValue("Comment")

        val parsedParameters = parameters?.let(::parseGenerationText) ?: ParsedImageMetadata()
        val prompt = chunks.firstValue("prompt").orEmpty()
        val negativePrompt = chunks.firstValue("negative_prompt")
            ?: chunks.firstValue("negative prompt")
            ?: chunks.firstValue("Negative prompt")
            ?: ""

        return parsedParameters.copy(
            prompt = parsedParameters.prompt.ifBlank { prompt },
            negativePrompt = parsedParameters.negativePrompt.ifBlank { negativePrompt },
            metadataText = parsedParameters.metadataText.ifBlank {
                chunks.entries.joinToString("\n") { (key, value) -> "$key: $value" }
            },
        )
    }

    private fun readPngTextChunks(file: File): Map<String, String> {
        val signature = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
        val result = linkedMapOf<String, String>()
        file.inputStream().buffered().use { input ->
            val header = ByteArray(8)
            if (input.read(header) != 8 || !header.contentEquals(signature)) return emptyMap()
            val data = DataInputStream(input)
            while (true) {
                val length = runCatching { data.readInt() }.getOrNull() ?: break
                if (length < 0 || length > 64 * 1024 * 1024) break
                val typeBytes = ByteArray(4)
                data.readFully(typeBytes)
                val type = String(typeBytes, StandardCharsets.ISO_8859_1)
                val payload = ByteArray(length)
                data.readFully(payload)
                data.skipBytes(4)
                when (type) {
                    "tEXt" -> parseTextChunk(payload)?.let { (key, value) -> result[key] = value }
                    "zTXt" -> parseCompressedTextChunk(payload)?.let { (key, value) -> result[key] = value }
                    "iTXt" -> parseInternationalTextChunk(payload)?.let { (key, value) -> result[key] = value }
                    "IEND" -> break
                }
            }
        }
        return result
    }

    private fun parseTextChunk(payload: ByteArray): Pair<String, String>? {
        val separator = payload.indexOf(0)
        if (separator <= 0) return null
        val key = payload.decode(0, separator, StandardCharsets.ISO_8859_1)
        val value = payload.decode(separator + 1, payload.size, StandardCharsets.ISO_8859_1)
        return key to value
    }

    private fun parseCompressedTextChunk(payload: ByteArray): Pair<String, String>? {
        val separator = payload.indexOf(0)
        if (separator <= 0 || separator + 2 >= payload.size) return null
        val key = payload.decode(0, separator, StandardCharsets.ISO_8859_1)
        val compressionMethod = payload[separator + 1].toInt()
        if (compressionMethod != 0) return null
        val compressed = payload.copyOfRange(separator + 2, payload.size)
        return key to inflate(compressed).toString(StandardCharsets.ISO_8859_1)
    }

    private fun parseInternationalTextChunk(payload: ByteArray): Pair<String, String>? {
        var offset = 0
        val keywordEnd = payload.indexOf(0, offset)
        if (keywordEnd <= 0 || keywordEnd + 2 >= payload.size) return null
        val key = payload.decode(offset, keywordEnd, StandardCharsets.ISO_8859_1)
        offset = keywordEnd + 1
        val compressionFlag = payload[offset].toInt()
        val compressionMethod = payload[offset + 1].toInt()
        offset += 2
        val languageEnd = payload.indexOf(0, offset)
        if (languageEnd < 0) return null
        offset = languageEnd + 1
        val translatedEnd = payload.indexOf(0, offset)
        if (translatedEnd < 0) return null
        offset = translatedEnd + 1
        val textBytes = payload.copyOfRange(offset, payload.size)
        val value = if (compressionFlag == 1) {
            if (compressionMethod != 0) return null
            inflate(textBytes).toString(StandardCharsets.UTF_8)
        } else {
            textBytes.toString(StandardCharsets.UTF_8)
        }
        return key to value
    }

    private fun parseKeyValueParams(text: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        Regex("""([^:,]+):\s*([^,]+)""").findAll(text).forEach { match ->
            val key = match.groupValues[1].trim().lowercase(Locale.US)
            val value = match.groupValues[2].trim()
            result[key] = value
        }
        return result
    }

    private fun readDimensions(file: File): Pair<Int, Int>? {
        return runCatching {
            ImageIO.createImageInputStream(file).use { input ->
                val readers = ImageIO.getImageReaders(input)
                if (!readers.hasNext()) return null
                val reader = readers.next()
                try {
                    reader.input = input
                    reader.getWidth(0) to reader.getHeight(0)
                } finally {
                    reader.dispose()
                }
            }
        }.getOrNull()
    }

    private fun ParsedImageMetadata.mergeMissingFrom(other: ParsedImageMetadata): ParsedImageMetadata {
        return copy(
            prompt = prompt.ifBlank { other.prompt },
            negativePrompt = negativePrompt.ifBlank { other.negativePrompt },
            seed = seed ?: other.seed,
            width = width ?: other.width,
            height = height ?: other.height,
            steps = steps ?: other.steps,
            cfgScale = cfgScale ?: other.cfgScale,
            sampler = sampler.ifBlank { other.sampler },
            modelId = modelId.ifBlank { other.modelId },
            generationTime = generationTime ?: other.generationTime,
            metadataText = metadataText.ifBlank { other.metadataText },
        )
    }

    private fun Map<String, String>.firstValue(vararg names: String): String? {
        for (name in names) {
            entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.let { return it }
        }
        return null
    }

    private fun String.removePrefixIgnoringCase(prefix: String): String {
        return if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else this
    }

    private fun ByteArray.indexOf(value: Int, startIndex: Int = 0): Int {
        for (index in startIndex until size) {
            if (this[index].toInt() == value) return index
        }
        return -1
    }

    private fun ByteArray.decode(start: Int, end: Int, charset: Charset): String {
        return copyOfRange(start, end).toString(charset)
    }

    private fun inflate(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        InflaterInputStream(ByteArrayInputStream(bytes)).use { input ->
            input.copyTo(out)
        }
        return out.toByteArray()
    }
}
