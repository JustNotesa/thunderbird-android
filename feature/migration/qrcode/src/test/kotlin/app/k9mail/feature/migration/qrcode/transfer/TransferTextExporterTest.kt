package app.k9mail.feature.migration.qrcode.transfer

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import com.fsck.k9.mail.AuthType
import com.fsck.k9.mail.ConnectionSecurity
import com.fsck.k9.mail.ServerSettings
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import net.thunderbird.core.logging.testing.TestLogger
import net.thunderbird.legacy.logging.Log

private const val CREATED_AT = 1792300000L
private const val USERNAME = "username"

class TransferTextExporterTest {

    @BeforeTest
    fun setUp() {
        Log.logger = TestLogger()
    }

    @Test
    fun `the exported text is the payload the other device can import`() = runTest {
        val accounts = listOf(imapAccount())
        val testSubject = createExporter(accounts = accounts)

        val result = testSubject.exportText()

        assertThat(result).isInstanceOf<TransferTextResult.Success>()
        val text = (result as TransferTextResult.Success).text
        val content = TransferPayload.read(text)
        assertThat(content).isNotNull()
        assertThat(content?.createdAt).isEqualTo(CREATED_AT)
        assertThat(content?.accountsJson).isEqualTo(DesktopPayloadWriter.write(accounts))
    }

    @Test
    fun `a device without accounts exports nothing`() = runTest {
        val testSubject = createExporter(accounts = emptyList())

        val result = testSubject.exportText()

        assertThat(result).isEqualTo(TransferTextResult.NoAccounts)
    }

    private fun createExporter(accounts: List<TransferAccount>): TransferTextExporter {
        return TransferTextExporter(
            accountSource = TransferAccountSource { accounts },
            clock = { CREATED_AT },
        )
    }

    private fun imapAccount(): TransferAccount {
        return TransferAccount(
            accountName = "Personal",
            incoming = ServerSettings(
                type = "imap",
                host = "imap.domain.example",
                port = 993,
                connectionSecurity = ConnectionSecurity.SSL_TLS_REQUIRED,
                authenticationType = AuthType.CRAM_MD5,
                username = USERNAME,
                password = "imap-password",
                clientCertificateAlias = null,
            ),
            outgoing = ServerSettings(
                type = "smtp",
                host = "smtp.domain.example",
                port = 587,
                connectionSecurity = ConnectionSecurity.STARTTLS_REQUIRED,
                authenticationType = AuthType.PLAIN,
                username = USERNAME,
                password = "smtp-password",
                clientCertificateAlias = null,
            ),
            identities = listOf(
                TransferIdentity(emailAddress = "user@domain.example", displayName = "Firstname Lastname"),
            ),
        )
    }
}
