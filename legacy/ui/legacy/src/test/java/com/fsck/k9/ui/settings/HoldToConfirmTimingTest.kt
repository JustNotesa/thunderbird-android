package com.fsck.k9.ui.settings

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isNotEmpty
import assertk.assertions.isTrue
import org.junit.Test

class HoldToConfirmTimingTest {
    private val scheduler = FakeHoldToConfirmScheduler()
    private val timing = HoldToConfirmTiming(scheduler = scheduler)

    @Test
    fun `hold is confirmed after the hold duration`() {
        var confirmed = false
        timing.start(onProgress = {}, onConfirmed = { confirmed = true })

        scheduler.advanceBy(HoldToConfirmTiming.HOLD_DURATION_MILLIS - 1)

        assertThat(confirmed).isFalse()
        assertThat(timing.isConfirmed).isFalse()

        scheduler.advanceBy(1)

        assertThat(confirmed).isTrue()
        assertThat(timing.isConfirmed).isTrue()
    }

    @Test
    fun `cancelling a hold prevents the confirmation`() {
        var confirmed = false
        timing.start(onProgress = {}, onConfirmed = { confirmed = true })

        scheduler.advanceBy(HoldToConfirmTiming.HOLD_DURATION_MILLIS / 2)
        timing.cancel()
        scheduler.advanceBy(HoldToConfirmTiming.HOLD_DURATION_MILLIS * 2)

        assertThat(confirmed).isFalse()
        assertThat(timing.isConfirmed).isFalse()
    }

    @Test
    fun `a cancelled hold can be started again`() {
        var confirmed = false
        timing.start(onProgress = {}, onConfirmed = { confirmed = true })

        scheduler.advanceBy(HoldToConfirmTiming.HOLD_DURATION_MILLIS / 2)
        timing.cancel()

        timing.start(onProgress = {}, onConfirmed = { confirmed = true })
        scheduler.advanceBy(HoldToConfirmTiming.HOLD_DURATION_MILLIS)

        assertThat(confirmed).isTrue()
    }

    @Test
    fun `progress is reported while the button is held down`() {
        val progressValues = mutableListOf<Int>()

        timing.start(onProgress = { progress -> progressValues.add(progress) }, onConfirmed = {})

        scheduler.advanceBy(HoldToConfirmTiming.HOLD_DURATION_MILLIS)

        assertThat(progressValues).isNotEmpty()
        assertThat(progressValues.last()).isGreaterThan(90)
    }

    @Test
    fun `progress starts from the beginning when the hold is started again`() {
        val progressValues = mutableListOf<Int>()
        timing.start(onProgress = { progress -> progressValues.add(progress) }, onConfirmed = {})

        scheduler.advanceBy(HoldToConfirmTiming.HOLD_DURATION_MILLIS / 2)
        timing.cancel()
        val progressBeforeRestart = progressValues.last()

        progressValues.clear()
        timing.start(onProgress = { progress -> progressValues.add(progress) }, onConfirmed = {})
        scheduler.advanceBy(100)

        assertThat(progressValues.last()).isLessThan(progressBeforeRestart)
    }
}

private class FakeHoldToConfirmScheduler : HoldToConfirmScheduler {
    private var currentTimeMillis = 0L
    private val scheduledActions = mutableListOf<ScheduledAction>()

    override fun schedule(delayMillis: Long, action: () -> Unit): () -> Unit {
        val scheduledAction = ScheduledAction(currentTimeMillis + delayMillis, action)
        scheduledActions.add(scheduledAction)

        return { scheduledActions.remove(scheduledAction) }
    }

    fun advanceBy(millis: Long) {
        val targetTimeMillis = currentTimeMillis + millis

        while (true) {
            val nextAction = scheduledActions.filter { it.timeMillis <= targetTimeMillis }
                .minByOrNull { it.timeMillis } ?: break
            scheduledActions.remove(nextAction)
            currentTimeMillis = nextAction.timeMillis
            nextAction.action()
        }

        currentTimeMillis = targetTimeMillis
    }

    private class ScheduledAction(
        val timeMillis: Long,
        val action: () -> Unit,
    )
}
