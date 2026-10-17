package com.fsck.k9.ui.compose

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

private const val SAVE_DELAY_MILLIS = 30_000L

/**
 * Starts the saving of the draft once the user has stopped changing the message for a while.
 *
 * Every change moves the save back, so a draft is saved at most once per pause and never while another save is still
 * running. What is saved and how is up to [startSave], which also decides whether a save is due at all.
 *
 * @param startSave starts saving the draft; returns whether a save was started. [onSaveFinished] must be called when
 *   a started save is over.
 * @param clock the time in milliseconds, for tests
 */
class DraftAutoSaver @JvmOverloads constructor(
    private val scheduler: Scheduler,
    private val startSave: () -> Boolean,
    private val clock: () -> Long = SystemClock::uptimeMillis,
) {
    private var lastChangeTime = 0L
    private var isTimerRunning = false
    private var isSaving = false
    private var isStopped = false

    private val timerAction = Runnable { onTimer() }

    /**
     * To be called whenever the message has changed.
     */
    fun onChange() {
        if (isStopped) return

        lastChangeTime = clock()
        if (!isTimerRunning) startTimer(SAVE_DELAY_MILLIS)
    }

    fun onSaveFinished() {
        isSaving = false
    }

    /**
     * Cancels what is scheduled; the saver does nothing afterwards.
     */
    fun stop() {
        isStopped = true
        isTimerRunning = false
        scheduler.cancel(timerAction)
    }

    private fun onTimer() {
        isTimerRunning = false
        if (isStopped) return

        val remaining = lastChangeTime + SAVE_DELAY_MILLIS - clock()
        if (remaining > 0) {
            startTimer(remaining)
        } else if (isSaving) {
            startTimer(SAVE_DELAY_MILLIS)
        } else {
            isSaving = startSave()
        }
    }

    private fun startTimer(delayMillis: Long) {
        isTimerRunning = true
        scheduler.schedule(timerAction, delayMillis)
    }

    /**
     * Runs an action later on the main thread.
     */
    interface Scheduler {
        fun schedule(action: Runnable, delayMillis: Long)
        fun cancel(action: Runnable)
    }

    class MainThreadScheduler : Scheduler {
        private val handler = Handler(Looper.getMainLooper())

        override fun schedule(action: Runnable, delayMillis: Long) {
            handler.postDelayed(action, delayMillis)
        }

        override fun cancel(action: Runnable) {
            handler.removeCallbacks(action)
        }
    }
}
