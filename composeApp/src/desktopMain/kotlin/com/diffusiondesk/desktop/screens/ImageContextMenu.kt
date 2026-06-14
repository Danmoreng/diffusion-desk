package com.diffusiondesk.desktop.screens

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.runtime.Composable
import com.diffusiondesk.desktop.core.GeneratedImage
import java.awt.Desktop
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.image.BufferedImage
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.imageio.ImageIO

internal data class ImageContextMenuData(
    val labelPrefix: String = "",
    val file: File? = null,
    val bytes: ByteArray? = null,
    val bufferedImage: BufferedImage? = null,
    val defaultFileName: String = file?.name ?: "image.png",
    val extension: String = defaultFileName.imageExtension(),
)

@Composable
internal fun ImageContextMenuArea(
    images: List<ImageContextMenuData>,
    content: @Composable () -> Unit,
) {
    ContextMenuArea(
        items = {
            buildList {
                images.filter { it.canUseContextMenu }.forEach { image ->
                    add(ContextMenuItem("${image.labelPrefix}Copy Image") {
                        runAfterPopupClick { image.copyToClipboard() }
                    })
                    add(ContextMenuItem("${image.labelPrefix}Save Image As...") {
                        runAfterPopupClick { image.saveAs() }
                    })
                    image.file?.takeIf { it.exists() }?.let { file ->
                        add(ContextMenuItem("${image.labelPrefix}Open Image") {
                            runAfterPopupClick { openImageFile(file) }
                        })
                        add(ContextMenuItem("${image.labelPrefix}Show in Explorer") {
                            runAfterPopupClick { showImageInExplorer(file) }
                        })
                    }
                }
            }
        },
        content = content,
    )
}

internal fun File.toImageContextMenuData(labelPrefix: String = ""): ImageContextMenuData {
    return ImageContextMenuData(
        labelPrefix = labelPrefix,
        file = this,
        defaultFileName = name,
        extension = extension.ifBlank { "png" },
    )
}

internal fun GeneratedImage.toImageContextMenuData(
    outputDir: String,
    labelPrefix: String = "",
    fallbackFileName: String = "generated-image.png",
): ImageContextMenuData {
    val file = resolveOutputFile(outputDir)
    val fileName = sourceFileName() ?: file?.name ?: fallbackFileName
    return ImageContextMenuData(
        labelPrefix = labelPrefix,
        file = file,
        bytes = bytes,
        bufferedImage = bufferedImage,
        defaultFileName = fileName,
        extension = fileName.imageExtension(),
    )
}

internal fun ImageContextMenuData.copyToClipboard() {
    val image = bufferedImage ?: file?.takeIf { it.isFile }?.let { ImageIO.read(it) } ?: return
    Toolkit.getDefaultToolkit().systemClipboard.setContents(DesktopImageTransferable(image), null)
}

internal fun ImageContextMenuData.saveAs() {
    val dialog = FileDialog(activeFrame(), "Save Image", FileDialog.SAVE).apply {
        file = defaultFileName
        isVisible = true
    }
    val selectedFile = dialog.file ?: return
    val directory = dialog.directory ?: return
    val target = File(directory, selectedFile).withImageExtension(extension)
    when {
        bytes != null -> target.writeBytes(bytes)
        file != null -> file.copyTo(target, overwrite = true)
    }
}

internal fun GeneratedImage.resolveOutputFile(outputDir: String): File? {
    val sourceUri = runCatching { URI(sourceUrl) }.getOrNull()
    if (sourceUri?.scheme.equals("file", ignoreCase = true)) {
        return runCatching { File(sourceUri).takeIf { it.isFile } }.getOrNull()
    }
    if (outputDir.isBlank()) return null
    val path = sourceUri?.path ?: return null
    val marker = "/outputs/"
    val markerIndex = path.indexOf(marker)
    if (markerIndex < 0) return null

    val relativePath = URLDecoder.decode(
        path.substring(markerIndex + marker.length),
        StandardCharsets.UTF_8,
    )
    val outputRoot = File(outputDir).canonicalFile
    val file = File(outputRoot, relativePath.replace('/', File.separatorChar)).canonicalFile
    return file.takeIf {
        it.path == outputRoot.path || it.path.startsWith(outputRoot.path + File.separator)
    }
}

internal fun GeneratedImage.sourceFileName(): String? {
    val path = runCatching { URI(sourceUrl).path }.getOrNull() ?: return null
    return URLDecoder.decode(path.substringAfterLast('/'), StandardCharsets.UTF_8)
        .takeIf { it.isNotBlank() }
}

internal fun File.withImageExtension(extension: String): File {
    return if (name.substringAfterLast('.', missingDelimiterValue = "").isBlank()) {
        File(parentFile, "$name.$extension")
    } else {
        this
    }
}

private val ImageContextMenuData.canUseContextMenu: Boolean
    get() = bufferedImage != null || bytes != null || file?.exists() == true

private fun runAfterPopupClick(action: () -> Unit) {
    EventQueue.invokeLater(action)
}

internal fun openImageFile(file: File) {
    if (Desktop.isDesktopSupported()) {
        Desktop.getDesktop().open(file)
    }
}

internal fun showImageInExplorer(file: File) {
    if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        ProcessBuilder("explorer.exe", "/select,", file.absolutePath).start()
    } else if (Desktop.isDesktopSupported()) {
        Desktop.getDesktop().open(file.parentFile)
    }
}

private fun activeFrame(): Frame? {
    return Frame.getFrames().firstOrNull { it.isActive }
        ?: Frame.getFrames().firstOrNull { it.isVisible }
}

private fun String.imageExtension(): String {
    return substringAfterLast('.', missingDelimiterValue = "")
        .lowercase(Locale.US)
        .takeIf { it in setOf("png", "jpg", "jpeg", "webp", "bmp", "gif") }
        ?: "png"
}

private class DesktopImageTransferable(
    private val image: java.awt.Image,
) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.imageFlavor

    override fun getTransferData(flavor: DataFlavor): Any {
        if (!isDataFlavorSupported(flavor)) {
            throw UnsupportedOperationException("Unsupported clipboard flavor: $flavor")
        }
        return image
    }
}
