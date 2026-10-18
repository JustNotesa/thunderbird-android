package app.k9mail.feature.migration.qrcode.transfer

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import net.thunderbird.core.logging.testing.TestLogger
import net.thunderbird.legacy.logging.Log
import okio.ByteString.Companion.toByteString
import org.junit.Before

class TransferReceiverTest {
    private val secrets = createSecrets()
    private val qrCode = TransferQrCode(secrets = secrets, route = LAN_ROUTE)
    private val payload = TransferPayload.write(accountsJson = ACCOUNTS_JSON, createdAt = CREATED_AT)
    private val container = createContainer(secrets = secrets, plaintext = payload.encodeToByteArray())

    @Before
    fun setUp() {
        Log.logger = TestLogger()
    }

    @Test
    fun `a received transfer returns the accounts of its container`() = runTest {
        val testSubject = TransferReceiver(fetcher = fetcherReturning(container))

        val result = testSubject.receive(qrCode)

        assertThat(result).isEqualTo(TransferReceiveResult.Success(accountsJson = ACCOUNTS_JSON))
    }

    @Test
    fun `a transfer of an unreachable device is not received`() = runTest {
        val testSubject = TransferReceiver(
            fetcher = fetcherReturning(LanTransferClientResult.Unreachable),
        )

        val result = testSubject.receive(qrCode)

        assertThat(result).isEqualTo(TransferReceiveResult.Unreachable)
    }

    @Test
    fun `a transfer the sending device doesn't deliver is not received`() = runTest {
        val testSubject = TransferReceiver(fetcher = fetcherReturning(LanTransferClientResult.Failed))

        val result = testSubject.receive(qrCode)

        assertThat(result).isEqualTo(TransferReceiveResult.TransferFailed)
    }

    @Test
    fun `a container encrypted with another key is not received`() = runTest {
        val otherContainer = createContainer(secrets = createSecrets(keyOffset = 1))
        val testSubject = TransferReceiver(fetcher = fetcherReturning(otherContainer))

        val result = testSubject.receive(qrCode)

        assertThat(result).isEqualTo(TransferReceiveResult.Corrupted)
    }

    @Test
    fun `a container of another transfer is not received`() = runTest {
        val otherContainer = createContainer(secrets = createSecrets(transferIdOffset = 1))
        val testSubject = TransferReceiver(fetcher = fetcherReturning(otherContainer))

        val result = testSubject.receive(qrCode)

        assertThat(result).isEqualTo(TransferReceiveResult.Corrupted)
    }

    @Test
    fun `a container of an unknown format is not received`() = runTest {
        val garbage = ByteArray(GARBAGE_SIZE) { it.toByte() }
        val testSubject = TransferReceiver(fetcher = fetcherReturning(garbage))

        val result = testSubject.receive(qrCode)

        assertThat(result).isEqualTo(TransferReceiveResult.UnsupportedFormat)
    }

    @Test
    fun `a container that doesn't hold a transfer payload is not received`() = runTest {
        val invalidPayload = createContainer(
            secrets = secrets,
            plaintext = "https://domain.example/path".encodeToByteArray(),
        )
        val testSubject = TransferReceiver(fetcher = fetcherReturning(invalidPayload))

        val result = testSubject.receive(qrCode)

        assertThat(result).isEqualTo(TransferReceiveResult.UnsupportedFormat)
    }

    @Test
    fun `a transfer without a route is not received`() = runTest {
        var fetchCount = 0
        val testSubject = TransferReceiver(
            fetcher = TransferContainerFetcher { _, _ ->
                fetchCount++
                LanTransferClientResult.Failed
            },
        )

        val result = testSubject.receive(qrCode.copy(route = null))

        assertThat(result).isEqualTo(TransferReceiveResult.UnsupportedRoute)
        assertThat(fetchCount).isEqualTo(0)
    }

    @Test
    fun `the key material is cleared after a transfer was received`() = runTest {
        val testSubject = TransferReceiver(fetcher = fetcherReturning(container))

        testSubject.receive(qrCode)

        assertThat(secrets.key.toByteString()).isEqualTo(ByteArray(KEY_LENGTH).toByteString())
    }

    private fun fetcherReturning(result: LanTransferClientResult): TransferContainerFetcher {
        return TransferContainerFetcher { _, _ -> result }
    }

    private fun fetcherReturning(container: ByteArray): TransferContainerFetcher {
        return fetcherReturning(LanTransferClientResult.Success(container))
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
        private const val TRANSFER_ID_LENGTH = 16
        private const val KEY_LENGTH = 32
        private const val GARBAGE_SIZE = 64
        private val LAN_ROUTE = TransferRoute.Lan(host = HOST, port = PORT)
    }
}
