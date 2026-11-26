package net.thunderbird.feature.mail.message.composer.internal.signature

/**
 * Fetches an image that is pasted into a signature and makes it small enough to be embedded.
 */
internal fun interface SignatureImageLoader {
    suspend fun load(source: String): ImageLoadResult
}

internal sealed interface ImageLoadResult {
    /**
     * An image ready to be embedded. [width] is the width it is shown with, [wasDownscaled] tells whether it had to be
     * made smaller for that.
     */
    class Loaded(
        val bytes: ByteArray,
        val mimeType: String,
        val width: Int,
        val wasDownscaled: Boolean,
    ) : ImageLoadResult

    /** The image could not be fetched from the web. */
    data object NotDownloaded : ImageLoadResult

    /** The data is not an image this app can read. */
    data object Unreadable : ImageLoadResult
}
