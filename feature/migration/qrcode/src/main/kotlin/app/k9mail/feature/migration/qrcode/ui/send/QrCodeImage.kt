package app.k9mail.feature.migration.qrcode.ui.send

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.k9mail.feature.migration.qrcode.R
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

private const val QR_CODE_SIZE = 280
private const val QR_CODE_MARGIN = 1

/**
 * Displays [text] as a QR code the camera of the other device can read.
 */
@Composable
internal fun QrCodeImage(
    text: String,
    modifier: Modifier = Modifier,
) {
    val size = with(LocalDensity.current) { QR_CODE_SIZE.dp.toPx() }.toInt()
    val qrCode = remember(text, size) { text.toQrCodeBitmap(size) }

    Image(
        bitmap = qrCode,
        contentDescription = stringResource(R.string.migration_qrcode_transfer_send_qr_code_description),
        filterQuality = FilterQuality.None,
        modifier = modifier,
    )
}

private fun String.toQrCodeBitmap(size: Int): ImageBitmap {
    val matrix = QRCodeWriter().encode(
        this,
        BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to QR_CODE_MARGIN,
        ),
    )

    return matrix.toImageBitmap()
}

private fun BitMatrix.toImageBitmap(): ImageBitmap {
    val pixels = IntArray(width * height) { index ->
        if (get(index % width, index / width)) Color.Black.toArgb() else Color.White.toArgb()
    }

    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, width, 0, 0, width, height)
    }.asImageBitmap()
}
