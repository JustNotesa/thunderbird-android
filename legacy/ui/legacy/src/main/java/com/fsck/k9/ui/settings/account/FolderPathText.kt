package com.fsck.k9.ui.settings.account

import android.content.Context
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.DynamicDrawableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.TypefaceSpan
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import app.k9mail.legacy.ui.folder.FolderIconProvider
import net.thunderbird.feature.mail.folder.FolderType
import net.thunderbird.feature.mail.folder.api.RemoteFolder

private const val ICON_SIZE_DP = 16f
private const val SEGMENT_SEPARATOR = " › "
private const val LABEL_SEPARATOR = "  "
private const val ICON_PLACEHOLDER = " "

/**
 * Builds the text the folder settings show for a folder: one icon per hierarchy level (a special folder's own icon,
 * otherwise a plain folder icon), the level names, and the server's special-use label of the selected folder.
 */
internal fun FolderPathFormatter.buildText(context: Context, folder: RemoteFolder): CharSequence {
    val accentColor = context.resolveColor(androidx.appcompat.R.attr.colorPrimary)
    val regularColor = context.resolveColor(android.R.attr.textColorSecondary)
    val iconProvider = FolderIconProvider()

    val text = SpannableStringBuilder()
    segments(folder).forEachIndexed { index, segment ->
        if (index > 0) text.append(SEGMENT_SEPARATOR)
        val iconColor = if (segment.type == FolderType.REGULAR) regularColor else accentColor
        context.folderIcon(iconProvider.getFolderIcon(segment.type), iconColor)?.let { icon ->
            text.appendWithSpan(ICON_PLACEHOLDER, ImageSpan(icon, DynamicDrawableSpan.ALIGN_BOTTOM))
            text.append(' ')
        }
        text.append(segment.name)
    }

    serverLabel(folder)?.let { label ->
        text.append(LABEL_SEPARATOR)
        val start = text.length
        text.append(label)
        text.setSpan(TypefaceSpan("monospace"), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        accentColor?.let { text.setSpan(ForegroundColorSpan(it), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
    }

    return text
}

private fun SpannableStringBuilder.appendWithSpan(value: String, span: Any) {
    val start = length
    append(value)
    setSpan(span, start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
}

private fun Context.folderIcon(drawableResId: Int, color: Int?): Drawable? {
    val drawable = AppCompatResources.getDrawable(this, drawableResId)?.mutate() ?: return null
    val size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, ICON_SIZE_DP, resources.displayMetrics).toInt()
    drawable.setBounds(0, 0, size, size)
    color?.let { DrawableCompat.setTint(drawable, it) }
    return drawable
}

private fun Context.resolveColor(@AttrRes attr: Int): Int? {
    val typedValue = TypedValue()
    if (!theme.resolveAttribute(attr, typedValue, true)) return null

    return if (typedValue.resourceId != 0) {
        AppCompatResources.getColorStateList(this, typedValue.resourceId)?.defaultColor
    } else {
        typedValue.data
    }
}
