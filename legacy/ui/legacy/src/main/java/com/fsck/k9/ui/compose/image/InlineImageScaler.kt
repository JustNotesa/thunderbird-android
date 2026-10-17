package com.fsck.k9.ui.compose.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.roundToInt

private const val JPEG_QUALITY = 85
private const val MAX_ORIGINAL_BYTES = 25L * 1024 * 1024
private const val COPY_BUFFER_BYTES = 8 * 1024
private const val FILE_PREFIX = "inline-image"

/**
 * Why an image can't be put into the text.
 */
enum class InlineImageProblem {
    /** The data is not an image the app can read. */
    NotAnImage,

    /** The image can't be read, e.g. the file is gone. */
    Unreadable,

    /** The original is larger than 25 MB. */
    TooLarge,

    /** There is not enough memory to scale the image. */
    OutOfMemory,
}

/**
 * The outcome of [InlineImageScaler.scale].
 */
sealed interface ScaledImage {
    /** The image is in [file], of type [mimeType], to be attached under [name]. */
    data class Ready(
        val file: File,
        val mimeType: String,
        val name: String,
        val width: Int,
        val height: Int,
    ) : ScaledImage

    data class Failed(val problem: InlineImageProblem) : ScaledImage
}

/**
 * Prepares an image for the text of a message: scaled down to the chosen [InlineImageSize] and written to a file in
 * [outputDirectory] (the cache directory of the app), which becomes the attachment.
 *
 * Scaled images are JPEG with quality 85, except images with transparency, which stay PNG. Scaling turns the image
 * upright by its EXIF orientation. The original is copied unchanged.
 *
 * Meant to run off the main thread.
 */
class InlineImageScaler(private val outputDirectory: File) {
    fun scale(openStream: () -> InputStream?, size: InlineImageSize): ScaledImage {
        return try {
            val bounds = readImageBounds(openStream)
            val mimeType = bounds.outMimeType
            val longEdge = size.longEdge
            when {
                mimeType == null || bounds.outWidth <= 0 || bounds.outHeight <= 0 ->
                    ScaledImage.Failed(InlineImageProblem.NotAnImage)
                longEdge == null -> copyOriginal(openStream, mimeType, bounds)
                else -> resize(openStream, bounds, longEdge)
            }
        } catch (@Suppress("SwallowedException") e: IOException) {
            ScaledImage.Failed(InlineImageProblem.Unreadable)
        } catch (@Suppress("SwallowedException") e: OutOfMemoryError) {
            ScaledImage.Failed(InlineImageProblem.OutOfMemory)
        }
    }

    private fun copyOriginal(
        openStream: () -> InputStream?,
        mimeType: String,
        bounds: BitmapFactory.Options,
    ): ScaledImage {
        val extension = extensionFor(mimeType)
        val file = File.createTempFile(FILE_PREFIX, ".$extension", outputDirectory).apply { deleteOnExit() }
        val complete = openStream()?.use { input ->
            file.outputStream().use { output -> input.copyUpTo(output, MAX_ORIGINAL_BYTES) }
        } ?: false
        if (!complete) file.delete()

        return if (complete) {
            ScaledImage.Ready(file, mimeType, "image.$extension", bounds.outWidth, bounds.outHeight)
        } else {
            ScaledImage.Failed(InlineImageProblem.TooLarge)
        }
    }

    private fun resize(openStream: () -> InputStream?, bounds: BitmapFactory.Options, longEdge: Int): ScaledImage {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(maxOf(bounds.outWidth, bounds.outHeight), longEdge)
        }
        val decoded = openStream()?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return ScaledImage.Failed(InlineImageProblem.Unreadable)
        val matrix = openStream()?.use { readOrientationMatrix(it) }
        // Asked before scaling, which has to keep the transparency but is not the place to find out about it.
        val isPng = decoded.hasAlpha()
        val bitmap = decoded.limitedTo(longEdge).turned(matrix)

        return write(bitmap, isPng)
    }

    private fun write(bitmap: Bitmap, isPng: Boolean): ScaledImage {
        // Only an image with transparency needs PNG; the photos and screenshots of the gallery are smaller as JPEG.
        val extension = if (isPng) "png" else "jpg"
        val format = if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val file = File.createTempFile(FILE_PREFIX, ".$extension", outputDirectory).apply { deleteOnExit() }
        file.outputStream().use { output -> bitmap.compress(format, JPEG_QUALITY, output) }

        return ScaledImage.Ready(
            file = file,
            mimeType = if (isPng) "image/png" else "image/jpeg",
            name = "image.$extension",
            width = bitmap.width,
            height = bitmap.height,
        )
    }

    private fun Bitmap.limitedTo(longEdge: Int): Bitmap {
        val longSide = maxOf(width, height)
        if (longSide <= longEdge) return this

        val scale = longEdge.toFloat() / longSide
        return Bitmap.createScaledBitmap(
            this,
            (width * scale).roundToInt().coerceAtLeast(1),
            (height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
    }

    private fun extensionFor(mimeType: String): String {
        return when (mimeType) {
            "image/jpeg" -> "jpg"
            else -> mimeType.substringAfter('/').filter { it.isLetterOrDigit() }.ifEmpty { "img" }
        }
    }

    /**
     * Copies this stream to [output] unless it has more than [limit] bytes; returns whether it was copied completely.
     */
    private fun InputStream.copyUpTo(output: OutputStream, limit: Long): Boolean {
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        var total = 0L
        var count = read(buffer)
        while (count >= 0 && total <= limit) {
            output.write(buffer, 0, count)
            total += count
            count = read(buffer)
        }

        return total <= limit
    }
}
