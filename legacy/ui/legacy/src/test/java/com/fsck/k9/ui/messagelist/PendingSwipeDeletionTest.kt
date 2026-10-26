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

class PendingSwipeDeletionTest {
    private val accountId = AccountIdFactory.of("01a0e8d7-87dc-74a4-a52c-3f507c6dc02c")
    private val deleteCalls = mutableListOf<DeleteCall>()

    private val testSubject = PendingSwipeDeletion { messages, threaded ->
        deleteCalls += DeleteCall(messages, threaded)
    }

    @Test
    fun `start does not delete the messages`() {
        val messages = listOf(createMessageReference(1, "uid-1"))
        val otherMessage = createMessageReference(1, "uid-2")

        testSubject.start(messages, threaded = false)

        assertThat(deleteCalls).isEmpty()
        assertThat(testSubject.hasPending).isTrue()
        assertThat(testSubject.isPending(messages[0])).isTrue()
        assertThat(testSubject.isPending(otherMessage)).isFalse()
    }

    @Test
    fun `start returns increasing ids`() {
        val firstId = testSubject.start(listOf(createMessageReference(1, "uid-1")), threaded = false)
        val secondId = testSubject.start(listOf(createMessageReference(2, "uid-2")), threaded = false)

        assertThat(firstId).isEqualTo(1L)
        assertThat(secondId).isEqualTo(2L)
    }

    @Test
    fun `commit with the correct id deletes the messages`() {
        val messages = listOf(createMessageReference(1, "uid-1"), createMessageReference(2, "uid-2"))
        val id = testSubject.start(messages, threaded = true)

        testSubject.commit(id)

        assertThat(deleteCalls).hasSize(1)
        assertThat(deleteCalls[0].messages).containsExactly(messages[0], messages[1])
        assertThat(deleteCalls[0].threaded).isTrue()
        assertThat(testSubject.hasPending).isFalse()
    }

    @Test
    fun `commit with the correct id twice deletes the messages only once`() {
        val messages = listOf(createMessageReference(1, "uid-1"))
        val id = testSubject.start(messages, threaded = false)

        testSubject.commit(id)
        testSubject.commit(id)

        assertThat(deleteCalls).hasSize(1)
    }

    @Test
    fun `commit with a stale id does nothing`() {
        val staleId = testSubject.start(listOf(createMessageReference(1, "uid-1")), threaded = true)
        testSubject.commit(staleId)
        val messages = listOf(createMessageReference(2, "uid-2"))
        testSubject.start(messages, threaded = false)

        testSubject.commit(staleId)

        assertThat(deleteCalls).hasSize(1)
        assertThat(testSubject.hasPending).isTrue()
        assertThat(testSubject.isPending(messages[0])).isTrue()
    }

    @Test
    fun `undo with the correct id discards the deletion`() {
        val messages = listOf(createMessageReference(1, "uid-1"))
        val id = testSubject.start(messages, threaded = false)

        val result = testSubject.undo(id)

        assertThat(result).isTrue()
        assertThat(deleteCalls).isEmpty()
        assertThat(testSubject.hasPending).isFalse()
        assertThat(testSubject.isPending(messages[0])).isFalse()
    }

    @Test
    fun `commit after undo does not delete the messages`() {
        val messages = listOf(createMessageReference(1, "uid-1"))
        val id = testSubject.start(messages, threaded = false)
        testSubject.undo(id)

        testSubject.commit(id)

        assertThat(deleteCalls).isEmpty()
    }

    @Test
    fun `undo with a stale id returns false and keeps the pending deletion`() {
        val staleId = testSubject.start(listOf(createMessageReference(1, "uid-1")), threaded = true)
        testSubject.commit(staleId)
        val messages = listOf(createMessageReference(2, "uid-2"))
        testSubject.start(messages, threaded = false)

        val result = testSubject.undo(staleId)

        assertThat(result).isFalse()
        assertThat(deleteCalls).hasSize(1)
        assertThat(testSubject.hasPending).isTrue()
        assertThat(testSubject.isPending(messages[0])).isTrue()
    }

    @Test
    fun `start while a deletion is pending deletes the first messages`() {
        val firstMessages = listOf(createMessageReference(1, "uid-1"))
        testSubject.start(firstMessages, threaded = false)
        val secondMessages = listOf(createMessageReference(2, "uid-2"))

        testSubject.start(secondMessages, threaded = true)

        assertThat(deleteCalls).hasSize(1)
        assertThat(deleteCalls[0].messages).containsExactly(firstMessages[0])
        assertThat(deleteCalls[0].threaded).isFalse()
        assertThat(testSubject.hasPending).isTrue()
        assertThat(testSubject.isPending(secondMessages[0])).isTrue()
    }

    @Test
    fun `commit with the old id does not delete the new pending messages`() {
        val firstMessages = listOf(createMessageReference(1, "uid-1"))
        val firstId = testSubject.start(firstMessages, threaded = false)
        val secondMessages = listOf(createMessageReference(2, "uid-2"))
        testSubject.start(secondMessages, threaded = true)

        testSubject.commit(firstId)

        assertThat(deleteCalls).hasSize(1)
        assertThat(deleteCalls[0].messages).containsExactly(firstMessages[0])
        assertThat(testSubject.hasPending).isTrue()
        assertThat(testSubject.isPending(secondMessages[0])).isTrue()
    }

    @Test
    fun `commitPending deletes the pending messages`() {
        val messages = listOf(createMessageReference(1, "uid-1"))
        testSubject.start(messages, threaded = true)

        testSubject.commitPending()

        assertThat(deleteCalls).hasSize(1)
        assertThat(deleteCalls[0].messages).containsExactly(messages[0])
        assertThat(deleteCalls[0].threaded).isTrue()
        assertThat(testSubject.hasPending).isFalse()
    }

    @Test
    fun `commitPending without a pending deletion does nothing`() {
        testSubject.commitPending()

        assertThat(deleteCalls).isEmpty()
    }

    private fun createMessageReference(folderId: Long, uid: String) = MessageReference(accountId, folderId, uid)

    private data class DeleteCall(
        val messages: List<MessageReference>,
        val threaded: Boolean,
    )
}
