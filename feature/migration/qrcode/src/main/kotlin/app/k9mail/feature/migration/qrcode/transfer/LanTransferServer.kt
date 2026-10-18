package app.k9mail.feature.migration.qrcode.transfer

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import net.thunderbird.legacy.logging.Log

private val MAGIC = "TBX1".encodeToByteArray()
private const val MAGIC_LENGTH = 4
private const val TRANSFER_ID_LENGTH = 16
private const val EPHEMERAL_PORT = 0
private const val CONNECTION_TIMEOUT_MILLIS = 10_000
private const val DEFAULT_LIFETIME_MILLIS = 5L * 60 * 1000

/**
 * Serves the encrypted transfer container once, on the local network, to the first device asking for it with the
 * matching transfer ID.
 *
 * The protocol is deliberately not HTTP: neither Android's cleartext policies nor iOS ATS apply to a plain socket, and
 * no TLS is needed because the container is encrypted and authenticated anyway. A connection asking for a different
 * transfer is closed without an answer and the server keeps waiting until its lifetime is over.
 */
internal class LanTransferServer(
    private val container: ByteArray,
    private val transferId: ByteArray,
    private val bindAddress: InetAddress,
    private val lifetimeMillis: Long = DEFAULT_LIFETIME_MILLIS,
) {
    @Volatile
    private var serverSocket: ServerSocket? = null

    /**
     * Opens the listening socket on a port picked by the system.
     *
     * @return The port the server listens on.
     */
    fun start(): Int {
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(InetSocketAddress(bindAddress, EPHEMERAL_PORT))
        serverSocket = socket

        return socket.localPort
    }

    /**
     * Waits for a device to fetch the container. Has to be called after [start].
     *
     * @return [LanTransferServerResult.Transferred] once the container was sent, [LanTransferServerResult.Expired] when
     * the lifetime is over, [LanTransferServerResult.Failed] when no connection can be served anymore.
     */
    suspend fun awaitTransfer(): LanTransferServerResult = withContext(Dispatchers.IO) {
        val socket = checkNotNull(serverSocket) { "Transfer server was not started" }
        val cancellation = currentCoroutineContext().job.invokeOnCompletion { closeSocket() }

        try {
            waitForTransfer(socket)
        } finally {
            cancellation.dispose()
            closeSocket()
        }
    }

    /**
     * Closes the listening socket. To be called when the user aborted the transfer.
     */
    fun stop() {
        closeSocket()
    }

    private fun waitForTransfer(socket: ServerSocket): LanTransferServerResult {
        val deadline = System.currentTimeMillis() + lifetimeMillis
        var result: LanTransferServerResult? = null

        while (result == null) {
            result = acceptTransfer(socket, deadline)
        }

        return checkNotNull(result)
    }

    private fun acceptTransfer(socket: ServerSocket, deadline: Long): LanTransferServerResult? {
        val remainingMillis = deadline - System.currentTimeMillis()

        return if (remainingMillis <= 0) {
            LanTransferServerResult.Expired
        } else {
            serveNextConnection(socket, remainingMillis.toInt())
        }
    }

    private fun serveNextConnection(socket: ServerSocket, timeoutMillis: Int): LanTransferServerResult? {
        socket.soTimeout = timeoutMillis

        return try {
            val served = socket.accept().use { sendContainer(it) }

            if (served) LanTransferServerResult.Transferred else null
        } catch (_: SocketTimeoutException) {
            LanTransferServerResult.Expired
        } catch (_: IOException) {
            Log.d("Failed to serve a connection of the LAN transfer")
            LanTransferServerResult.Failed
        }
    }

    private fun sendContainer(connection: Socket): Boolean {
        return try {
            connection.soTimeout = CONNECTION_TIMEOUT_MILLIS

            val input = DataInputStream(connection.getInputStream())
            val magic = ByteArray(MAGIC_LENGTH)
            val requestId = ByteArray(TRANSFER_ID_LENGTH)
            input.readFully(magic)
            input.readFully(requestId)

            val isTransferRequested = isTransferRequested(magic = magic, requestId = requestId)
            if (isTransferRequested) {
                writeContainer(connection)
            }

            isTransferRequested
        } catch (_: IOException) {
            Log.d("Discarded an incomplete request of the LAN transfer")
            false
        }
    }

    private fun isTransferRequested(magic: ByteArray, requestId: ByteArray): Boolean {
        return MAGIC.contentEquals(magic) && MessageDigest.isEqual(transferId, requestId)
    }

    private fun writeContainer(connection: Socket) {
        val output = DataOutputStream(connection.getOutputStream())
        output.writeInt(container.size)
        output.write(container)
        output.flush()
    }

    private fun closeSocket() {
        val socket = serverSocket ?: return
        serverSocket = null
        socket.close()
    }
}

internal sealed interface LanTransferServerResult {
    data object Transferred : LanTransferServerResult

    data object Expired : LanTransferServerResult

    data object Failed : LanTransferServerResult
}
