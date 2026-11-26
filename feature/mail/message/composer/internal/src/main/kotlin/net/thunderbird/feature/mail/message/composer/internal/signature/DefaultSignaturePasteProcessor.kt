package net.thunderbird.feature.mail.message.composer.internal.signature

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import kotlin.io.encoding.Base64
import net.thunderbird.feature.mail.message.composer.signature.ClipboardContent
import net.thunderbird.feature.mail.message.composer.signature.HtmlSignatureSanitizer
import net.thunderbird.feature.mail.message.composer.signature.PasteNotice
import net.thunderbird.feature.mail.message.composer.signature.PasteResult
import net.thunderbird.feature.mail.message.composer.signature.SignatureImageLimits
import net.thunderbird.feature.mail.message.composer.signature.SignaturePasteProcessor

private const val ACTIVE_ELEMENTS = "script, iframe, object, embed, applet, form"

internal class DefaultSignaturePasteProcessor(
    private val imageLoader: SignatureImageLoader,
    private val sanitizer: HtmlSignatureSanitizer,
) : SignaturePasteProcessor {

    override suspend fun process(content: ClipboardContent, usedImageBytes: Int): PasteResult {
        if (content.isEmpty) return PasteResult.NothingToPaste()

        val document = Ksoup.parseBodyFragment(sourceHtml(content))
        val notices = mutableListOf<PasteNotice>()
        if (containsActiveContent(document)) notices += PasteNotice.ActiveContentRemoved

        appendClipboardImages(document, content.imageSources)
        notices += embedImages(document, usedImageBytes)

        val html = sanitizer.sanitize(document.body().html()).trim()
        return if (html.isEmpty()) PasteResult.NothingToPaste(notices) else PasteResult.Content(html, notices)
    }

    private fun sourceHtml(content: ClipboardContent): String {
        return content.html?.takeIf { it.isNotBlank() } ?: plainTextToHtml(content.plainText.orEmpty())
    }

    private fun plainTextToHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\r\n", "\n")
            .replace("\n", "<br>")
    }

    private fun containsActiveContent(document: Document): Boolean {
        if (document.select(ACTIVE_ELEMENTS).isNotEmpty()) return true

        return document.getAllElements().any { element ->
            element.attributes().asList().any { attribute ->
                attribute.key.startsWith("on", ignoreCase = true) ||
                    attribute.value.trimStart().startsWith("javascript:", ignoreCase = true)
            }
        }
    }

    private fun appendClipboardImages(document: Document, sources: List<String>) {
        for (source in sources) {
            document.body().appendElement("p").appendElement("img").attr("src", source)
        }
    }

    private suspend fun embedImages(document: Document, usedImageBytes: Int): List<PasteNotice> {
        var usedBytes = usedImageBytes
        var notDownloaded = 0
        var unreadable = 0
        var tooLarge = 0
        var downscaled = 0

        for (image in document.select("img").toList()) {
            val source = image.attr("src").trim()
            val loaded = if (source.isEmpty()) ImageLoadResult.Unreadable else imageLoader.load(source)
            when (loaded) {
                ImageLoadResult.NotDownloaded -> {
                    notDownloaded++
                    image.remove()
                }

                ImageLoadResult.Unreadable -> {
                    unreadable++
                    image.remove()
                }

                is ImageLoadResult.Loaded -> {
                    if (usedBytes + loaded.bytes.size > SignatureImageLimits.MAX_TOTAL_BYTES) {
                        tooLarge++
                        image.remove()
                    } else {
                        usedBytes += loaded.bytes.size
                        if (loaded.wasDownscaled) downscaled++
                        embed(image, loaded)
                    }
                }
            }
        }

        return buildList {
            if (notDownloaded > 0) add(PasteNotice.ImagesNotDownloaded(notDownloaded))
            if (unreadable > 0) add(PasteNotice.ImagesUnreadable(unreadable))
            if (tooLarge > 0) add(PasteNotice.ImagesTooLarge(tooLarge, SignatureImageLimits.MAX_TOTAL_BYTES))
            if (downscaled > 0) add(PasteNotice.ImagesDownscaled(downscaled))
        }
    }

    private fun embed(image: Element, loaded: ImageLoadResult.Loaded) {
        image.attr("src", "data:${loaded.mimeType};base64,${Base64.encode(loaded.bytes)}")
        if (loaded.wasDownscaled) {
            // The proportions come from the new width, so the old height must not stay.
            image.attr("width", loaded.width.toString())
            image.removeAttr("height")
        }
    }
}
