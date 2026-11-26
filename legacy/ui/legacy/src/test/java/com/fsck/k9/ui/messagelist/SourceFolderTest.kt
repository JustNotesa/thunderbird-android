package com.fsck.k9.ui.messagelist

import app.k9mail.legacy.message.controller.MessageReference
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import net.thunderbird.feature.account.AccountIdFactory
import org.junit.Test

class SourceFolderTest {
    private val accountId = AccountIdFactory.of("01a0e8d7-87dc-74a4-a52c-3f507c6dc02c")
    private val otherAccountId = AccountIdFactory.of("01a0e8d7-87dc-74a4-a52c-3f507c6dc02d")

    @Test
    fun `commonFolderId() with messages in the same folder should return the folder`() {
        val messages = listOf(
            MessageReference(accountId, folderId = 7, uid = "1"),
            MessageReference(accountId, folderId = 7, uid = "2"),
        )

        val result = commonFolderId(messages)

        assertThat(result).isEqualTo(7L)
    }

    @Test
    fun `commonFolderId() with messages in different folders should return null`() {
        val messages = listOf(
            MessageReference(accountId, folderId = 7, uid = "1"),
            MessageReference(accountId, folderId = 8, uid = "2"),
        )

        val result = commonFolderId(messages)

        assertThat(result).isNull()
    }

    @Test
    fun `commonFolderId() with messages in different accounts should return null`() {
        val messages = listOf(
            MessageReference(accountId, folderId = 7, uid = "1"),
            MessageReference(otherAccountId, folderId = 7, uid = "2"),
        )

        val result = commonFolderId(messages)

        assertThat(result).isNull()
    }

    @Test
    fun `commonFolderId() without messages should return null`() {
        val result = commonFolderId(emptyList())

        assertThat(result).isNull()
    }
}
