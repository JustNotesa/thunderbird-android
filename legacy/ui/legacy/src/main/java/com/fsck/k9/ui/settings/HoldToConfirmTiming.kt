package com.fsck.k9.ui.settings

/**
 * Schedules an action to be executed after a delay.
 */
internal interface HoldToConfirmScheduler {
    /**
     * Schedule [action] to be executed after [delayMillis].
     *
     * @return A function that cancels the scheduled action.
     */
    fun schedule(delayMillis: Long, action: () -> Unit): () -> Unit
}

/**
 * Timing logic of a hold-to-confirm button.
 *
 * The hold is confirmed after the button was held down for [HOLD_DURATION_MILLIS]. While the button is held down,
 * the progress is reported to allow displaying a progress indicator.
 */
internal class HoldToConfirmTiming(
    private val scheduler: HoldToConfirmScheduler,
    private val holdDurationMillis: Long = HOLD_DURATION_MILLIS,
    private val tickIntervalMillis: Long = TICK_INTERVAL_MILLIS,
) {
    private var remainingMillis = holdDurationMillis
    private var cancelScheduledAction: (() -> Unit)? = null
    private var confirmed = false

    val isConfirmed: Boolean
        get() = confirmed

    /**
     * Start the hold.
     *
     * @param onProgress Called with a value between 0 and 100 while the button is held down.
     * @param onConfirmed Called when the button was held down for the full duration.
     */
    fun start(onProgress: (Int) -> Unit, onConfirmed: () -> Unit) {
        cancel()
        scheduleNextTick(onProgress, onConfirmed)
    }

    /**
     * Cancel a running hold. The progress is reset and `onConfirmed` won't be called.
     */
    fun cancel() {
        cancelScheduledAction?.invoke()
        cancelScheduledAction = null
        remainingMillis = holdDurationMillis
        confirmed = false
    }

    private fun scheduleNextTick(onProgress: (Int) -> Unit, onConfirmed: () -> Unit) {
        val delayMillis = minOf(remainingMillis, tickIntervalMillis)
        cancelScheduledAction = scheduler.schedule(delayMillis) {
            remainingMillis -= delayMillis
            if (remainingMillis <= 0L) {
                confirmed = true
                onConfirmed()
            } else {
                onProgress(progressInPercent())
                scheduleNextTick(onProgress, onConfirmed)
            }
        }
    }

    private fun progressInPercent(): Int {
        val elapsedMillis = holdDurationMillis - remainingMillis
        return (elapsedMillis * PROGRESS_MAXIMUM / holdDurationMillis).toInt()
    }

    companion object {
        const val HOLD_DURATION_MILLIS = 2000L

        private const val TICK_INTERVAL_MILLIS = 50L
        private const val PROGRESS_MAXIMUM = 100
    }
}
