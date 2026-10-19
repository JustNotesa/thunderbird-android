package com.fsck.k9.ui.compose.image

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.fsck.k9.activity.compose.AttachmentPresenter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.thunderbird.core.android.testing.RobolectricTest
import org.junit.Before
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

// The legacy graphics shadows don't decode images and don't know about transparency.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalCoroutinesApi::class)
class InlineImageControllerTest : RobolectricTest() {
    private val activity = Robolectric.buildActivity(AppCompatActivity::class.java).create().get().apply {
        // The Snackbar a problem is reported with is a Material view.
        setTheme(com.google.android.material.R.style.Theme_MaterialComponents_Light)
    }
    private val attachmentPresenter = FakeAttachmentPresenter()
    private val imageUri = "content://picked/image".toUri()
    private val image = jpeg(width = 100, height = 80)

    @Before
    fun setUp() {
        // The image is read more than once while it is scaled and shown, so every read gets the data again.
        shadowOf(activity.contentResolver).registerInputStreamSupplier(imageUri) { ByteArrayInputStream(image) }
    }

    @Test
    fun `saveState with an insert in progress should hold the image and the size it is scaled to`() = runTest {
        // Arrange
        val testSubject = createController(StandardTestDispatcher(testScheduler))

        // Act
        testSubject.insert(imageUri, InlineImageSize.Small)
        val state = testSubject.saveState()

        // Assert
        assertThat(state).hasSize(1)
        assertThat(state[0].getString(KEY_PENDING_IMAGE_URI)).isEqualTo(imageUri.toString())
        assertThat(state[0].getString(KEY_PENDING_IMAGE_SIZE)).isEqualTo(InlineImageSize.Small.name)
    }

    @Test
    fun `saveState should be empty once the image has been inserted`() = runTest {
        // Arrange
        val testSubject = createController(UnconfinedTestDispatcher(testScheduler))

        // Act
        testSubject.insert(imageUri, InlineImageSize.Small)

        // Assert
        assertThat(testSubject.saveState()).isEmpty()
        assertThat(attachmentPresenter.addedContentIds).hasSize(1)
    }

    @Test
    fun `restoreState should insert the saved image again`() = runTest {
        // Arrange
        val testSubject = createController(StandardTestDispatcher(testScheduler))
        val state = arrayListOf(Bundle().apply {
            putString(KEY_PENDING_IMAGE_URI, imageUri.toString())
            putString(KEY_PENDING_IMAGE_SIZE, InlineImageSize.Medium.name)
        })

        // Act
        testSubject.restoreState(state)

        // Assert
        val restoredState = testSubject.saveState()
        assertThat(restoredState).hasSize(1)
        assertThat(restoredState[0].getString(KEY_PENDING_IMAGE_URI)).isEqualTo(imageUri.toString())
        assertThat(restoredState[0].getString(KEY_PENDING_IMAGE_SIZE)).isEqualTo(InlineImageSize.Medium.name)
    }

    private fun createController(dispatcher: CoroutineDispatcher) =
        InlineImageController(activity, attachmentPresenter, dispatcher, dispatcher)

    private fun jpeg(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        bitmap.setHasAlpha(false)
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)

        return output.toByteArray()
    }
}

/**
 * Records the images that were put into the message; the collaborators it is built with are never used.
 */
private class FakeAttachmentPresenter : AttachmentPresenter(null, null, null, null) {
    val addedContentIds = mutableListOf<String>()

    override fun addInlineImage(file: File, contentId: String, mimeType: String, name: String) {
        addedContentIds += contentId
    }
}
