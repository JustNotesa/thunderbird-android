package com.fsck.k9.ui.fab

import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.updatePadding
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.google.android.material.floatingactionbutton.FloatingActionButton
import net.thunderbird.core.android.testing.RobolectricTest
import org.junit.Test
import org.robolectric.Robolectric

private const val PARENT_WIDTH = 1000
private const val PARENT_HEIGHT = 2000
private const val BOTTOM_MARGIN = 16

class HideFabOnScrollBehaviorTest : RobolectricTest() {
    private val activity = Robolectric.buildActivity(AppCompatActivity::class.java).create().get()
    private val context = ContextThemeWrapper(
        activity,
        com.google.android.material.R.style.Theme_Material3_Light_NoActionBar,
    )

    @Test
    fun `hidden FAB should be moved below the bottom padding of the CoordinatorLayout`() {
        val bottomBarHeight = 100
        val (child, behavior) = createLayout(bottomBarHeight = bottomBarHeight)

        behavior.slideDown(child, false)

        val expectedTranslationY = child.height + BOTTOM_MARGIN + bottomBarHeight
        assertThat(child.translationY).isEqualTo(expectedTranslationY.toFloat())
    }

    @Test
    fun `hidden FAB should not be moved down when the CoordinatorLayout has no bottom padding`() {
        val (child, behavior) = createLayout(bottomBarHeight = 0)

        behavior.slideDown(child, false)

        val expectedTranslationY = child.height + BOTTOM_MARGIN
        assertThat(child.translationY).isEqualTo(expectedTranslationY.toFloat())
    }

    private fun createLayout(bottomBarHeight: Int): Pair<FloatingActionButton, HideFabOnScrollBehavior> {
        val parent = CoordinatorLayout(context)
        parent.clipToPadding = false
        parent.updatePadding(bottom = bottomBarHeight)

        val behavior = HideFabOnScrollBehavior(context, Robolectric.buildAttributeSet().build())
        val child = FloatingActionButton(context)
        val layoutParams = CoordinatorLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        layoutParams.gravity = Gravity.BOTTOM or Gravity.END
        layoutParams.bottomMargin = BOTTOM_MARGIN
        layoutParams.behavior = behavior
        child.layoutParams = layoutParams

        parent.addView(child)
        parent.measure(
            View.MeasureSpec.makeMeasureSpec(PARENT_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(PARENT_HEIGHT, View.MeasureSpec.EXACTLY),
        )
        parent.layout(0, 0, PARENT_WIDTH, PARENT_HEIGHT)

        return child to behavior
    }
}
