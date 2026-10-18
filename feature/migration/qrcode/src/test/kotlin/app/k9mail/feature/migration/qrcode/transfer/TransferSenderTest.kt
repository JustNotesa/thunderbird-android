package app.k9mail.feature.migration.qrcode.transfer

import app.k9mail.feature.migration.qrcode.payload.QrCodeData
import app.k9mail.feature.migration.qrcode.payload.QrCodePayloadAdapter
import app.k9mail.feature.migration.qrcode.payload.QrCodePayloadParser
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import com.fsck.k9.mail.AuthType
import com.fsck.k9.mail.ConnectionSecurity
import com.fsck.k9.mail.ServerSettings
import java.net.Inet4Address
import java.net.InetAddress
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import net.thunderbird.core.logging.testing.TestLogger
import net.thunderbird.legacy.logging.Log
import org.junit.Before

class TransferSenderTest {
    private val bindAddress = InetAddress.getLoopbackAddress() as Inet4Address
    private val accounts = listOf(imapAccount(), pop3Account())
    private val parser = QrCodePayloadParser(QrCodePayloadAdapter())

    @Before
    fun setUp() {
        Log.logger = TestLogger()
    }

    @Test
    fun `a prepared transfer is fetched by the receiving device`() = runTest {
        val testSubject = createSender(accounts = accounts, address = bindAddress)

        val result = testSubject.prepare()

        assertThat(result).isInstanceOf<TransferSendResult.Ready>()
        val session = (result as TransferSendResult.Ready).session
        try {
            val qrCode = checkNotNull(TransferQrCode.parse(session.qrText)) { "The QR code is invalid" }
            val route = checkNotNull(qrCode.route as? TransferRoute.Lan) { "The QR code has no LAN route" }
            assertThat(route.host).isEqualTo(bindAddress.hostAddress)
            assertThat(route.port).isGreaterThan(NO_PORT)

            val serverResult = async(Dispatchers.IO) { session.awaitTransfer() }
            val received = TransferReceiver(fetcher = LanTransferClient()).receive(qrCode)
            val accountsJson = (received as TransferReceiveResult.Success).accountsJson

            assertThat(serverResult.await()).isEqualTo(LanTransferServerResult.Transferred)
            assertThat(parser.parse(accountsJson)).isEqualTo(expectedAccounts())
        } finally {
            session.stop()
        }
    }

    @Test
    fun `a transfer without a wifi network is not prepared`() = runTest {
        val testSubject = createSender(accounts = accounts, address = null)

        val result = testSubject.prepare()

        assertThat(result).isEqualTo(TransferSendResult.NoWifi)
    }

    @Test
    fun `a transfer without accounts is not prepared`() = runTest {
        val testSubject = createSender(accounts = emptyList(), address = bindAddress)

        val result = testSubject.prepare()

        assertThat(result).isEqualTo(TransferSendResult.NoAccounts)
    }

    @Test
    fun `a transfer that can't open a port fails`() = runTest {
        val testSubject = createSender(
            accounts = accounts,
            address = bindAddress,
            bindAddress = InetAddress.getByName(UNASSIGNED_ADDRESS),
        )

        val result = testSubject.prepare()

        assertThat(result).isEqualTo(TransferSendResult.Failed)
    }

    private fun createSender(
        accounts: List<TransferAccount>,
        address: Inet4Address?,
        bindAddress: InetAddress = address ?: InetAddress.getLoopbackAddress(),
    ): TransferSender {
        return TransferSender(
            accountSource = TransferAccountSource { accounts },
            addressProvider = LocalNetworkAddressProvider { address },
            clock = { CREATED_AT },
            createServer = { container, transferId, _ ->
                LanTransferServer(
                    container = container,
                    transferId = transferId,
                    bindAddress = bindAddress,
                    lifetimeMillis = SERVER_LIFETIME,
                )
            },
        )
    }

    private fun imapAccount(): TransferAccount {
        return TransferAccount(
            accountName = ACCOUNT_NAME,
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

    private fun expectedAccounts(): QrCodeData {
        return QrCodeData(
            version = 1,
            misc = QrCodeData.Misc(sequenceNumber = 1, sequenceEnd = 1),
            accounts = listOf(expectedImapAccount(), expectedPop3Account()),
        )
    }

    private fun expectedImapAccount(): QrCodeData.Account {
        return QrCodeData.Account(
            incomingServer = QrCodeData.IncomingServer(
                protocol = 0,
                hostname = "imap.domain.example",
                port = 993,
                connectionSecurity = 3,
                authenticationType = 2,
                username = USERNAME,
                accountName = ACCOUNT_NAME,
                password = "imap-password",
            ),
            outgoingServers = listOf(
                QrCodeData.OutgoingServer(
                    protocol = 0,
                    hostname = "smtp.domain.example",
                    port = 587,
                    connectionSecurity = 2,
                    authenticationType = 1,
                    username = USERNAME,
                    password = "smtp-password",
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
        private const val CREATED_AT = 1792300000L
        private const val NO_PORT = 0
        private const val SERVER_LIFETIME = 10_000L
        private const val UNASSIGNED_ADDRESS = "192.0.2.1"
    }
}
