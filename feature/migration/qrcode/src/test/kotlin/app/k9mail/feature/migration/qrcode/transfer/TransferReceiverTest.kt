package app.k9mail.feature.migration.qrcode.transfer

import app.k9mail.feature.migration.qrcode.transfer.hotspot.HotspotConnectResult
import app.k9mail.feature.migration.qrcode.transfer.hotspot.HotspotNetworkConnector
import assertk.assertThat
import assertk.assertions.isEqualTo
import java.net.Inet4Address
import java.net.InetAddress
import javax.net.SocketFactory
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import net.thunderbird.components.core.logging.testing.TestLogger
import net.thunderbird.legacy.logging.Log
import okio.ByteString.Companion.toByteString
import org.junit.Before

class TransferReceiverTest {
    private val secrets = createSecrets()
    private val qrCode = TransferQrCode(secrets = secrets, route = LAN_ROUTE)
    private val payload = TransferPayload.write(accountsJson = ACCOUNTS_JSON, createdAt = CREATED_AT)
    private val container = createContainer(secrets = secrets, plaintext = payload.encodeToByteArray())
    private val unsupportedConnector = FakeHotspotNetworkConnector(HotspotConnectResult.Unsupported)

    @Before
    fun setUp() {
        Log.logger = TestLogger()
    }

    @Test
    fun `a received transfer returns the accounts of its container`() = runTest {
        val testSubject = TransferReceiver(
            fetcher = fetcherReturning(container),
            hotspotConnector = unsupportedConnector,
        )

        val result = testSubject.receive(qrCode)

        assertThat(result).isEqualTo(TransferReceiveResult.Success(accountsJson = ACCOUNTS_JSON))
    }

    @Test
    fun `a transfer of an unreachable device is not received`() = runTest {
        val testSubject = TransferReceiver(
            fetcher = fetcherReturning(LanTransferClientResult.Unreachable),
            hotspotConnector = unsupportedConnector,
        )

        val result = testSubject.receive(qrCode)

        assertThat(result).isEqualTo(TransferReceiveResult.Unreachable)
    }

    @Test
    fun `a transfer the sending device doesn't deliver is not received`() = runTest {
        val testSubject = TransferReceiver(
            fetcher = fetcherReturning(LanTransferClientResult.Failed),
            hotspotConnector = unsupportedConnector,
        )

        val result = testSubject.receive(qrCode)

        assertThat(result).isEqualTo(TransferReceiveResult.TransferFailed)
    }

    @Test
    fun `a container encrypted with another key is not received`() = runTest {
        val otherContainer = createContainer(secrets = createSecrets(keyOffset = 1))
        val testSubject = TransferReceiver(
            fetcher = fetcherReturning(otherContainer),
            hotspotConnector = unsupportedConnector,
        )

        val result = testSubject.receive(qrCode)

        assertThat(result).isEqualTo(TransferReceiveResult.Corrupted)
    }

    @Test
    fun `a container of another transfer is not received`() = runTest {
        val otherContainer = createContainer(secrets = createSecrets(transferIdOffset = 1))
        val testSubject = TransferReceiver(
            fetcher = fetcherReturning(otherContainer),
            hotspotConnector = unsupportedConnector,
        )

        val result = testSubject.receive(qrCode)

        assertThat(result).isEqualTo(TransferReceiveResult.Corrupted)
    }

    @Test
    fun `a container of an unknown format is not received`() = runTest {
        val garbage = ByteArray(GARBAGE_SIZE) { it.toByte() }
        val testSubject = TransferReceiver(
            fetcher = fetcherReturning(garbage),
            hotspotConnector = unsupportedConnector,
        )

        val result = testSubject.receive(qrCode)

        assertThat(result).isEqualTo(TransferReceiveResult.UnsupportedFormat)
    }

