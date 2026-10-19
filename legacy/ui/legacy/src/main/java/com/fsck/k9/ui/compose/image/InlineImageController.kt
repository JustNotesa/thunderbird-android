package com.fsck.k9.ui.compose.image

import android.app.Activity
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import com.fsck.k9.activity.compose.AttachmentPresenter
import com.fsck.k9.message.Attachment.LoadingState
import com.fsck.k9.ui.R
import com.fsck.k9.ui.compose.richtext.InlineImageStore
import com.fsck.k9.ui.compose.richtext.MessageFormattingToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import de.cketti.safecontentresolver.SafeContentResolver
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CONTENT_ID_DOMAIN = "thunderbird.local"
internal const val KEY_PENDING_IMAGE_URI = "uri"
internal const val KEY_PENDING_IMAGE_SIZE = "size"

/**
 * Puts images into the text of the message being composed, and gives the text field their pictures.
 *
 * An image the user picked is scaled to the size they chose (off the main thread), becomes an inline attachment of the
 * message under a new Content-ID and is inserted into the text. What can't be done is reported in a Snackbar.
 *
 * The work is bound to the activity: call [cancel] when it is destroyed. An insert that hasn't finished by then isn't
 * lost: [saveState] hands it to the activity of the next configuration, which continues it with [restoreState].
 */
class InlineImageController @JvmOverloads constructor(
    private val activity: Activity,
    private val attachmentPresenter: AttachmentPresenter,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : InlineImageStore {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val scaler = InlineImageScaler(activity.cacheDir)
    private val drawables = mutableMapOf<String, Drawable>()
    private val pendingContentIds = mutableSetOf<String>()
    private val insertsInProgress = mutableListOf<PendingInsert>()
    private var toolbar: MessageFormattingToolbar? = null

    /**
     * Connects the text field the images are inserted into and shown in.
     */
    fun attach(toolbar: MessageFormattingToolbar) {
        this.toolbar = toolbar
    }

    /**
     * Lets the user choose how large the picked image is sent, and then inserts it.
     */
    fun askSizeAndInsert(uri: Uri) {
        val sizes = InlineImageSize.entries
        val labels = sizes.map { size -> activity.getString(size.labelRes()) }.toTypedArray()
        var selected = sizes.indexOf(InlineImageSize.Medium)

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.message_compose_image_size_title)
            .setSingleChoiceItems(labels, selected) { _, which -> selected = which }
            .setPositiveButton(com.fsck.k9.ui.base.R.string.okay_action) { _, _ -> insert(uri, sizes[selected]) }
            .setNegativeButton(com.fsck.k9.ui.base.R.string.cancel_action, null)
            .show()
    }

    /**
     * Scales the image at [uri] and inserts it at the cursor.
     */
    fun insert(uri: Uri, size: InlineImageSize) {
        val insert = PendingInsert(uri, size)
        insertsInProgress.add(insert)
        scope.launch {
            try {
                val result = withContext(workDispatcher) { scaler.scale({ activity.openImage(uri) }, size) }
                when (result) {
                    is ScaledImage.Failed -> activity.showProblem(result.problem)
                    is ScaledImage.Ready -> addToMessage(result)
                }
            } finally {
                insertsInProgress.remove(insert)
            }
        }
    }

    /**
     * To be called when the inline attachments have changed, e.g. those of a draft have been loaded: the images of the
     * text may be shown now.
     */
    fun onAttachmentsChanged() {
        toolbar?.refreshInlineImages()
    }

    /**
     * The images that are being put into the text right now, so that they survive a rotation of the device.
     */
    fun saveState(): ArrayList<Bundle> {
        return ArrayList(insertsInProgress.map { insert -> insert.toBundle() })
    }

    /**
     * Puts the images of [state], saved by [saveState] of the activity before a rotation, into the text again.
     */
    fun restoreState(state: ArrayList<Bundle>?) {
        state.orEmpty().forEach { bundle ->
            val uri = bundle.getString(KEY_PENDING_IMAGE_URI)?.toUri() ?: return@forEach
            val size = InlineImageSize.entries.find { it.name == bundle.getString(KEY_PENDING_IMAGE_SIZE) }
            if (size != null) insert(uri, size)
        }
    }

    fun cancel() {
        scope.cancel()
    }

    override fun drawableFor(contentId: String): Drawable? {
        if (contentId !in drawables) loadDrawable(contentId)

        return drawables[contentId]
    }

    private suspend fun addToMessage(image: ScaledImage.Ready) {
        val contentId = "${UUID.randomUUID()}@$CONTENT_ID_DOMAIN"
        // The picture is decoded before the image is inserted, so that no placeholder is shown for it.
        val drawable = withContext(workDispatcher) { activity.decodeDrawable(image.file) }
        if (drawable != null) drawables[contentId] = drawable

        attachmentPresenter.addInlineImage(image.file, contentId, image.mimeType, image.name)
        toolbar?.insertInlineImage(contentId)
    }

    // The attachment of an image of a draft arrives some time after the text, so the picture may not be there yet.
    private fun loadDrawable(contentId: String) {
        val attachment = attachmentPresenter.inlineAttachments[contentId]
        val path = attachment?.fileName
        if (attachment?.state != LoadingState.COMPLETE || path == null || !pendingContentIds.add(contentId)) return

        scope.launch {
            val drawable = withContext(workDispatcher) { activity.decodeDrawable(File(path)) }
            // Not tried again for this activity: a file that can't be decoded now won't be later.
            if (drawable != null) {
                drawables[contentId] = drawable
                toolbar?.refreshInlineImages()
            }
        }
    }
}

/**
 * An image the user picked that is being put into the text.
 */
private data class PendingInsert(val uri: Uri, val size: InlineImageSize)

private fun PendingInsert.toBundle() = Bundle().apply {
    putString(KEY_PENDING_IMAGE_URI, uri.toString())
    putString(KEY_PENDING_IMAGE_SIZE, size.name)
}

private fun Activity.showProblem(problem: InlineImageProblem) {
    val anchor: View = findViewById(android.R.id.content)
    Snackbar.make(anchor, problem.messageRes(), Snackbar.LENGTH_LONG).show()
}

private fun Activity.openImage(uri: Uri): InputStream? {
    return try {
        SafeContentResolver.newInstance(this).openInputStream(uri)
    } catch (@Suppress("SwallowedException") e: IOException) {
        null
    } catch (@Suppress("SwallowedException") e: SecurityException) {
        null
    }
}

private fun Activity.decodeDrawable(file: File): Drawable? {
    val metrics = resources.displayMetrics
    val bitmap = try {
        decodeForDisplay(file, maxOf(metrics.widthPixels, metrics.heightPixels))
    } catch (@Suppress("SwallowedException") e: IOException) {
        null
    } catch (@Suppress("SwallowedException") e: OutOfMemoryError) {
        null
    }

    return bitmap?.toDrawable(resources)
}

private fun InlineImageSize.labelRes() = when (this) {
    InlineImageSize.Small -> R.string.message_compose_image_size_small
    InlineImageSize.Medium -> R.string.message_compose_image_size_medium
    InlineImageSize.Original -> R.string.message_compose_image_size_original
}

private fun InlineImageProblem.messageRes() = when (this) {
    InlineImageProblem.NotAnImage -> R.string.message_compose_image_error_not_an_image
    InlineImageProblem.Unreadable -> R.string.message_compose_image_error_unreadable
    InlineImageProblem.TooLarge -> R.string.message_compose_image_error_too_large
    InlineImageProblem.OutOfMemory -> R.string.message_compose_image_error_out_of_memory
}
