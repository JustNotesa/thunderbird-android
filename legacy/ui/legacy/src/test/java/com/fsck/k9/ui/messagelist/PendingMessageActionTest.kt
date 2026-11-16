package com.fsck.k9.ui.messagelist

import app.k9mail.legacy.message.controller.MessageReference
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import net.thunderbird.feature.account.AccountIdFactory
import org.junit.Test

class PendingMessageActionTest {
    private val accountId = AccountIdFactory.of("01a0e8d7-87dc-74a4-a52c-3f507c6dc02c")
    private val performedActions = mutableListOf<String>()
    private val pendingStates = mutableListOf<Boolean>()

    private val testSubject = PendingMessageAction()

    @Test
    fun `start does not carry out the action`() {
        val messages = listOf(createMessageReference(1, "uid-1"))
        val otherMessage = createMessageReference(1, "uid-2")

        testSubject.start(messages) { performedActions += "first" }

        assertThat(performedActions).isEmpty()
        assertThat(testSubject.hasPending).isTrue()
        assertThat(testSubject.isPending(messages[0])).isTrue()
        assertThat(testSubject.isPending(otherMessage)).isFalse()
    }

    @Test
    fun `start returns increasing ids`() {
        val firstId = testSubject.start(listOf(createMessageReference(1, "uid-1"))) { performedActions += "first" }
        val secondId = testSubject.start(listOf(createMessageReference(2, "uid-2"))) { performedActions += "second" }

        assertThat(firstId).isEqualTo(1L)
        assertThat(secondId).isEqualTo(2L)
    }

    @Test
    fun `commit with the correct id carries out the action`() {
        val messages = listOf(createMessageReference(1, "uid-1"), createMessageReference(2, "uid-2"))
        val id = testSubject.start(messages) { performedActions += "first" }

        testSubject.commit(id)

        assertThat(performedActions).hasSize(1)
        assertThat(performedActions[0]).isEqualTo("first")
        assertThat(testSubject.hasPending).isFalse()
        assertThat(testSubject.isPending(messages[0])).isFalse()
    }

    @Test
    fun `commit with the correct id twice carries out the action only once`() {
        val messages = listOf(createMessageReference(1, "uid-1"))
        val id = testSubject.start(messages) { performedActions += "first" }

        testSubject.commit(id)
        testSubject.commit(id)

        assertThat(performedActions).hasSize(1)
    }

    @Test
    fun `commit with a stale id does nothing`() {
        val staleId = testSubject.start(listOf(createMessageReference(1, "uid-1"))) { performedActions += "first" }
        testSubject.commit(staleId)
        val messages = listOf(createMessageReference(2, "uid-2"))
        testSubject.start(messages) { performedActions += "second" }

        testSubject.commit(staleId)

        assertThat(performedActions).hasSize(1)
        assertThat(performedActions[0]).isEqualTo("first")
        assertThat(testSubject.hasPending).isTrue()
        assertThat(testSubject.isPending(messages[0])).isTrue()
    }

    @Test
    fun `undo with the correct id discards the action`() {
        val messages = listOf(createMessageReference(1, "uid-1"))
        val id = testSubject.start(messages) { performedActions += "first" }

        val result = testSubject.undo(id)

        assertThat(result).isTrue()
        assertThat(performedActions).isEmpty()
        assertThat(testSubject.hasPending).isFalse()
        assertThat(testSubject.isPending(messages[0])).isFalse()
    }

    @Test
    fun `commit after undo does not carry out the action`() {
        val messages = listOf(createMessageReference(1, "uid-1"))
        val id = testSubject.start(messages) { performedActions += "first" }
        testSubject.undo(id)

        testSubject.commit(id)

        assertThat(performedActions).isEmpty()
    }

    @Test
    fun `undo with a stale id returns false and keeps the pending action`() {
        val staleId = testSubject.start(listOf(createMessageReference(1, "uid-1"))) { performedActions += "first" }
        testSubject.commit(staleId)
        val messages = listOf(createMessageReference(2, "uid-2"))
        testSubject.start(messages) { performedActions += "second" }

        val result = testSubject.undo(staleId)

        assertThat(result).isFalse()
        assertThat(performedActions).hasSize(1)
        assertThat(performedActions[0]).isEqualTo("first")
        assertThat(testSubject.hasPending).isTrue()
        assertThat(testSubject.isPending(messages[0])).isTrue()
    }

    @Test
    fun `start while an action is pending carries out the first action`() {
        val firstMessages = listOf(createMessageReference(1, "uid-1"))
        testSubject.start(firstMessages) { performedActions += "first" }
        val secondMessages = listOf(createMessageReference(2, "uid-2"))

        testSubject.start(secondMessages) { performedActions += "second" }

        assertThat(performedActions).containsExactly("first")
        assertThat(testSubject.hasPending).isTrue()
        assertThat(testSubject.isPending(secondMessages[0])).isTrue()
        assertThat(testSubject.isPending(firstMessages[0])).isFalse()
    }

    @Test
    fun `commit with the old id does not carry out the new pending action`() {
        val firstMessages = listOf(createMessageReference(1, "uid-1"))
        val firstId = testSubject.start(firstMessages) { performedActions += "first" }
        val secondMessages = listOf(createMessageReference(2, "uid-2"))
        testSubject.start(secondMessages) { performedActions += "second" }

        testSubject.commit(firstId)

        assertThat(performedActions).containsExactly("first")
        assertThat(testSubject.hasPending).isTrue()
        assertThat(testSubject.isPending(secondMessages[0])).isTrue()
    }

    @Test
    fun `commitPending carries out the pending action`() {
        val messages = listOf(createMessageReference(1, "uid-1"))
        testSubject.start(messages) { performedActions += "first" }

        testSubject.commitPending()

        assertThat(performedActions).hasSize(1)
        assertThat(performedActions[0]).isEqualTo("first")
        assertThat(testSubject.hasPending).isFalse()
    }

    @Test
    fun `commitPending without a pending action does nothing`() {
        testSubject.commitPending()

        assertThat(performedActions).isEmpty()
    }

    @Test
    fun `carrying out an action clears the pending state first`() {
        val id = testSubject.start(listOf(createMessageReference(1, "uid-1"))) {
            pendingStates += testSubject.hasPending
        }

        testSubject.commit(id)

        assertThat(pendingStates).containsExactly(false)
    }

    @Test
    fun `pendingMessages is empty when there is no pending action`() {
        assertThat(testSubject.pendingMessages).isEmpty()
    }

    @Test
    fun `pendingMessages contains the messages of the pending action`() {
        val messages = listOf(createMessageReference(1, "uid-1"), createMessageReference(2, "uid-2"))
        testSubject.start(messages) { performedActions += "first" }

        assertThat(testSubject.pendingMessages).isEqualTo(messages)

        testSubject.commitPending()

        assertThat(testSubject.pendingMessages).isEmpty()
    }

    private fun createMessageReference(folderId: Long, uid: String) = MessageReference(accountId, folderId, uid)
}
