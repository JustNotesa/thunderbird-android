package com.fsck.k9.view

import android.content.Context
import android.util.AttributeSet
import android.view.View.MeasureSpec
import android.widget.ScrollView

/**
 * A [ScrollView] that is only as high as its content, but never higher than [maxHeight]. A taller content scrolls.
 */
class MaxHeightScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ScrollView(context, attrs) {
    var maxHeight: Int = Int.MAX_VALUE
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val cappedHeightSpec = MeasureSpec.makeMeasureSpec(
            minOf(MeasureSpec.getSize(heightMeasureSpec), maxHeight),
            MeasureSpec.AT_MOST,
        )
        super.onMeasure(widthMeasureSpec, cappedHeightSpec)
    }
}
