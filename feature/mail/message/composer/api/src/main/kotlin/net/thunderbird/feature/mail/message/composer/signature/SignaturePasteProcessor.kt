package net.thunderbird.feature.mail.message.composer.signature

/**
 * What the user copied, as far as a signature can use it.
 *
 * @param html The formatted text, if the clipboard has some.
 * @param plainText The text without formatting, used when there is no [html].
 * @param imageSources Addresses (`content:`) of images that are on the clipboard on their own.
 */
data class ClipboardContent(
    val html: String? = null,
    val plainText: String? = null,
    val imageSources: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = html.isNullOrBlank() && plainText.isNullOrEmpty() && imageSources.isEmpty()
}

/**
 * Something about a paste the user has to be told. Nothing that goes wrong or is left out is hidden.
 */
sealed interface PasteNotice {
    /** Scripts, event handlers or other active content were removed from the pasted text. */
    data object ActiveContentRemoved : PasteNotice

    /** [count] images could not be downloaded (no connection, timeout, error answer) and are left out. */
    data class ImagesNotDownloaded(val count: Int) : PasteNotice

    /** [count] images could not be read or are no images and are left out. */
    data class ImagesUnreadable(val count: Int) : PasteNotice

    /** [count] images are left out because the signature would become larger than [limitBytes]. */
    data class ImagesTooLarge(val count: Int, val limitBytes: Int) : PasteNotice

    /** [count] images were made smaller to fit the signature. */
    data class ImagesDownscaled(val count: Int) : PasteNotice

    /** The clipboard has nothing a signature can use. */
    data object NothingUsable : PasteNotice
}

sealed interface PasteResult {
    /** The sanitized [html] to insert into a signature, with the [notices] to show to the user. */
    data class Content(val html: String, val notices: List<PasteNotice>) : PasteResult

    /** There was nothing a signature can use, e.g. all images failed. [notices] says why, if there is a reason. */
    data class NothingToPaste(val notices: List<PasteNotice> = emptyList()) : PasteResult
}

/**
 * Turns what the user copied into HTML for a signature: formatting is kept, the HTML is sanitized, and images are
 * embedded as `data:` addresses. Images with web addresses are downloaded only here, which is only called when the user
 * explicitly pastes.
 */
fun interface SignaturePasteProcessor {
    /**
     * @param usedImageBytes The size of the images the signature already has, they count towards the limit.
     */
    suspend fun process(content: ClipboardContent, usedImageBytes: Int): PasteResult
}

/**
 * The size of the images embedded in a signature.
 */
object SignatureImageLimits {
    const val MAX_WIDTH_PIXELS = 800
    const val MAX_TOTAL_BYTES = 256 * 1024
    const val MAX_DOWNLOAD_BYTES = 8 * 1024 * 1024
    private const val BASE64_DECODED_BYTES = 3
    private const val BASE64_ENCODED_CHARS = 4

    /**
     * The size an image of [width] x [height] pixels is shown with: unchanged if it is narrow enough, otherwise
     * [MAX_WIDTH_PIXELS] wide with the same proportions.
     */
    fun scaledSize(width: Int, height: Int): Pair<Int, Int> {
        if (width <= MAX_WIDTH_PIXELS || width <= 0) return width to height
        val scaledHeight = (height.toLong() * MAX_WIDTH_PIXELS / width).toInt().coerceAtLeast(1)
        return MAX_WIDTH_PIXELS to scaledHeight
    }

    private val EMBEDDED_IMAGE = Regex("""data:image/[a-zA-Z0-9.+-]+;base64,[A-Za-z0-9+/=\s]*""")

    /** The size of all images embedded as `data:` address in [html]. */
    fun embeddedImageBytes(html: String): Int = EMBEDDED_IMAGE.findAll(html).sumOf { embeddedBytes(it.value) }

    /** The bytes of an image embedded as `data:` address, without the base64 inflation counted. */
    fun embeddedBytes(dataUri: String): Int {
        val payload = dataUri.substringAfter(',', "")
        return payload.length * BASE64_DECODED_BYTES / BASE64_ENCODED_CHARS
    }
}
