package com.fsck.k9.ui.compose.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * The power of two by which an image of [longSide] pixels can be shrunk while it still has at least [target] pixels
 * on its longer side.
 */
internal fun sampleSizeFor(longSide: Int, target: Int): Int {
    var sampleSize = 1
    while (target > 0 && longSide / (sampleSize * 2) >= target) {
        sampleSize *= 2
    }

    return sampleSize
}

/**
 * Reads the size and type of the image without decoding it.
 */
internal fun readImageBounds(openStream: () -> InputStream?): BitmapFactory.Options {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val stream = openStream() ?: throw FileNotFoundException("The image can't be opened")
    stream.use { BitmapFactory.decodeStream(it, null, options) }

    return options
}

/**
 * This bitmap turned as [matrix] says, or itself without a matrix.
 */
internal fun Bitmap.turned(matrix: Matrix?): Bitmap {
    return if (matrix == null) this else Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

/**
 * The image of [file] to show on screen: upright and not much larger than [maxEdge] pixels on its longer side, or
 * `null` if it can't be decoded.
 */
internal fun decodeForDisplay(file: File, maxEdge: Int): Bitmap? {
    val bounds = readImageBounds { file.inputStream() }
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(maxOf(bounds.outWidth, bounds.outHeight), maxEdge)
    }
    val bitmap = file.inputStream().use { BitmapFactory.decodeStream(it, null, options) }
    val matrix = file.inputStream().use { readOrientationMatrix(it) }

    return bitmap?.turned(matrix)
}
