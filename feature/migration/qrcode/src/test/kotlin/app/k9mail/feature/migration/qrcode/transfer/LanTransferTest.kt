package app.k9mail.feature.migration.qrcode.transfer

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import javax.net.SocketFactory
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import net.thunderbird.core.logging.testing.TestLogger
import net.thunderbird.legacy.logging.Log
import okio.ByteString.Companion.toByteString
import org.junit.Before

class LanTransferTest {
    private val bindAddress = InetAddress.getLoopbackAddress()
    private val host = bindAddress.hostAddress
    private val container = "encrypted-transfer-container".encodeToByteArray()
    private val transferId = ByteArray(TRANSFER_ID_LENGTH) { it.toByte() }
    private val client = LanTransferClient(connectTimeoutMillis = CONNECT_TIMEOUT, readTimeoutMillis = READ_TIMEOUT)

    @Before
    fun setUp() {
        Log.logger = TestLogger()
    }

    @Test
    fun `client fetches the container and server reports the transfer`() = runBlocking {
        val testSubject = createServer(lifetimeMillis = SERVER_LIFETIME)
        val route = TransferRoute.Lan(host = host, port = testSubject.start())

        val serverResult = async(Dispatchers.IO) { testSubject.awaitTransfer() }
        val clientResult = client.fetch(route, transferId, SocketFactory.getDefault())

        assertThat(clientResult).isInstanceOf<LanTransferClientResult.Success>()
        assertThat((clientResult as LanTransferClientResult.Success).container.toByteString())
            .isEqualTo(container.toByteString())
        assertThat(serverResult.await()).isEqualTo(LanTransferServerResult.Transferred)
    }

    @Test
    fun `client with a wrong transfer id fails and the server keeps waiting`() = runBlocking {
        val testSubject = createServer(lifetimeMillis = SERVER_LIFETIME)
        val route = TransferRoute.Lan(host = host, port = testSubject.start())
        val wrongTransferId = ByteArray(TRANSFER_ID_LENGTH) { (it + 1).toByte() }

        val serverResult = async(Dispatchers.IO) { testSubject.awaitTransfer() }
        val failedResult = client.fetch(route, wrongTransferId, SocketFactory.getDefault())
        val clientResult = client.fetch(route, transferId, SocketFactory.getDefault())

        assertThat(failedResult).isEqualTo(LanTransferClientResult.Failed)
        assertThat(clientResult).isInstanceOf<LanTransferClientResult.Success>()
        assertThat(serverResult.await()).isEqualTo(LanTransferServerResult.Transferred)
    }

    @Test
    fun `server stops listening after the container was transferred`() = runBlocking {
        val testSubject = createServer(lifetimeMillis = SERVER_LIFETIME)
        val route = TransferRoute.Lan(host = host, port = testSubject.start())

        val serverResult = async(Dispatchers.IO) { testSubject.awaitTransfer() }
        val firstResult = client.fetch(route, transferId, SocketFactory.getDefault())
        assertThat(serverResult.await()).isEqualTo(LanTransferServerResult.Transferred)
        assertThat(firstResult).isInstanceOf<LanTransferClientResult.Success>()

        val secondResult = client.fetch(route, transferId, SocketFactory.getDefault())

        assertThat(secondResult).isEqualTo(LanTransferClientResult.Unreachable)
    }

    @Test
    fun `server expires when nobody fetches the container`() = runBlocking {
        val testSubject = createServer(lifetimeMillis = SHORT_LIFETIME)
        testSubject.start()

        val result = testSubject.awaitTransfer()

        assertThat(result).isEqualTo(LanTransferServerResult.Expired)
    }

    @Test
    fun `client fetching from a closed port is unreachable`() = runBlocking {
        val port = closedPort()

        val result = client.fetch(TransferRoute.Lan(host = host, port = port), transferId, SocketFactory.getDefault())

        assertThat(result).isEqualTo(LanTransferClientResult.Unreachable)
    }

    @Test
    fun `client rejects a container larger than allowed`() = runBlocking {
        val testSubject = LanTransferClient(
            connectTimeoutMillis = CONNECT_TIMEOUT,
            readTimeoutMillis = READ_TIMEOUT,
            maxContainerSize = SMALL_MAX_CONTAINER_SIZE,
        )
        val port = startFakeServer(announcedSize = ANNOUNCED_SIZE)

        val result = testSubject.fetch(
            TransferRoute.Lan(host = host, port = port),
            transferId,
            SocketFactory.getDefault(),
        )

        assertThat(result).isEqualTo(LanTransferClientResult.Failed)
    }

    private fun createServer(lifetimeMillis: Long): LanTransferServer {
        return LanTransferServer(
            container = container,
            transferId = transferId,
            bindAddress = bindAddress,
            lifetimeMillis = lifetimeMillis,
        )
    }

    private fun closedPort(): Int {
        return ServerSocket(0, BACKLOG, bindAddress).use { it.localPort }
    }

    /**
     * Answers a single request with a container length that is larger than the client accepts.
     *
     * @return The port the fake server listens on.
     */
    private fun startFakeServer(announcedSize: Int): Int {
        val serverSocket = ServerSocket(0, BACKLOG, bindAddress)

        thread(isDaemon = true) {
            serverSocket.use { socket ->
                socket.accept().use { connection ->
                    val input = DataInputStream(connection.getInputStream())
                    input.readFully(ByteArray(MAGIC_LENGTH + TRANSFER_ID_LENGTH))
                    DataOutputStream(connection.getOutputStream()).writeInt(announcedSize)
                }
            }
        }

        return serverSocket.localPort
    }

    private companion object {
        private const val MAGIC_LENGTH = 4
        private const val TRANSFER_ID_LENGTH = 16
        private const val BACKLOG = 1
        private const val CONNECT_TIMEOUT = 2_000
        private const val READ_TIMEOUT = 5_000
        private const val SERVER_LIFETIME = 10_000L
        private const val SHORT_LIFETIME = 200L
        private const val SMALL_MAX_CONTAINER_SIZE = 16
        private const val ANNOUNCED_SIZE = 1_024
    }
}
