package com.fsck.k9.ui.compose.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import kotlin.io.path.createTempDirectory
import net.thunderbird.core.android.testing.RobolectricTest
import org.junit.Test
import org.robolectric.annotation.GraphicsMode

// The legacy graphics shadows don't decode images and don't know about transparency.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InlineImageScalerTest : RobolectricTest() {
    private val outputDirectory = createTempDirectory("scaler-test").toFile().apply { deleteOnExit() }
    private val testSubject = InlineImageScaler(outputDirectory)

    @Test
    fun `scale to Small should limit the long edge to 640 pixels and keep the proportions`() {
        // Arrange
        val image = jpeg(width = 2000, height = 1000)

        // Act
        val result = testSubject.scale({ ByteArrayInputStream(image) }, InlineImageSize.Small).ready()

        // Assert
        assertThat(result.width).isEqualTo(640)
        assertThat(result.height).isEqualTo(320)
        assertThat(decodedSize(result.file)).isEqualTo(640 to 320)
        assertThat(result.mimeType).isEqualTo("image/jpeg")
    }

    @Test
    fun `scale to Medium should limit the long edge of a portrait image to 1280 pixels`() {
        // Arrange
        val image = jpeg(width = 1500, height = 3000)

        // Act
        val result = testSubject.scale({ ByteArrayInputStream(image) }, InlineImageSize.Medium).ready()

        // Assert
        assertThat(decodedSize(result.file)).isEqualTo(640 to 1280)
    }

    @Test
    fun `scale should not enlarge an image that is smaller than the limit`() {
        // Arrange
        val image = jpeg(width = 300, height = 200)

        // Act
        val result = testSubject.scale({ ByteArrayInputStream(image) }, InlineImageSize.Medium).ready()

        // Assert
        assertThat(decodedSize(result.file)).isEqualTo(300 to 200)
    }

    @Test
    fun `scale should keep a PNG with transparency a PNG`() {
        // Arrange
        val image = png(width = 800, height = 400, alpha = 128)

        // Act
        val result = testSubject.scale({ ByteArrayInputStream(image) }, InlineImageSize.Small).ready()

        // Assert
        assertThat(result.mimeType).isEqualTo("image/png")
        assertThat(result.name).isEqualTo("image.png")
        assertThat(BitmapFactory.decodeFile(result.file.path).hasAlpha()).isTrue()
        assertThat(decodedSize(result.file)).isEqualTo(640 to 320)
    }

    @Test
    fun `scale should write an image without transparency as JPEG even if it was a PNG`() {
        // Arrange
        val image = png(width = 800, height = 400, alpha = 255)

        // Act
        val result = testSubject.scale({ ByteArrayInputStream(image) }, InlineImageSize.Small).ready()

        // Assert
        assertThat(result.mimeType).isEqualTo("image/jpeg")
        assertThat(result.name).isEqualTo("image.jpg")
    }

    @Test
    fun `scale should turn an image upright as its EXIF orientation says`() {
        // Arrange: stored as 2000x1000, to be shown turned by 90 degrees
        val source = File(outputDirectory, "rotated.jpg")
        source.writeBytes(jpeg(width = 2000, height = 1000))
        ExifInterface(source.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }

        // Act
        val result = testSubject.scale({ source.inputStream() }, InlineImageSize.Small).ready()

        // Assert
        assertThat(decodedSize(result.file)).isEqualTo(320 to 640)
    }

    @Test
    fun `scale to Original should copy the image unchanged`() {
        // Arrange
        val image = jpeg(width = 2000, height = 1000)

        // Act
        val result = testSubject.scale({ ByteArrayInputStream(image) }, InlineImageSize.Original).ready()

        // Assert
        assertThat(result.file.readBytes().toList()).isEqualTo(image.toList())
        assertThat(result.mimeType).isEqualTo("image/jpeg")
        assertThat(result.width).isEqualTo(2000)
    }

    @Test
    fun `scale to Original should refuse an image larger than 25 MB`() {
        // Arrange: a valid image followed by data up to just over the limit
        val image = jpeg(width = 100, height = 100) + ByteArray(26 * 1024 * 1024)

        // Act
        val result = testSubject.scale({ ByteArrayInputStream(image) }, InlineImageSize.Original)

        // Assert
        assertThat(result).isEqualTo(ScaledImage.Failed(InlineImageProblem.TooLarge))
        assertThat(outputDirectory.listFiles { file -> file.name.startsWith("inline-image") }.orEmpty().size)
            .isEqualTo(0)
    }

    @Test
    fun `scale should report data that is not an image`() {
        // Arrange
        val data = "this is not an image".toByteArray()

        // Act
        val result = testSubject.scale({ ByteArrayInputStream(data) }, InlineImageSize.Medium)

        // Assert
        assertThat(result).isEqualTo(ScaledImage.Failed(InlineImageProblem.NotAnImage))
    }

    @Test
    fun `scale should report an image that can't be opened`() {
        // Act
        val result = testSubject.scale({ throw FileNotFoundException() }, InlineImageSize.Medium)

        // Assert
        assertThat(result).isEqualTo(ScaledImage.Failed(InlineImageProblem.Unreadable))
    }

    @Test
    fun `scale should report an image that has no stream`() {
        // Act
        val result = testSubject.scale({ null }, InlineImageSize.Small)

        // Assert
        assertThat(result).isEqualTo(ScaledImage.Failed(InlineImageProblem.Unreadable))
    }

    @Test
    fun `sampleSizeFor should shrink by powers of two while the target is still reached`() {
        assertThat(sampleSizeFor(longSide = 4000, target = 1000)).isEqualTo(4)
        assertThat(sampleSizeFor(longSide = 1999, target = 1000)).isEqualTo(1)
        assertThat(sampleSizeFor(longSide = 100, target = 1000)).isEqualTo(1)
    }

    private fun ScaledImage.ready(): ScaledImage.Ready {
        assertThat(this).isInstanceOf(ScaledImage.Ready::class)
        return this as ScaledImage.Ready
    }

    private fun decodedSize(file: File): Pair<Int, Int> {
        val bitmap = BitmapFactory.decodeFile(file.path)
        return bitmap.width to bitmap.height
    }

    private fun jpeg(width: Int, height: Int) = encode(width, height, Color.RED, Bitmap.CompressFormat.JPEG)

    private fun png(width: Int, height: Int, alpha: Int) =
        encode(width, height, Color.argb(alpha, 0, 0, 255), Bitmap.CompressFormat.PNG)

    private fun encode(width: Int, height: Int, color: Int, format: Bitmap.CompressFormat): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        // An image without transparent pixels is stored without an alpha channel.
        bitmap.setHasAlpha(Color.alpha(color) != 255)
        val output = ByteArrayOutputStream()
        bitmap.compress(format, 90, output)

        return output.toByteArray()
    }
}
