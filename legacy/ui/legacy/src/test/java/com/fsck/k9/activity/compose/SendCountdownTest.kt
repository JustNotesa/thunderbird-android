package com.fsck.k9.activity.compose

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import org.junit.Test

class SendCountdownTest {
    private val scheduler = FakeScheduler()
    private val listener = FakeListener()
    private val testSubject = SendCountdown(seconds = SendCountdown.DEFAULT_SECONDS, scheduler = scheduler)

    @Test
    fun `start ticks the full number of seconds right away`() {
        // Act
        testSubject.start(listener)

        // Assert
        assertThat(listener.events).containsExactly("tick 3")
        assertThat(testSubject.isRunning).isTrue()
    }

    @Test
    fun `start schedules the first tick after one second`() {
        // Act
        testSubject.start(listener)

        // Assert
        assertThat(scheduler.scheduledActions).hasSize(1)
        assertThat(scheduler.scheduledActions.single().delayMillis).isEqualTo(1000L)
    }

    @Test
    fun `running all ticks finishes the countdown`() {
        // Arrange
        testSubject.start(listener)

        // Act
        scheduler.runNextAction()
        scheduler.runNextAction()
        scheduler.runNextAction()

        // Assert
        assertThat(listener.events).containsExactly("tick 3", "tick 2", "tick 1", "finished")
        assertThat(testSubject.isRunning).isFalse()
        assertThat(scheduler.scheduledActions).isEmpty()
    }

    @Test
    fun `cancel stops the countdown without sending the message`() {
        // Arrange
        testSubject.start(listener)

        // Act
        val result = testSubject.cancel()

        // Assert
        assertThat(result).isTrue()
        assertThat(listener.events).containsExactly("tick 3")
        assertThat(testSubject.isRunning).isFalse()
        assertThat(scheduler.cancelledActions).hasSize(1)
        assertThat(scheduler.scheduledActions).isEmpty()
    }

    @Test
    fun `a tick delivered after cancel is ignored`() {
        // Arrange
        testSubject.start(listener)
        val lateTick = scheduler.scheduledActions.single().action

        // Act
        testSubject.cancel()
        lateTick.run()

        // Assert
        assertThat(listener.events).containsExactly("tick 3")
        assertThat(testSubject.isRunning).isFalse()
    }

    @Test
    fun `cancel does nothing when no countdown is running`() {
        // Act
        val result = testSubject.cancel()

        // Assert
        assertThat(result).isFalse()
    }

    @Test
    fun `finishNow completes the countdown`() {
        // Arrange
        testSubject.start(listener)

        // Act
        val result = testSubject.finishNow()

        // Assert
        assertThat(result).isTrue()
        assertThat(listener.events).containsExactly("tick 3", "finished")
        assertThat(testSubject.isRunning).isFalse()
        assertThat(scheduler.cancelledActions).hasSize(1)
        assertThat(scheduler.scheduledActions).isEmpty()
    }

    @Test
    fun `a tick delivered after finishNow is ignored`() {
        // Arrange
        testSubject.start(listener)
        val lateTick = scheduler.scheduledActions.single().action

        // Act
        testSubject.finishNow()
        lateTick.run()

        // Assert
        assertThat(listener.events).containsExactly("tick 3", "finished")
        assertThat(testSubject.isRunning).isFalse()
    }

    @Test
    fun `finishNow does nothing when no countdown is running`() {
        // Arrange
        testSubject.start(listener)
        testSubject.finishNow()

        // Act
        val result = testSubject.finishNow()

        // Assert
        assertThat(result).isFalse()
        assertThat(listener.events).containsExactly("tick 3", "finished")
    }

    @Test
    fun `start does nothing while a countdown is running`() {
        // Arrange
        testSubject.start(listener)

        // Act
        testSubject.start(listener)

        // Assert
        assertThat(listener.events).containsExactly("tick 3")
        assertThat(scheduler.scheduledActions).hasSize(1)
    }

    @Test
    fun `start works again after the countdown has finished`() {
        // Arrange
        testSubject.start(listener)
        scheduler.runNextAction()
        scheduler.runNextAction()
        scheduler.runNextAction()
        val newListener = FakeListener()

        // Act
        testSubject.start(newListener)

        // Assert
        assertThat(newListener.events).containsExactly("tick 3")
        assertThat(testSubject.isRunning).isTrue()
    }

    @Test
    fun `a countdown of zero seconds is not allowed`() {
        assertFailure {
            SendCountdown(seconds = 0, scheduler = scheduler)
        }.isInstanceOf<IllegalArgumentException>()
    }
}

private class FakeScheduler : SendCountdown.Scheduler {
    val scheduledActions = mutableListOf<ScheduledAction>()
    val cancelledActions = mutableListOf<Runnable>()

    override fun schedule(delayMillis: Long, action: Runnable) {
        scheduledActions.add(ScheduledAction(delayMillis, action))
    }

    override fun cancel(action: Runnable) {
        cancelledActions.add(action)
        scheduledActions.removeAll { it.action === action }
    }

    fun runNextAction() {
        val scheduledAction = scheduledActions.first()
        scheduledActions.remove(scheduledAction)
        scheduledAction.action.run()
    }

    data class ScheduledAction(val delayMillis: Long, val action: Runnable)
}

private class FakeListener : SendCountdown.Listener {
    val events = mutableListOf<String>()

    override fun onTick(remainingSeconds: Int) {
        events.add("tick $remainingSeconds")
    }

    override fun onFinished() {
        events.add("finished")
    }
}
