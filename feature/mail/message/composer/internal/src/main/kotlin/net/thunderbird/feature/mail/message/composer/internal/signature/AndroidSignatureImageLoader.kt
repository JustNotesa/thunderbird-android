package net.thunderbird.feature.mail.message.composer.internal.signature

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.thunderbird.components.core.logging.Logger
import net.thunderbird.feature.mail.message.composer.signature.SignatureImageLimits
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "SignatureImageLoader"
private const val JPEG_QUALITY = 85
private const val PNG_QUALITY = 100
private const val JPEG_FALLBACK_QUALITY = 60
private const val KEEP_AS_IS_BYTES = 100 * 1024
private val KEEP_AS_IS_TYPES = setOf("image/png", "image/jpeg", "image/gif", "image/webp")

/**
 * Loads the images of a pasted signature from the web (`http(s)`, only because the user pasted), from other apps
 * (`content:`) or from the HTML itself (`data:`), and makes them small enough to embed.
 *
 * Nothing about the addresses or the images is logged.
 */
internal class AndroidSignatureImageLoader(
    private val contentResolver: ContentResolver,
    private val httpClient: OkHttpClient,
    private val logger: Logger,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SignatureImageLoader {

    override suspend fun load(source: String): ImageLoadResult = withContext(dispatcher) {
        val scheme = source.substringBefore(':', "").lowercase()
        val raw = when (scheme) {
            "http", "https" -> download(source) ?: return@withContext ImageLoadResult.NotDownloaded
            "content" -> read(source) ?: return@withContext ImageLoadResult.Unreadable
            "data" -> decodeDataUri(source) ?: return@withContext ImageLoadResult.Unreadable
            else -> return@withContext ImageLoadResult.Unreadable
        }
        prepare(raw) ?: ImageLoadResult.Unreadable
    }

    // The exceptions are not logged with their message, which holds the address of the image.
    @Suppress("SwallowedException")
    private fun download(url: String): ByteArray? {
        return try {
            val request = Request.Builder().url(url).header("Accept", "image/*").build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body.byteStream().readLimited()
            }
        } catch (e: IOException) {
            logger.warn(TAG) { "Downloading a pasted image failed" }
            null
        } catch (e: IllegalArgumentException) {
            logger.warn(TAG) { "A pasted image has an invalid address" }
            null
        }
    }

    @Suppress("SwallowedException")
    private fun read(uri: String): ByteArray? {
        return try {
            contentResolver.openInputStream(Uri.parse(uri))?.use { it.readLimited() }
        } catch (e: IOException) {
            logger.warn(TAG) { "Reading a pasted image failed" }
            null
        } catch (e: SecurityException) {
            logger.warn(TAG) { "A pasted image is not readable" }
            null
        }
    }

    @Suppress("ReturnCount", "SwallowedException")
    private fun decodeDataUri(uri: String): ByteArray? {
        val header = uri.substringBefore(',', "")
        if (!header.contains(";base64", ignoreCase = true)) return null
        return try {
            Base64.Mime.decode(uri.substringAfter(',').filterNot { it.isWhitespace() })
                .takeIf { it.size <= SignatureImageLimits.MAX_DOWNLOAD_BYTES }
        } catch (e: IllegalArgumentException) {
            logger.warn(TAG) { "A pasted data image is broken" }
            null
        }
    }

    /**
     * Reads at most [SignatureImageLimits.MAX_DOWNLOAD_BYTES]. Anything bigger is not read to the end.
     */
    private fun InputStream.readLimited(): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            if (output.size() > SignatureImageLimits.MAX_DOWNLOAD_BYTES) return null
        }
        return output.toByteArray()
    }

    @Suppress("ReturnCount")
    private fun prepare(raw: ByteArray): ImageLoadResult.Loaded? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        val mimeType = bounds.outMimeType ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val (targetWidth, targetHeight) = SignatureImageLimits.scaledSize(bounds.outWidth, bounds.outHeight)
        val needsScaling = targetWidth != bounds.outWidth
        if (!needsScaling && mimeType in KEEP_AS_IS_TYPES && raw.size <= KEEP_AS_IS_BYTES) {
            return ImageLoadResult.Loaded(raw, mimeType, bounds.outWidth, wasDownscaled = false)
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, targetWidth)
        }
        val decoded = BitmapFactory.decodeByteArray(raw, 0, raw.size, options) ?: return null
        val bitmap = if (decoded.width != targetWidth) {
            Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true).also { decoded.recycle() }
        } else {
            decoded
        }

        return try {
            val hasAlpha = bitmap.hasAlpha()
            val bytes = encode(bitmap, hasAlpha)
            ImageLoadResult.Loaded(
                bytes = bytes,
                mimeType = if (hasAlpha) "image/png" else "image/jpeg",
                width = bitmap.width,
                wasDownscaled = needsScaling,
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun sampleSize(sourceWidth: Int, targetWidth: Int): Int {
        var sample = 1
        while (sourceWidth / (sample * 2) >= targetWidth) sample *= 2
        return sample
    }

    private fun encode(bitmap: Bitmap, hasAlpha: Boolean): ByteArray {
        if (hasAlpha) return compress(bitmap, Bitmap.CompressFormat.PNG, PNG_QUALITY)
        val first = compress(bitmap, Bitmap.CompressFormat.JPEG, JPEG_QUALITY)
        return if (first.size >
            KEEP_AS_IS_BYTES
        ) {
            compress(bitmap, Bitmap.CompressFormat.JPEG, JPEG_FALLBACK_QUALITY)
        } else {
            first
        }
    }

    private fun compress(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int): ByteArray {
        return ByteArrayOutputStream().also { bitmap.compress(format, quality, it) }.toByteArray()
    }

    private companion object {
        const val BUFFER_SIZE = 8 * 1024
    }
}
