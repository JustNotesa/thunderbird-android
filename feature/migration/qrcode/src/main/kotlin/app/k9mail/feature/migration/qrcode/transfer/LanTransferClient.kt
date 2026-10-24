package app.k9mail.feature.migration.qrcode.transfer

import java.io.DataInputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.SocketFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.thunderbird.legacy.logging.Log

private const val TAG = "LanTransferClient"

private val MAGIC = "TBX1".encodeToByteArray()
private const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 10_000
private const val DEFAULT_READ_TIMEOUT_MILLIS = 30_000
private const val DEFAULT_MAX_CONTAINER_SIZE = 1024 * 1024
private const val MIN_CONTAINER_SIZE = 1

/**
 * Fetches an encrypted transfer container from the sending device on the local network.
 */
internal class LanTransferClient(
    private val connectTimeoutMillis: Int = DEFAULT_CONNECT_TIMEOUT_MILLIS,
    private val readTimeoutMillis: Int = DEFAULT_READ_TIMEOUT_MILLIS,
    private val maxContainerSize: Int = DEFAULT_MAX_CONTAINER_SIZE,
) : TransferContainerFetcher {
    /**
     * Asks the device described by [route] for the container belonging to [transferId], over a socket created by
     * [socketFactory].
     *
     * @return [LanTransferClientResult.Success] with the container, [LanTransferClientResult.Unreachable] when there is
     * no device to talk to, [LanTransferClientResult.Failed] when the device didn't deliver a container.
     */
    override suspend fun fetch(
        route: TransferRoute.Lan,
        transferId: ByteArray,
        socketFactory: SocketFactory,
    ): LanTransferClientResult {
        return withContext(Dispatchers.IO) {
            val socket = connect(host = route.host, port = route.port, socketFactory = socketFactory)

            if (socket == null) {
                LanTransferClientResult.Unreachable
            } else {
                readContainer(socket, transferId)
            }
        }
    }

    private fun connect(host: String, port: Int, socketFactory: SocketFactory): Socket? {
        // The factory decides the network the transfer runs on, e.g. the hotspot of the sending device.
        val socket = socketFactory.createSocket()

        return try {
            socket.connect(InetSocketAddress(host, port), connectTimeoutMillis)
            socket.soTimeout = readTimeoutMillis
            socket
        } catch (_: IOException) {
            Log.d(TAG, "The device of the LAN transfer could not be reached")
            socket.close()
            null
        }
    }

    private fun readContainer(socket: Socket, transferId: ByteArray): LanTransferClientResult {
        return try {
            socket.use {
                writeRequest(it, transferId)

                val input = DataInputStream(it.getInputStream())
                val length = input.readInt()
                val isLengthValid = length in MIN_CONTAINER_SIZE..maxContainerSize

                if (isLengthValid) {
                    val container = ByteArray(length)
                    input.readFully(container)
                    LanTransferClientResult.Success(container)
                } else {
                    Log.d(TAG, "The container of the LAN transfer has an unexpected size")
                    LanTransferClientResult.Failed
                }
            }
        } catch (_: IOException) {
            Log.d(TAG, "Failed to read the container of the LAN transfer")
            LanTransferClientResult.Failed
        }
    }

    private fun writeRequest(socket: Socket, transferId: ByteArray) {
        val output = socket.getOutputStream()
        output.write(MAGIC)
        output.write(transferId)
        output.flush()
    }
}

internal sealed interface LanTransferClientResult {
    data class Success(val container: ByteArray) : LanTransferClientResult

    data object Unreachable : LanTransferClientResult

    data object Failed : LanTransferClientResult
}
