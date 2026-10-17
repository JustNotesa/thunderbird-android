package com.fsck.k9.ui.compose

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import org.junit.Test

class DraftAutoSaverTest {
    private val scheduler = FakeScheduler()
    private var saves = 0
    private var saveStarts = true

    private fun createTestSubject(): DraftAutoSaver {
        return DraftAutoSaver(
            scheduler = scheduler,
            startSave = {
                saves++
                saveStarts
            },
            clock = { scheduler.now },
        )
    }

    @Test
    fun `a change should start a save after 30 seconds`() {
        // Arrange
        val testSubject = createTestSubject()

        // Act
        testSubject.onChange()
        scheduler.advance(29_999)
        assertThat(saves).isEqualTo(0)
        scheduler.advance(1)

        // Assert
        assertThat(saves).isEqualTo(1)
    }

    @Test
    fun `every change should move the save back`() {
        // Arrange
        val testSubject = createTestSubject()
        testSubject.onChange()
        scheduler.advance(20_000)
        testSubject.onChange()

        // Act
        scheduler.advance(20_000)

        // Assert
        assertThat(saves).isEqualTo(0)
        scheduler.advance(10_000)
        assertThat(saves).isEqualTo(1)
    }

    @Test
    fun `no second save should start while one is running`() {
        // Arrange
        val testSubject = createTestSubject()
        testSubject.onChange()
        scheduler.advance(30_000)
        testSubject.onChange()

        // Act
        scheduler.advance(60_000)

        // Assert
        assertThat(saves).isEqualTo(1)
        testSubject.onSaveFinished()
        scheduler.advance(30_000)
        assertThat(saves).isEqualTo(2)
    }

    @Test
    fun `a save that did not start should not block the next one`() {
        // Arrange
        val testSubject = createTestSubject()
        saveStarts = false
        testSubject.onChange()
        scheduler.advance(30_000)

        // Act
        testSubject.onChange()
        scheduler.advance(30_000)

        // Assert
        assertThat(saves).isEqualTo(2)
    }

    @Test
    fun `stop should cancel the save`() {
        // Arrange
        val testSubject = createTestSubject()
        testSubject.onChange()

        // Act
        testSubject.stop()
        assertThat(scheduler.hasPendingAction).isFalse()
        scheduler.advance(60_000)
        testSubject.onChange()
        scheduler.advance(60_000)

        // Assert
        assertThat(saves).isEqualTo(0)
    }

    private class FakeScheduler : DraftAutoSaver.Scheduler {
        var now = 0L
        val hasPendingAction: Boolean get() = action != null
        private var action: Runnable? = null
        private var dueTime = 0L

        override fun schedule(action: Runnable, delayMillis: Long) {
            this.action = action
            dueTime = now + delayMillis
        }

        override fun cancel(action: Runnable) {
            this.action = null
        }

        fun advance(millis: Long) {
            val target = now + millis
            while (true) {
                val pending = action
                if (pending == null || dueTime > target) break
                now = dueTime
                action = null
                pending.run()
            }
            now = target
        }
    }
}
