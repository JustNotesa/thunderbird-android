package com.fsck.k9.activity.compose

private const val TICK_INTERVAL_MILLIS = 1000L

/**
 * Counts down the time remaining until a message is sent.
 *
 * The countdown is driven by a [Scheduler], so it can be tested without waiting for real time to pass. A running
 * countdown can either be stopped with [cancel] or completed right away with [finishNow].
 */
class SendCountdown(private val seconds: Int, private val scheduler: Scheduler) {
    private var running = false
    private var remainingSeconds = seconds
    private var listener: Listener? = null
    private var pendingTick: Runnable? = null

    init {
        require(seconds >= 1) { "seconds must be at least 1, but was $seconds" }
    }

    /** Whether a countdown is currently running. */
    val isRunning: Boolean
        get() = running

    /**
     * Start the countdown.
     *
     * The listener is immediately told about the full number of seconds. Afterwards it is notified once per second
     * until the countdown has run out. Starting a countdown while another one is running has no effect.
     */
    fun start(listener: Listener) {
        if (running) {
            return
        }

        running = true
        remainingSeconds = seconds
        this.listener = listener

        listener.onTick(seconds)
        if (running) {
            scheduleTick()
        }
    }

    /**
     * Stop the countdown without sending the message.
     *
     * @return `true` if a running countdown was stopped, `false` otherwise.
     */
    fun cancel(): Boolean {
        if (!running) {
            return false
        }

        stopCountdown()
        return true
    }

    /**
     * Stop the countdown and send the message right away.
     *
     * @return `true` if a running countdown was completed, `false` otherwise.
     */
    fun finishNow(): Boolean {
        if (!running) {
            return false
        }

        val currentListener = stopCountdown()
        currentListener?.onFinished()
        return true
    }

    private fun stopCountdown(): Listener? {
        running = false

        val tick = pendingTick
        pendingTick = null
        if (tick != null) {
            scheduler.cancel(tick)
        }

        val currentListener = listener
        listener = null
        return currentListener
    }

    private fun scheduleTick() {
        val tick = object : Runnable {
            override fun run() {
                handleTick(this)
            }
        }
        pendingTick = tick
        scheduler.schedule(TICK_INTERVAL_MILLIS, tick)
    }

    private fun handleTick(tick: Runnable) {
        if (!running || pendingTick !== tick) {
            return
        }

        remainingSeconds--
        if (remainingSeconds > 0) {
            listener?.onTick(remainingSeconds)
            if (running) {
                scheduleTick()
            }
        } else {
            // The tick has already run, so there's nothing left to unschedule.
            pendingTick = null
            val currentListener = stopCountdown()
            currentListener?.onFinished()
        }
    }

    /** Schedules the countdown ticks. */
    interface Scheduler {
        /** Schedule [action] to be run after [delayMillis]. */
        fun schedule(delayMillis: Long, action: Runnable)

        /** Cancel a previously scheduled [action]. */
        fun cancel(action: Runnable)
    }

    /** Receives updates about the progress of the countdown. */
    interface Listener {
        /** Called with the number of seconds left. Never called with `0`. */
        fun onTick(remainingSeconds: Int)

        /** Called when the countdown has run out. */
        fun onFinished()
    }

    companion object {
        /** Default number of seconds the countdown runs for. */
        const val DEFAULT_SECONDS = 3
    }
}
