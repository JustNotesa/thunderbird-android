package app.k9mail.feature.migration.qrcode.transfer

import app.k9mail.feature.migration.qrcode.payload.QrCodeData
import app.k9mail.feature.migration.qrcode.payload.QrCodePayloadAdapter
import app.k9mail.feature.migration.qrcode.payload.QrCodePayloadParser
import app.k9mail.feature.migration.qrcode.transfer.hotspot.FakeHotspotController
import app.k9mail.feature.migration.qrcode.transfer.hotspot.HotspotController
import app.k9mail.feature.migration.qrcode.transfer.hotspot.HotspotStartResult
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
    fun `a prepared hotspot transfer is fetched by the receiving device`() = runTest {
        val hotspotController = createStartedHotspot()
        val testSubject = createSender(
            accounts = accounts,
            address = bindAddress,
            hotspotController = hotspotController,
        )

        val result = testSubject.prepareHotspot()

        assertThat(result).isInstanceOf<TransferSendResult.Ready>()
        val session = (result as TransferSendResult.Ready).session
        val qrCode = checkNotNull(TransferQrCode.parse(session.qrText)) { "The QR code is invalid" }
        val route = checkNotNull(qrCode.route as? TransferRoute.Hotspot) { "The QR code has no hotspot route" }
        assertThat(route.ssid).isEqualTo(HOTSPOT_SSID)
        assertThat(route.passphrase).isEqualTo(HOTSPOT_PASSPHRASE)
        assertThat(route.host).isEqualTo(bindAddress.hostAddress)
        assertThat(route.port).isGreaterThan(NO_PORT)

        val serverResult = async(Dispatchers.IO) { session.awaitTransfer() }
        val accountsJson = fetchAccounts(qrCode, route)

        assertThat(serverResult.await()).isEqualTo(LanTransferServerResult.Transferred)
        assertThat(parser.parse(accountsJson)).isEqualTo(expectedAccounts())
        assertThat(hotspotController.stopCount).isEqualTo(1)
    }

    @Test
    fun `stopping a hotspot transfer stops the hotspot`() = runTest {
        val hotspotController = createStartedHotspot()
        val testSubject = createSender(
            accounts = accounts,
            address = bindAddress,
            hotspotController = hotspotController,
        )

        val result = testSubject.prepareHotspot()

        assertThat(result).isInstanceOf<TransferSendResult.Ready>()
        (result as TransferSendResult.Ready).session.stop()

        assertThat(hotspotController.stopCount).isEqualTo(1)
    }

    @Test
    fun `a hotspot transfer that can't open a port fails and stops the hotspot`() = runTest {
        val hotspotController = createStartedHotspot()
        val testSubject = createSender(
            accounts = accounts,
            address = bindAddress,
            bindAddress = InetAddress.getByName(UNASSIGNED_ADDRESS),
            hotspotController = hotspotController,
        )

        val result = testSubject.prepareHotspot()

        assertThat(result).isEqualTo(TransferSendResult.Failed)
        assertThat(hotspotController.stopCount).isEqualTo(1)
    }

    @Test
    fun `a transfer over an unsupported hotspot is not prepared`() = runTest {
        val testSubject = createSender(
            accounts = accounts,
            address = bindAddress,
            hotspotController = FakeHotspotController(HotspotStartResult.Unsupported),
        )

        val result = testSubject.prepareHotspot()

        assertThat(result).isEqualTo(TransferSendResult.HotspotUnsupported)
    }

    @Test
    fun `a transfer over a hotspot that failed is not prepared`() = runTest {
        val testSubject = createSender(
            accounts = accounts,
            address = bindAddress,
            hotspotController = FakeHotspotController(HotspotStartResult.Failed),
        )

        val result = testSubject.prepareHotspot()

        assertThat(result).isEqualTo(TransferSendResult.Failed)
    }

    @Test
    fun `a hotspot transfer without accounts doesn't start the hotspot`() = runTest {
        val hotspotController = createStartedHotspot()
        val testSubject = createSender(
            accounts = emptyList(),
            address = bindAddress,
            hotspotController = hotspotController,
        )

        val result = testSubject.prepareHotspot()

        assertThat(result).isEqualTo(TransferSendResult.NoAccounts)
        assertThat(hotspotController.startCount).isEqualTo(0)
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
        hotspotController: HotspotController = FakeHotspotController(HotspotStartResult.Unsupported),
    ): TransferSender {
        return TransferSender(
            accountSource = TransferAccountSource { accounts },
            addressProvider = LocalNetworkAddressProvider { address },
            hotspotController = hotspotController,
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

    private fun createStartedHotspot(): FakeHotspotController {
        return FakeHotspotController(
            HotspotStartResult.Started(
                ssid = HOTSPOT_SSID,
                passphrase = HOTSPOT_PASSPHRASE,
                address = bindAddress,
            ),
        )
    }

    /**
     * Fetches the container of a hotspot transfer the way a receiving device that is already on the hotspot does: with
     * the host and port of the route, after joining the network named by the QR code.
     */
    private suspend fun fetchAccounts(qrCode: TransferQrCode, route: TransferRoute.Hotspot): String {
        val lanRoute = TransferRoute.Lan(host = route.host, port = route.port)
        val fetched = LanTransferClient().fetch(lanRoute, qrCode.secrets.transferId)

        assertThat(fetched).isInstanceOf<LanTransferClientResult.Success>()
        val container = (fetched as LanTransferClientResult.Success).container
        val decrypted = TransferContainer.decrypt(container, qrCode.secrets)
        assertThat(decrypted).isInstanceOf<TransferContainerResult.Success>()
        val plaintext = (decrypted as TransferContainerResult.Success).plaintext

        return checkNotNull(TransferPayload.read(String(plaintext, Charsets.UTF_8))) {
            "The payload of the transfer is invalid"
        }.accountsJson
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
        private const val HOTSPOT_SSID = "DIRECT-test"
        private const val HOTSPOT_PASSPHRASE = "secret123"
    }
}
