package com.fsck.k9.ui.compose.image

import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.IOException
import java.io.InputStream

private const val QUARTER_TURN = 90f
private const val HALF_TURN = 180f

/**
 * The matrix that turns an image upright as its EXIF orientation says (a photo is stored the way the sensor saw it),
 * or `null` if it is upright already or the orientation is not known.
 */
internal fun readOrientationMatrix(stream: InputStream): Matrix? {
    val orientation = try {
        ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } catch (@Suppress("SwallowedException") e: IOException) {
        ExifInterface.ORIENTATION_NORMAL
    }

    return orientationMatrix(orientation)
}

internal fun orientationMatrix(orientation: Int): Matrix? {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(HALF_TURN)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(QUARTER_TURN)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(QUARTER_TURN)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(-QUARTER_TURN)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(-QUARTER_TURN)
        else -> return null
    }

    return matrix
}
