package com.fsck.k9.ui.messagelist

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import net.thunderbird.feature.account.AccountIdFactory
import org.junit.Test

class ReadStateRestoreTest {
    private val accountId = AccountIdFactory.of("01a0e8d7-87dc-74a4-a52c-3f507c6dc02c")
    private val otherAccountId = AccountIdFactory.of("01a0e8d7-87dc-74a4-a52c-3f507c6dc02d")

    @Test
    fun `mixed selection is restored to the previous state of each message`() {
        val changes = listOf(
            change(1, wasRead = false),
            change(2, wasRead = true),
            change(3, wasRead = false),
        )

        val result = restoreReadStateActions(changes, markedRead = true)

        // message 2 was read already, so it isn't touched
        assertThat(result).containsExactlyInAnyOrder(
            ReadStateRestoreAction(accountId, listOf(1, 3), isThread = false, read = false),
        )
    }

    @Test
    fun `marking as unread restores the messages that were read`() {
        val changes = listOf(change(1, wasRead = true), change(2, wasRead = false), change(3, wasRead = true))

        val result = restoreReadStateActions(changes, markedRead = false)

        assertThat(result).containsExactlyInAnyOrder(
            ReadStateRestoreAction(accountId, listOf(1, 3), isThread = false, read = true),
        )
    }

    @Test
    fun `accounts and threads are restored separately`() {
        val changes = listOf(
            change(1, wasRead = false),
            change(2, wasRead = false, isThread = true),
            change(3, wasRead = false, account = otherAccountId),
        )

        val result = restoreReadStateActions(changes, markedRead = true)

        assertThat(result).containsExactlyInAnyOrder(
            ReadStateRestoreAction(accountId, listOf(1), isThread = false, read = false),
            ReadStateRestoreAction(accountId, listOf(2), isThread = true, read = false),
            ReadStateRestoreAction(otherAccountId, listOf(3), isThread = false, read = false),
        )
    }

    @Test
    fun `nothing to restore when no state changed`() {
        val changes = listOf(change(1, wasRead = true), change(2, wasRead = true))

        val result = restoreReadStateActions(changes, markedRead = true)

        assertThat(result).isEmpty()
    }

    @Test
    fun `nothing to restore for an empty list`() {
        assertThat(restoreReadStateActions(emptyList(), markedRead = false)).isEmpty()
    }

    private fun change(
        id: Long,
        wasRead: Boolean,
        isThread: Boolean = false,
        account: net.thunderbird.feature.account.AccountId = accountId,
    ) = ReadStateChange(account, id, isThread, wasRead)
}
