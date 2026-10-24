package app.k9mail.feature.migration.qrcode.transfer

import app.k9mail.feature.migration.qrcode.payload.QrCodeData
import app.k9mail.feature.migration.qrcode.payload.QrCodePayloadAdapter
import app.k9mail.feature.migration.qrcode.payload.QrCodePayloadParser
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.fsck.k9.mail.AuthType
import com.fsck.k9.mail.ConnectionSecurity
import com.fsck.k9.mail.ServerSettings
import kotlin.test.Test
import net.thunderbird.components.core.logging.testing.TestLogger
import net.thunderbird.legacy.logging.Log
import org.junit.Before

class DesktopPayloadWriterTest {
    private val testSubject = DesktopPayloadWriter
    private val parser = QrCodePayloadParser(QrCodePayloadAdapter())

    @Before
    fun setUp() {
        Log.logger = TestLogger()
    }

    @Test
    fun `written payload is read by the desktop payload parser`() {
        val payload = testSubject.write(listOf(imapAccount()))

        val result = parser.parse(payload)

        assertThat(result).isEqualTo(
            QrCodeData(
                version = 1,
                misc = QrCodeData.Misc(sequenceNumber = 1, sequenceEnd = 1),
                accounts = listOf(expectedImapAccount()),
            ),
        )
    }

    @Test
    fun `two accounts are written into a single payload`() {
        val payload = testSubject.write(listOf(imapAccount(), pop3Account()))

        val result = parser.parse(payload)

        assertThat(result).isEqualTo(
            QrCodeData(
                version = 1,
                misc = QrCodeData.Misc(sequenceNumber = 1, sequenceEnd = 1),
                accounts = listOf(expectedImapAccount(), expectedPop3Account()),
            ),
        )
    }

    @Test
    fun `account without account name and passwords is written with empty strings`() {
        val payload = testSubject.write(
            listOf(imapAccount(accountName = null, incomingPassword = null, outgoingPassword = null)),
        )

        val result = parser.parse(payload)

        assertThat(result).isEqualTo(
            QrCodeData(
                version = 1,
                misc = QrCodeData.Misc(sequenceNumber = 1, sequenceEnd = 1),
                accounts = listOf(
                    expectedImapAccount(
                        accountName = "",
                        incomingPassword = "",
                        outgoingPassword = "",
                    ),
                ),
            ),
        )
    }

    private fun imapAccount(
        accountName: String? = ACCOUNT_NAME,
        incomingPassword: String? = "imap-password",
        outgoingPassword: String? = "smtp-password",
    ): TransferAccount {
        return TransferAccount(
            accountName = accountName,
            incoming = ServerSettings(
                type = "imap",
                host = "imap.domain.example",
                port = 993,
                connectionSecurity = ConnectionSecurity.SSL_TLS_REQUIRED,
                authenticationType = AuthType.CRAM_MD5,
                username = USERNAME,
                password = incomingPassword,
                clientCertificateAlias = null,
            ),
            outgoing = ServerSettings(
                type = "smtp",
                host = "smtp.domain.example",
                port = 587,
                connectionSecurity = ConnectionSecurity.STARTTLS_REQUIRED,
                authenticationType = AuthType.PLAIN,
                username = USERNAME,
                password = outgoingPassword,
                clientCertificateAlias = null,
            ),
            identities = listOf(
                TransferIdentity(emailAddress = "user@domain.example", displayName = "Firstname Lastname"),
            ),
        )
    }

    private fun pop3Account(): TransferAccount {
        return TransferAccount(
            accountName = null,
            incoming = ServerSettings(
                type = "pop3",
                host = "pop3.domain.example",
                port = 995,
                connectionSecurity = ConnectionSecurity.SSL_TLS_REQUIRED,
                authenticationType = AuthType.PLAIN,
                username = "pop-user",
                password = null,
                clientCertificateAlias = null,
            ),
            outgoing = ServerSettings(
                type = "smtp",
                host = "smtp.domain.example",
                port = 465,
                connectionSecurity = ConnectionSecurity.SSL_TLS_REQUIRED,
                authenticationType = AuthType.XOAUTH2,
                username = "pop-user",
                password = null,
                clientCertificateAlias = null,
            ),
            identities = listOf(
                TransferIdentity(emailAddress = "pop@domain.example", displayName = ""),
            ),
        )
    }

    private fun expectedImapAccount(
        accountName: String = ACCOUNT_NAME,
        incomingPassword: String = "imap-password",
        outgoingPassword: String = "smtp-password",
    ): QrCodeData.Account {
        return QrCodeData.Account(
            incomingServer = QrCodeData.IncomingServer(
                protocol = 0,
                hostname = "imap.domain.example",
                port = 993,
                connectionSecurity = 3,
                authenticationType = 2,
                username = USERNAME,
                accountName = accountName,
                password = incomingPassword,
            ),
            outgoingServers = listOf(
                QrCodeData.OutgoingServer(
                    protocol = 0,
                    hostname = "smtp.domain.example",
                    port = 587,
                    connectionSecurity = 2,
                    authenticationType = 1,
                    username = USERNAME,
                    password = outgoingPassword,
                    identities = listOf(
                        QrCodeData.Identity(
                            emailAddress = "user@domain.example",
                            displayName = "Firstname Lastname",
                        ),
                    ),
                ),
            ),
        )
    }

    private fun expectedPop3Account(): QrCodeData.Account {
        return QrCodeData.Account(
            incomingServer = QrCodeData.IncomingServer(
                protocol = 1,
                hostname = "pop3.domain.example",
                port = 995,
                connectionSecurity = 3,
                authenticationType = 1,
                username = "pop-user",
                accountName = "",
                password = "",
            ),
            outgoingServers = listOf(
                QrCodeData.OutgoingServer(
                    protocol = 0,
                    hostname = "smtp.domain.example",
                    port = 465,
                    connectionSecurity = 3,
                    authenticationType = 6,
                    username = "pop-user",
                    password = "",
                    identities = listOf(
                        QrCodeData.Identity(
                            emailAddress = "pop@domain.example",
                            displayName = "",
                        ),
                    ),
                ),
            ),
        )
    }

    private companion object {
        private const val ACCOUNT_NAME = "Personal"
        private const val USERNAME = "username"
    }
}
