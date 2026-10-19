package com.fsck.k9.ui.messagelist

import app.k9mail.legacy.message.controller.MessageReference
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import net.thunderbird.core.android.testing.RobolectricTest
import net.thunderbird.feature.account.AccountIdFactory
import org.junit.Test

private val ACCOUNT_ID = AccountIdFactory.of("6b84207b-25de-4dab-97c3-953bbf03fec6")
private val OTHER_ACCOUNT_ID = AccountIdFactory.of("a1f6e1c4-70c5-4b5e-9d0a-3f6b0a7f5d11")

class MessageListLoaderTest : RobolectricTest() {
    @Test
    fun `buildIncludedMessagesSelections should return nothing without included messages`() {
        val result = buildIncludedMessagesSelections(ACCOUNT_ID, emptySet())

        assertThat(result).isEmpty()
    }

    @Test
    fun `buildIncludedMessagesSelections should create one selection per folder`() {
        val includedMessages = setOf(
            MessageReference(ACCOUNT_ID, 1L, "10"),
            MessageReference(ACCOUNT_ID, 1L, "11"),
            MessageReference(ACCOUNT_ID, 2L, "20"),
        )

        val result = buildIncludedMessagesSelections(ACCOUNT_ID, includedMessages)

        assertThat(result).containsExactlyInAnyOrder(
            "folder_id = 1 AND uid IN ('10','11')",
            "folder_id = 2 AND uid IN ('20')",
        )
    }

    @Test
    fun `buildIncludedMessagesSelections should ignore messages of other accounts`() {
        val includedMessages = setOf(MessageReference(OTHER_ACCOUNT_ID, 1L, "10"))

        val result = buildIncludedMessagesSelections(ACCOUNT_ID, includedMessages)

        assertThat(result).isEmpty()
    }

    @Test
    fun `buildIncludedMessagesSelections should escape message uids`() {
        val includedMessages = setOf(MessageReference(ACCOUNT_ID, 1L, "1') OR ('1'='1"))

        val result = buildIncludedMessagesSelections(ACCOUNT_ID, includedMessages)

        assertThat(result).containsExactlyInAnyOrder("folder_id = 1 AND uid IN ('1'') OR (''1''=''1')")
    }
}
