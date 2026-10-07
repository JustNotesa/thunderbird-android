package com.fsck.k9.mail.store.imap

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.fsck.k9.mail.store.imap.ImapResponseHelper.createImapResponseList
import kotlin.test.Test

class QuotaResponseTest {
    @Test
    fun `storage quota should be converted to bytes`() {
        val imapResponses = createImapResponseList(
            """* QUOTAROOT INBOX """"",
            """* QUOTA "" (STORAGE 10 512)""",
            "1 OK GETQUOTAROOT completed",
        )

        val result = QuotaResponse.parse(imapResponses)

        assertThat(result).isEqualTo(ImapStorageQuota(usedBytes = 10_240L, limitBytes = 524_288L))
    }

    @Test
    fun `storage quota should be found next to other resources`() {
        val imapResponses = createImapResponseList(
            """* QUOTA "User quota" (MESSAGE 42 1000 STORAGE 2048 4096)""",
            "1 OK GETQUOTAROOT completed",
        )

        val result = QuotaResponse.parse(imapResponses)

        assertThat(result).isEqualTo(ImapStorageQuota(usedBytes = 2_097_152L, limitBytes = 4_194_304L))
    }

    @Test
    fun `first quota root with storage quota should be used`() {
        val imapResponses = createImapResponseList(
            """* QUOTA "messages" (MESSAGE 42 1000)""",
            """* QUOTA "user" (STORAGE 1 2)""",
            """* QUOTA "domain" (STORAGE 3 4)""",
            "1 OK GETQUOTAROOT completed",
        )

        val result = QuotaResponse.parse(imapResponses)

        assertThat(result).isEqualTo(ImapStorageQuota(usedBytes = 1_024L, limitBytes = 2_048L))
    }

    @Test
    fun `responses without storage quota should return null`() {
        val imapResponses = createImapResponseList(
            """* QUOTAROOT INBOX """"",
            """* QUOTA "" ()""",
            "1 OK GETQUOTAROOT completed",
        )

        val result = QuotaResponse.parse(imapResponses)

        assertThat(result).isNull()
    }

    @Test
    fun `malformed storage quota should return null`() {
        val imapResponses = createImapResponseList(
            """* QUOTA "" (STORAGE many few)""",
            "1 OK GETQUOTAROOT completed",
        )

        val result = QuotaResponse.parse(imapResponses)

        assertThat(result).isNull()
    }
}
