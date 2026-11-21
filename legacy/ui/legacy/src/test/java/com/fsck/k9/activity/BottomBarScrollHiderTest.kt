package com.fsck.k9.activity

import android.content.Context
import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import net.thunderbird.core.android.testing.RobolectricTest
import org.junit.Before
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import java.time.Duration

private const val BAR_WIDTH = 100
private const val BAR_HEIGHT = 120
private val IDLE_TIME = Duration.ofSeconds(1)

class BottomBarScrollHiderTest : RobolectricTest() {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val bar = View(context)
    private val scrollHider = BottomBarScrollHider(bar)

    @Before
    fun setUp() {
        bar.measure(
            View.MeasureSpec.makeMeasureSpec(BAR_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(BAR_HEIGHT, View.MeasureSpec.EXACTLY),
        )
        bar.layout(0, 0, BAR_WIDTH, BAR_HEIGHT)
    }

    @Test
    fun `scrolling down should hide the bar`() {
        scrollHider.onScrolled(10)

        assertThat(scrollHider.isHidden).isTrue()

        idle()

        assertThat(bar.translationY).isEqualTo(BAR_HEIGHT.toFloat())
    }

    @Test
    fun `scrolling up should show the bar again`() {
        scrollHider.onScrolled(10)
        idle()

        scrollHider.onScrolled(-10)

        assertThat(scrollHider.isHidden).isFalse()

        idle()

        assertThat(bar.translationY).isEqualTo(0f)
    }

    @Test
    fun `scrolling without a change should leave the bar alone`() {
        scrollHider.onScrolled(0)

        assertThat(scrollHider.isHidden).isFalse()

        idle()

        assertThat(bar.translationY).isEqualTo(0f)
    }

    @Test
    fun `resetting a hidden bar should show it again`() {
        scrollHider.onScrolled(10)
        idle()

        scrollHider.reset()

        assertThat(scrollHider.isHidden).isFalse()
        assertThat(bar.translationY).isEqualTo(0f)
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idleFor(IDLE_TIME)
    }
}
