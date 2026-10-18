package com.fsck.k9.ui.compose

import android.content.Context
import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import net.thunderbird.core.android.testing.RobolectricTest
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import java.time.Duration

private const val AT_REST_COLOR = 0xFFFFFFFF.toInt()
private const val SCROLLED_COLOR = 0xFFF3F3F3.toInt()
private const val SCROLL_OFFSET = 24
private const val ANIMATION_DURATION = 150L

/**
 * The app bar of the composer starts out as one surface with the message area and is only tinted once the message is
 * scrolled - and it isn't tinted again by every scroll event in between.
 */
class AppBarScrollTintTest : RobolectricTest() {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val appBar = ColorRecordingView(context)
    private val toolbar = ColorRecordingView(context)
    private val testSubject = AppBarScrollTint(appBar, toolbar, AT_REST_COLOR, SCROLLED_COLOR)

    @Test
    fun `the app bar should have the colour of the message area while nothing is scrolled`() {
        assertThat(appBar.currentColor).isEqualTo(AT_REST_COLOR)
        assertThat(toolbar.currentColor).isEqualTo(AT_REST_COLOR)
    }

    @Test
    fun `the app bar should be tinted while the message is scrolled`() {
        testSubject.onScrolled(SCROLL_OFFSET)

        idleFor(ANIMATION_DURATION)

        assertThat(appBar.currentColor).isEqualTo(SCROLLED_COLOR)
        assertThat(toolbar.currentColor).isEqualTo(SCROLLED_COLOR)
    }

    @Test
    fun `the app bar should lose its tint when the message is back at the top`() {
        testSubject.onScrolled(SCROLL_OFFSET)
        idleFor(ANIMATION_DURATION)

        testSubject.onScrolled(0)
        idleFor(ANIMATION_DURATION)

        assertThat(appBar.currentColor).isEqualTo(AT_REST_COLOR)
        assertThat(toolbar.currentColor).isEqualTo(AT_REST_COLOR)
    }

    @Test
    fun `scrolling on within the message should not tint the app bar again`() {
        testSubject.onScrolled(SCROLL_OFFSET)
        idleFor(ANIMATION_DURATION)
        val appliedColors = appBar.colors.size

        testSubject.onScrolled(SCROLL_OFFSET + SCROLL_OFFSET)
        idleFor(ANIMATION_DURATION)

        assertThat(appBar.colors).hasSize(appliedColors)
    }

    @Test
    fun `scrolling on at the top should not draw any colour`() {
        testSubject.onScrolled(0)
        idleFor(ANIMATION_DURATION)

        assertThat(appBar.colors).hasSize(1)
    }

    private fun idleFor(millis: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
    }

    /** A view that remembers every colour it was given, so that the test sees every step of the animation. */
    private class ColorRecordingView(context: Context) : View(context) {
        val colors = mutableListOf<Int>()
        val currentColor: Int get() = colors.last()

        override fun setBackgroundColor(color: Int) {
            super.setBackgroundColor(color)
            colors += color
        }
    }
}
