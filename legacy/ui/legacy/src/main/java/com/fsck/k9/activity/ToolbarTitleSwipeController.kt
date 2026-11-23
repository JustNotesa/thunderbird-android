package com.fsck.k9.activity

import android.annotation.SuppressLint
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.OvershootInterpolator
import kotlin.math.abs

/**
 * Lets the user switch the one-handed mode by swiping the title of the toolbar down or up.
 *
 * The toolbar gives way a little while the finger moves. When the mode switches, it slides across the screen to its
 * new edge, and a short tick is felt when it arrives.
 *
 * @param appBar The toolbar container that moves.
 * @param titleView The view that is swiped.
 * @param canToggle Whether the gesture may switch the mode right now.
 * @param isAtBottom Whether the toolbar is currently at the bottom.
 * @param toggle Switches the mode and puts the toolbar at its new edge. The controller animates the move.
 * @param onDocked Called when the toolbar has arrived at its new edge, with the new state of the mode.
 */
internal class ToolbarTitleSwipeController(
    private val appBar: View,
    private val titleView: View,
    private val canToggle: () -> Boolean,
    private val isAtBottom: () -> Boolean,
    private val toggle: (turnOn: Boolean) -> Unit,
    private val onDocked: (turnedOn: Boolean) -> Unit,
) {
    private val density = titleView.resources.displayMetrics.density
    private val thresholdPx = OneHandedModeGesture.VERTICAL_THRESHOLD_DP * density
    private val maxDragPx = OneHandedModeGesture.MAX_DRAG_DP * density
    private val tapSlopPx = TAP_SLOP_DP * density

    private var downX = 0f
    private var downY = 0f
    private var isTracking = false

    @SuppressLint("ClickableViewAccessibility")
    fun attach() {
        titleView.setOnTouchListener { view, event -> onTouch(view, event) }
    }

    private fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isTracking = canToggle()
                downX = event.rawX
                downY = event.rawY
            }

            MotionEvent.ACTION_MOVE -> if (isTracking) drag(view, event)
            MotionEvent.ACTION_UP -> if (isTracking) release(view, event)
            MotionEvent.ACTION_CANCEL -> if (isTracking) settleBack()
        }

        return isTracking
    }

    private fun drag(view: View, event: MotionEvent) {
        val dx = event.rawX - downX
        val dy = event.rawY - downY
        if (abs(dy) < abs(dx) * OneHandedModeGesture.VERTICAL_DOMINANCE) return

        view.parent?.requestDisallowInterceptTouchEvent(true)
        appBar.animate().cancel()
        appBar.translationY = OneHandedModeGesture.dragOffset(dy, isAtBottom(), maxDragPx)
    }

    private fun release(view: View, event: MotionEvent) {
        isTracking = false

        val dx = event.rawX - downX
        val dy = event.rawY - downY
        val decision = OneHandedModeGesture.decide(dx, dy, thresholdPx, isAtBottom())
        when (decision) {
            OneHandedModeToggle.TURN_ON -> switchMode(turnOn = true)
            OneHandedModeToggle.TURN_OFF -> switchMode(turnOn = false)
            OneHandedModeToggle.NONE -> {
                settleBack()
                if (abs(dx) < tapSlopPx && abs(dy) < tapSlopPx) view.performClick()
            }
        }
    }

    private fun settleBack() {
        isTracking = false
        appBar.animate().cancel()
        appBar.animate().translationY(0f).setDuration(SETTLE_DURATION_MS).start()
    }

    private fun switchMode(turnOn: Boolean) {
        val visualTopBefore = appBar.top + appBar.translationY
        appBar.animate().cancel()

        toggle(turnOn)

        if (!areAnimationsEnabled()) {
            appBar.translationY = 0f
            appBar.post { onDocked(turnOn) }
            return
        }

        appBar.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    appBar.viewTreeObserver.removeOnPreDrawListener(this)
                    appBar.translationY = visualTopBefore - appBar.top
                    appBar.animate()
                        .translationY(0f)
                        .setDuration(SLIDE_DURATION_MS)
                        .setInterpolator(OvershootInterpolator(OVERSHOOT_TENSION))
                        .withEndAction { onDocked(turnOn) }
                        .start()
                    return true
                }
            },
        )
    }

    private fun areAnimationsEnabled(): Boolean {
        val scale = Settings.Global.getFloat(
            appBar.context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
        return scale > 0f
    }

    companion object {
        private const val TAP_SLOP_DP = 8
        private const val SETTLE_DURATION_MS = 150L
        private const val SLIDE_DURATION_MS = 280L
        private const val OVERSHOOT_TENSION = 0.8f

        /** The tick that is felt when the toolbar docks, the same one the message list uses for its reach area. */
        fun dockedHapticFeedbackConstant(): Int {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }
        }
    }
}
