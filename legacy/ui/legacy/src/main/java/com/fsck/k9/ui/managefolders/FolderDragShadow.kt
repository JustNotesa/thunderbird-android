package com.fsck.k9.ui.managefolders

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.view.View
import android.widget.TextView
import com.fsck.k9.ui.R
import com.fsck.k9.ui.resolveColorAttribute
import com.google.android.material.R as MaterialR

/**
 * What follows the finger while a folder is moved: the icon and the name of the folder on a card, so it is visible
 * above the other folders.
 */
internal class FolderDragShadow(private val folderView: View) : View.DragShadowBuilder(folderView) {
    private val icon: View = folderView.findViewById(R.id.folder_icon)
    private val name: View = folderView.findViewById(R.id.folder_name)
    private val density = folderView.resources.displayMetrics.density
    private val padding = CARD_PADDING_DP * density
    private val cornerRadius = CARD_CORNER_RADIUS_DP * density

    // The part of the row that is displayed, in the coordinates of the row. Nested folders are indented.
    private val contentStart = icon.left - padding
    private val contentWidth = minOf(
        folderView.width - contentStart,
        name.left + measureName() + padding - contentStart,
    )

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = folderView.context.theme.resolveColorAttribute(MaterialR.attr.colorSurfaceVariant)
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = folderView.context.theme.resolveColorAttribute(MaterialR.attr.colorOutline)
        style = Paint.Style.STROKE
        strokeWidth = CARD_OUTLINE_WIDTH_DP * density
    }

    override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
        outShadowSize.set(contentWidth.toInt(), folderView.height)
        // The finger holds the folder at its icon
        outShadowTouchPoint.set((icon.left + icon.width / 2 - contentStart).toInt(), folderView.height / 2)
    }

    override fun onDrawShadow(canvas: Canvas) {
        val card = RectF(0f, 0f, contentWidth, folderView.height.toFloat())
        canvas.drawRoundRect(card, cornerRadius, cornerRadius, backgroundPaint)

        val outline = RectF(card).apply { inset(outlinePaint.strokeWidth / 2, outlinePaint.strokeWidth / 2) }
        canvas.drawRoundRect(outline, cornerRadius, cornerRadius, outlinePaint)

        canvas.save()
        canvas.clipRect(card)
        canvas.translate(-contentStart, 0f)
        folderView.draw(canvas)
        canvas.restore()
    }

    private fun measureName(): Float {
        val textView = name as? TextView ?: return name.width.toFloat()
        return minOf(textView.paint.measureText(textView.text.toString()), name.width.toFloat())
    }

    private companion object {
        const val CARD_PADDING_DP = 12
        const val CARD_CORNER_RADIUS_DP = 12
        const val CARD_OUTLINE_WIDTH_DP = 1
    }
}