    @Test
    fun `a container that doesn't hold a transfer payload is not received`() = runTest {
        val invalidPayload = createContainer(
            secrets = secrets,
            plaintext = "https://domain.example/path".encodeToByteArray(),
        )
        val testSubject = TransferReceiver(
            fetcher = fetcherReturning(invalidPayload),
            hotspotConnector = unsupportedConnector,
        )

        val result = testSubject.receive(qrCode)

        assertThat(result).isEqualTo(TransferReceiveResult.UnsupportedFormat)
    }

    @Test
    fun `a transfer without a route is not received`() = runTest {
        var fetchCount = 0
        val testSubject = TransferReceiver(
            fetcher = TransferContainerFetcher { _, _, _ ->
                fetchCount++
                LanTransferClientResult.Failed
            },
            hotspotConnector = unsupportedConnector,
        )

        val result = testSubject.receive(qrCode.copy(route = null))

        assertThat(result).isEqualTo(TransferReceiveResult.UnsupportedRoute)
        assertThat(fetchCount).isEqualTo(0)
    }

    @Test
    fun `a transfer over the hotspot returns the accounts and leaves the hotspot`() = runTest {
        val connector = FakeHotspotNetworkConnector(connectedResult())
        val testSubject = TransferReceiver(
            fetcher = fetcherReturning(container),
            hotspotConnector = connector,
        )

        val result = testSubject.receive(qrCode.copy(route = HOTSPOT_ROUTE))

        assertThat(result).isEqualTo(TransferReceiveResult.Success(accountsJson = ACCOUNTS_JSON))
        assertThat(connector.releaseCount).isEqualTo(1)
    }

    @Test
    fun `a transfer over the hotspot the sending device doesn't deliver leaves the hotspot`() = runTest {
        val connector = FakeHotspotNetworkConnector(connectedResult())
        val testSubject = TransferReceiver(
            fetcher = fetcherReturning(LanTransferClientResult.Failed),
            hotspotConnector = connector,
        )

        val result = testSubject.receive(qrCode.copy(route = HOTSPOT_ROUTE))

        assertThat(result).isEqualTo(TransferReceiveResult.TransferFailed)
        assertThat(connector.releaseCount).isEqualTo(1)
    }

    @Test
    fun `a hotspot that can't be joined doesn't start a transfer`() = runTest {
        var fetchCount = 0
        val testSubject = TransferReceiver(
            fetcher = TransferContainerFetcher { _, _, _ ->
                fetchCount++
                LanTransferClientResult.Success(container)
            },
            hotspotConnector = FakeHotspotNetworkConnector(HotspotConnectResult.Failed),
        )

        val result = testSubject.receive(qrCode.copy(route = HOTSPOT_ROUTE))

        assertThat(result).isEqualTo(TransferReceiveResult.HotspotUnavailable)
        assertThat(fetchCount).isEqualTo(0)
    }

    @Test
    fun `a hotspot this device can't join doesn't start a transfer`() = runTest {
        var fetchCount = 0
        val testSubject = TransferReceiver(
            fetcher = TransferContainerFetcher { _, _, _ ->
                fetchCount++
                LanTransferClientResult.Success(container)
            },
            hotspotConnector = unsupportedConnector,
        )

        val result = testSubject.receive(qrCode.copy(route = HOTSPOT_ROUTE))

        assertThat(result).isEqualTo(TransferReceiveResult.HotspotUnavailable)
        assertThat(fetchCount).isEqualTo(0)
    }

    @Test
    fun `the key material is cleared after a transfer was received`() = runTest {
        val testSubject = TransferReceiver(
            fetcher = fetcherReturning(container),
            hotspotConnector = unsupportedConnector,
        )

        testSubject.receive(qrCode)

        assertThat(secrets.key.toByteString()).isEqualTo(ByteArray(KEY_LENGTH).toByteString())
    }

    @Test
    fun `hosts in the network of the device are tried first`() {
        val hosts = listOf("192.168.1.24", "192.168.178.20")
        val localAddresses = listOf("192.168.178.5")

        val result = orderHostsBySubnet(hosts = hosts, localAddresses = localAddresses)

        assertThat(result).isEqualTo(listOf("192.168.178.20", "192.168.1.24"))
    }

