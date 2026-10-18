package app.k9mail.feature.migration.qrcode.transfer

import java.io.IOException
import java.net.InetAddress
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.thunderbird.legacy.logging.Log

private const val MILLISECONDS_PER_SECOND = 1000L

/**
 * Sends the accounts of the device over the local network.
 *
 * The accounts are encrypted into a transfer container that is served once, to the first device asking for it with the
 * transfer ID of the QR code. The key material is only needed until the QR code has been formatted and is wiped
 * afterwards; the QR code holds the only copy of it that is left.
 */
internal class TransferSender(
    private val accountSource: TransferAccountSource,
    private val addressProvider: LocalNetworkAddressProvider,
    private val random: SecureRandom = SecureRandom(),
    private val clock: () -> Long = { System.currentTimeMillis() / MILLISECONDS_PER_SECOND },
    private val createServer: (ByteArray, ByteArray, InetAddress) -> LanTransferServer =
        { container, transferId, address -> LanTransferServer(container, transferId, address) },
) {
    /**
     * Serves the accounts of this device on the local network.
     *
     * @return [TransferSendResult.Ready] with the content of the QR code and the running transfer,
     * [TransferSendResult.NoWifi] when the device isn't on a network that can be reached,
     * [TransferSendResult.NoAccounts] when there is no account to send, [TransferSendResult.Failed] when no port could
     * be opened for the transfer.
     */
    suspend fun prepare(): TransferSendResult = withContext(Dispatchers.IO) {
        val address = addressProvider.getWifiAddress()

        if (address == null) {
            return@withContext TransferSendResult.NoWifi
        }

        val accounts = accountSource.getAccounts()

        if (accounts.isEmpty()) {
            TransferSendResult.NoAccounts
        } else {
            startTransfer(address = address, accounts = accounts)
        }
    }

    private fun startTransfer(address: InetAddress, accounts: List<TransferAccount>): TransferSendResult {
        val plaintext = TransferPayload.write(
            accountsJson = DesktopPayloadWriter.write(accounts),
            createdAt = clock(),
        ).encodeToByteArray()
        val secrets = TransferSecrets.generate(random)

        return try {
            startServing(address = address, plaintext = plaintext, secrets = secrets)
        } catch (_: IOException) {
            Log.d("Failed to open the port of the account transfer")
            TransferSendResult.Failed
        } finally {
            secrets.clear()
            plaintext.fill(0)
        }
    }

    private fun startServing(address: InetAddress, plaintext: ByteArray, secrets: TransferSecrets): TransferSendResult {
        val container = TransferContainer.encrypt(plaintext, secrets)
        // The server keeps serving after the key material is wiped, so it gets a copy of the transfer ID.
        val server = createServer(container, secrets.transferId.copyOf(), address)
        val port = server.start()
        val route = TransferRoute.Lan(host = address.hostAddress, port = port)
        val qrCode = TransferQrCode(secrets = secrets, route = route)

        return TransferSendResult.Ready(TransferSession(qrText = qrCode.format(), server = server))
    }
}

/**
 * Running transfer of the accounts of this device.
 *
 * Has to be stopped when the user aborted the transfer, the container is only served while the transfer runs.
 */
internal class TransferSession(
    val qrText: String,
    private val server: LanTransferServer,
) {
    /**
     * Waits for a device to fetch the transfer container.
     */
    suspend fun awaitTransfer(): LanTransferServerResult = server.awaitTransfer()

    /**
     * Stops serving the transfer container. To be called when the user aborted the transfer.
     */
    fun stop() {
        server.stop()
    }
}

internal sealed interface TransferSendResult {
    data class Ready(val session: TransferSession) : TransferSendResult

    data object NoWifi : TransferSendResult

    data object NoAccounts : TransferSendResult

    data object Failed : TransferSendResult
}
