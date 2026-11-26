package net.thunderbird.feature.mail.message.composer.signature

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import net.thunderbird.feature.mail.message.composer.R

/**
 * What the user copied, as far as a signature can use it. Only called when the user explicitly pastes.
 */
fun Context.readClipboardForSignature(): ClipboardContent {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip: ClipData = clipboard?.primaryClip ?: return ClipboardContent()

    var html: String? = null
    val text = StringBuilder()
    val images = mutableListOf<String>()

    for (index in 0 until clip.itemCount) {
        val item = clip.getItemAt(index)
        if (html == null) html = item.htmlText
        item.text?.let {
            if (text.isNotEmpty()) text.append('\n')
            text.append(it)
        }
        val uri = item.uri
        if (uri != null && contentResolver.getType(uri)?.startsWith("image/") == true) {
            images += uri.toString()
        }
    }

    return ClipboardContent(
        html = html,
        plainText = text.toString().takeIf { it.isNotEmpty() },
        imageSources = images,
    )
}

/**
 * The text that tells the user about a paste, in the language of the app.
 */
fun PasteNotice.message(context: Context): String {
    val resources = context.resources
    return when (this) {
        PasteNotice.ActiveContentRemoved ->
            resources.getString(R.string.message_composer_signature_paste_active_content_removed)

        is PasteNotice.ImagesNotDownloaded -> resources.getQuantityString(
            R.plurals.message_composer_signature_paste_images_not_downloaded,
            count,
            count,
        )

        is PasteNotice.ImagesUnreadable -> resources.getQuantityString(
            R.plurals.message_composer_signature_paste_images_unreadable,
            count,
            count,
        )

        is PasteNotice.ImagesTooLarge -> resources.getQuantityString(
            R.plurals.message_composer_signature_paste_images_too_large,
            count,
            count,
            limitBytes / BYTES_PER_KB,
        )

        is PasteNotice.ImagesDownscaled -> resources.getQuantityString(
            R.plurals.message_composer_signature_paste_images_downscaled,
            count,
            count,
        )

        PasteNotice.NothingUsable -> resources.getString(R.string.message_composer_signature_paste_nothing_usable)
    }
}

private const val BYTES_PER_KB = 1024
