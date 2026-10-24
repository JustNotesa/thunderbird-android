package com.fsck.k9.ui.settings

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.Toast
import android.view.accessibility.AccessibilityManager
import androidx.core.view.isVisible
import com.fsck.k9.ui.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator

/**
 * A button that only triggers its action after it was held down for a certain amount of time.
 *
 * Users who can't hold the button down (e.g. because they use a screen reader) can activate the button using the
 * accessibility "click" or "long click" action. In that case the action is triggered right away, but the button still
 * needs to be part of a confirmation dialog.
 */
class HoldToConfirmButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    private val labelButton: MaterialButton
    private val progressIndicator: LinearProgressIndicator
    private val timing = HoldToConfirmTiming(scheduler = HandlerHoldToConfirmScheduler())
    private var confirmListener: (() -> Unit)? = null
    private var isHolding = false

    init {
        LayoutInflater.from(context).inflate(R.layout.hold_to_confirm_button, this, true)
        labelButton = findViewById(R.id.hold_to_confirm_label)
        progressIndicator = findViewById(R.id.hold_to_confirm_progress)

        labelButton.isFocusable = false
        labelButton.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        progressIndicator.isIndeterminate = false

        contentDescription = labelButton.text
        isClickable = true
        isFocusable = true
        isLongClickable = true
    }

    var text: CharSequence
        get() = labelButton.text
        set(value) {
            labelButton.text = value
            contentDescription = value
        }

    fun setOnConfirmListener(listener: () -> Unit) {
        confirmListener = listener
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> startHolding()
            MotionEvent.ACTION_MOVE -> if (!isTouchInsideBounds(event)) cancelHolding()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> cancelHolding()
        }
        return true
    }

    override fun performClick(): Boolean {
        // Screen reader users activate the button using the accessibility action. They can't hold the button down,
        // so the action is triggered right away. Keyboards, D-pads and switch access only get a hint, otherwise a
        // single key press would delete the accounts.
        confirmIfScreenReaderOrHint()

        return super.performClick()
    }

    override fun performLongClick(): Boolean {
        confirmIfScreenReaderOrHint()

        return true
    }

    override fun onDetachedFromWindow() {
        cancelHolding()

        super.onDetachedFromWindow()
    }

    private fun startHolding() {
        isHolding = true
        progressIndicator.isVisible = true
        progressIndicator.setProgressCompat(0, false)
        timing.start(
            onProgress = { progress -> progressIndicator.setProgressCompat(progress, false) },
            onConfirmed = { confirm() },
        )
    }

    private fun cancelHolding() {
        if (!isHolding) return

        isHolding = false
        timing.cancel()
        progressIndicator.isVisible = false
        progressIndicator.setProgressCompat(0, false)
    }

    private fun confirmIfScreenReaderOrHint() {
        val accessibilityManager = context.getSystemService(AccessibilityManager::class.java)
        if (accessibilityManager?.isTouchExplorationEnabled == true) {
            confirm()
        } else {
            Toast.makeText(context, R.string.hold_to_confirm_hint, Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirm() {
        cancelHolding()
        confirmListener?.invoke()
    }

    private fun isTouchInsideBounds(event: MotionEvent): Boolean {
        val isInsideHorizontalBounds = event.x >= 0f && event.x <= width
        val isInsideVerticalBounds = event.y >= 0f && event.y <= height

        return isInsideHorizontalBounds && isInsideVerticalBounds
    }
}

internal class HandlerHoldToConfirmScheduler(
    private val handler: Handler = Handler(Looper.getMainLooper()),
) : HoldToConfirmScheduler {
    override fun schedule(delayMillis: Long, action: () -> Unit): () -> Unit {
        val runnable = Runnable { action() }
        handler.postDelayed(runnable, delayMillis)

        return { handler.removeCallbacks(runnable) }
    }
}
