package com.fsck.k9.ui.settings.account

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.ReplacementSpan
import android.text.style.TypefaceSpan
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import app.k9mail.legacy.ui.folder.FolderIconProvider
import net.thunderbird.feature.mail.folder.FolderType
import net.thunderbird.feature.mail.folder.api.RemoteFolder
import kotlin.math.roundToInt

/** Separator between two levels. The no-break space keeps the arrow on the line of the level before it. */
private const val SEGMENT_SEPARATOR = "\u00A0› "
private const val LABEL_SEPARATOR = "  "
private const val ICON_PLACEHOLDER = "\u00A0"
private const val NO_BREAK_SPACE = '\u00A0'

/** Keeps an icon on the line of the name after it; line breakers may otherwise break after a replacement span. */
private const val WORD_JOINER = "\u2060"
private const val ICON_SIZE_FACTOR = 1.15f
private const val ICON_GAP_FACTOR = 0.3f

/**
 * Builds the text the folder settings show for a folder: one icon per hierarchy level (a special folder's own icon,
 * otherwise a plain folder icon), the level names, and the server's special-use label of the selected folder.
 *
 * Icon and name of a level never get separated by a line break; a long path wraps between levels.
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
            text.appendWithSpan(ICON_PLACEHOLDER, CenteredIconSpan(icon))
            text.append(WORD_JOINER)
        }
        text.append(segment.name.replace(' ', NO_BREAK_SPACE))
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

/**
 * Draws an icon sized to the surrounding text and centered on it, followed by a small gap. Works on every supported
 * API level (ImageSpan.ALIGN_CENTER needs API 29) and does not change the line height.
 */
private class CenteredIconSpan(private val icon: Drawable) : ReplacementSpan() {
    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        val iconSize = iconSize(paint)
        return iconSize + (iconSize * ICON_GAP_FACTOR).roundToInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        val iconSize = iconSize(paint)
        val metrics = paint.fontMetricsInt
        val textCenter = y + (metrics.ascent + metrics.descent) / 2f
        icon.setBounds(0, 0, iconSize, iconSize)

        canvas.save()
        canvas.translate(x, textCenter - iconSize / 2f)
        icon.draw(canvas)
        canvas.restore()
    }

    private fun iconSize(paint: Paint): Int = (paint.textSize * ICON_SIZE_FACTOR).roundToInt()
}

private fun SpannableStringBuilder.appendWithSpan(value: String, span: Any) {
    val start = length
    append(value)
    setSpan(span, start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
}

private fun Context.folderIcon(drawableResId: Int, color: Int?): Drawable? {
    val drawable = AppCompatResources.getDrawable(this, drawableResId)?.mutate() ?: return null
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
