package com.fsck.k9.ui.compose

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.fsck.k9.ui.compose.DraftSaveGate.Pending
import org.junit.Test

class DraftSaveGateTest {
    private val testSubject = DraftSaveGate()

    @Test
    fun `a request should be granted while no save is running`() {
        assertThat(testSubject.request(explicit = false)).isTrue()
    }

    @Test
    fun `a request should be held back while a save is running`() {
        testSubject.onSaveStarted()

        assertThat(testSubject.request(explicit = false)).isFalse()
    }

    @Test
    fun `the held back request should be handed back when the save is over`() {
        testSubject.onSaveStarted()
        testSubject.request(explicit = false)

        val pending = testSubject.onSaveFinished()

        assertThat(pending).isEqualTo(Pending.IMPLICIT)
        assertThat(testSubject.request(explicit = false)).isTrue()
    }

    @Test
    fun `nothing should be pending when no request was held back`() {
        testSubject.onSaveStarted()

        assertThat(testSubject.onSaveFinished()).isEqualTo(Pending.NONE)
    }

    @Test
    fun `an explicit request should not be downgraded by a later implicit one`() {
        testSubject.onSaveStarted()
        testSubject.request(explicit = true)
        testSubject.request(explicit = false)

        assertThat(testSubject.onSaveFinished()).isEqualTo(Pending.EXPLICIT)
    }

    @Test
    fun `the pending request should be forgotten once it was handed back`() {
        testSubject.onSaveStarted()
        testSubject.request(explicit = true)
        testSubject.onSaveFinished()
        testSubject.onSaveStarted()

        assertThat(testSubject.onSaveFinished()).isEqualTo(Pending.NONE)
    }
}