    @Test
    fun `hosts without a network of the device keep their order`() {
        val hosts = listOf("192.168.1.24", "192.168.178.20")
        val localAddresses = listOf("10.0.0.5")

        val result = orderHostsBySubnet(hosts = hosts, localAddresses = localAddresses)

        assertThat(result).isEqualTo(listOf("192.168.1.24", "192.168.178.20"))
    }

    @Test
    fun `a transfer is fetched from the host in the network of the device`() = runTest {
        var fetchedHosts: List<String> = emptyList()
        val testSubject = TransferReceiver(
            fetcher = TransferContainerFetcher { route, _, _ ->
                fetchedHosts = route.hosts
                LanTransferClientResult.Success(container)
            },
            hotspotConnector = unsupportedConnector,
            addressProvider = LocalNetworkAddressProvider { address("192.168.178.5") },
        )

        val result = testSubject.receive(qrCode.copy(route = LAN_ROUTE_WITH_HOSTS))

        assertThat(result).isEqualTo(TransferReceiveResult.Success(accountsJson = ACCOUNTS_JSON))
        assertThat(fetchedHosts).isEqualTo(listOf("192.168.178.20", "192.168.1.24"))
    }

    private fun fetcherReturning(result: LanTransferClientResult): TransferContainerFetcher {
        return TransferContainerFetcher { _, _, _ -> result }
    }

    private fun fetcherReturning(container: ByteArray): TransferContainerFetcher {
        return fetcherReturning(LanTransferClientResult.Success(container))
    }

    private fun address(value: String): Inet4Address {
        return InetAddress.getByName(value) as Inet4Address
    }

    private fun connectedResult(): HotspotConnectResult.Connected {
        return HotspotConnectResult.Connected(socketFactory = SocketFactory.getDefault(), release = {})
    }

    private fun createContainer(
        secrets: TransferSecrets,
        plaintext: ByteArray = payload.encodeToByteArray(),
    ): ByteArray {
        return TransferContainer.encrypt(plaintext, secrets)
    }

    private fun createSecrets(transferIdOffset: Int = 0, keyOffset: Int = 0): TransferSecrets {
        return TransferSecrets(
            transferId = ByteArray(TRANSFER_ID_LENGTH) { (it + transferIdOffset).toByte() },
            key = ByteArray(KEY_LENGTH) { (it + keyOffset).toByte() },
        )
    }

    private companion object {
        private const val CREATED_AT = 1792300000L
        private const val ACCOUNTS_JSON = """[1,[1,1],""" +
            """[0,"imap.domain.example",993,3,2,"username","","imap-password"]]"""
        private const val HOST = "192.168.0.2"
        private const val PORT = 49152
        private const val SSID = "Thunderbird Transfer"
        private const val PASSPHRASE = "transfer-passphrase"
        private const val TRANSFER_ID_LENGTH = 16
        private const val KEY_LENGTH = 32
        private const val GARBAGE_SIZE = 64
        private val LAN_ROUTE = TransferRoute.Lan(host = HOST, port = PORT)
        private val LAN_ROUTE_WITH_HOSTS = TransferRoute.Lan(
            hosts = listOf("192.168.1.24", "192.168.178.20"),
            port = PORT,
        )
        private val HOTSPOT_ROUTE = TransferRoute.Hotspot(
            ssid = SSID,
            passphrase = PASSPHRASE,
            host = HOST,
            port = PORT,
        )
    }
}

private class FakeHotspotNetworkConnector(
    private val result: HotspotConnectResult,
) : HotspotNetworkConnector {
    var connectCount = 0
        private set

    var releaseCount = 0
        private set

    override suspend fun connect(ssid: String, passphrase: String): HotspotConnectResult {
        connectCount++

        // The release of the hotspot is counted instead of the one the fake was created with.
        return when (result) {
            is HotspotConnectResult.Connected -> result.copy(release = { releaseCount++ })
            else -> result
        }
    }
}
