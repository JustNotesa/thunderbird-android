package app.k9mail.feature.migration.qrcode.transfer

import app.k9mail.feature.migration.qrcode.transfer.hotspot.HotspotController
import app.k9mail.feature.migration.qrcode.transfer.hotspot.HotspotStartResult
import java.io.IOException
import java.net.InetAddress
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.thunderbird.legacy.logging.Log

private const val TAG = "TransferSender"
private const val MILLISECONDS_PER_SECOND = 1000L
private const val MAX_HOST_COUNT = 4
private const val WILDCARD_ADDRESS = "0.0.0.0"

/**
 * Sends the accounts of the device, either over the local network both devices are connected to, or over the
 * local-only hotspot of this device.
 *
 * The accounts are encrypted into a transfer container that is served once, to the first device asking for it with the
 * transfer ID of the QR code. The key material is only needed until the QR code has been formatted and is wiped
 * afterwards; the QR code holds the only copy of it that is left.
 */
internal class TransferSender(
    private val accountSource: TransferAccountSource,
    private val addressProvider: LocalNetworkAddressProvider,
    private val hotspotController: HotspotController,
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
            val hosts = lanHosts(primary = address)
            startTransfer(
                address = bindAddress(primary = address, hosts = hosts),
                accounts = accounts,
                createRoute = { port -> TransferRoute.Lan(hosts = hosts, port = port) },
            )
        }
    }

    /**
     * Serves the accounts of this device on its own local-only hotspot, so the other device doesn't have to be on the
     * same network. The hotspot only runs as long as the transfer does.
     *
     * @return [TransferSendResult.Ready] with the content of the QR code and the running transfer,
     * [TransferSendResult.NoAccounts] when there is no account to send, [TransferSendResult.HotspotUnsupported] when
     * the device can't run a local-only hotspot, [TransferSendResult.Failed] when neither the hotspot nor a port for
     * the transfer could be opened.
     */
    suspend fun prepareHotspot(): TransferSendResult = withContext(Dispatchers.IO) {
        val accounts = accountSource.getAccounts()

        if (accounts.isEmpty()) {
            return@withContext TransferSendResult.NoAccounts
        }

        when (val hotspot = hotspotController.start()) {
            is HotspotStartResult.Started -> startHotspotTransfer(hotspot = hotspot, accounts = accounts)
            HotspotStartResult.Unsupported -> TransferSendResult.HotspotUnsupported
            HotspotStartResult.Failed -> TransferSendResult.Failed
        }
    }

    private fun startHotspotTransfer(
        hotspot: HotspotStartResult.Started,
        accounts: List<TransferAccount>,
    ): TransferSendResult {
        return startTransfer(
            address = hotspot.address,
            accounts = accounts,
            createRoute = { port -> hotspotRoute(hotspot, port) },
            onStop = hotspotController::stop,
        )
    }

    private fun startTransfer(
        address: InetAddress,
        accounts: List<TransferAccount>,
        createRoute: (Int) -> TransferRoute,
        onStop: () -> Unit = {},
    ): TransferSendResult {
        val plaintext = TransferPayload.write(
            accountsJson = DesktopPayloadWriter.write(accounts),
            createdAt = clock(),
        ).encodeToByteArray()
        val secrets = TransferSecrets.generate(random)

        return try {
            startServing(
                address = address,
                plaintext = plaintext,
                secrets = secrets,
                createRoute = createRoute,
                onStop = onStop,
            )
        } catch (_: IOException) {
            Log.d(TAG, "Failed to open the port of the account transfer")
            // Without a port to serve the container on there is nothing the hotspot could be used for.
            onStop()
            TransferSendResult.Failed
        } finally {
            secrets.clear()
            plaintext.fill(0)
        }
    }

    private fun startServing(
        address: InetAddress,
        plaintext: ByteArray,
        secrets: TransferSecrets,
        createRoute: (Int) -> TransferRoute,
        onStop: () -> Unit,
    ): TransferSendResult {
        val container = TransferContainer.encrypt(plaintext, secrets)
        // The server keeps serving after the key material is wiped, so it gets a copy of the transfer ID.
        val server = createServer(container, secrets.transferId.copyOf(), address)
        val port = server.start()
        val qrCode = TransferQrCode(secrets = secrets, route = createRoute(port))

        return TransferSendResult.Ready(
            TransferSession(qrText = qrCode.format(), server = server, onStop = onStop),
        )
    }

    /**
     * The addresses the other device can reach this device at. The primary one is the Wi-Fi address, the others are
     * the addresses of the other networks this device is connected to.
     */
    private fun lanHosts(primary: InetAddress): List<String> {
        val hosts = listOf(primary.hostAddress) + privateAddresses()

        return hosts.distinct().take(MAX_HOST_COUNT)
    }

    private fun privateAddresses(): List<String> {
        return addressProvider.getAddresses()
            .filter { it.isSiteLocalAddress }
            .map { it.hostAddress }
    }

    private fun bindAddress(primary: InetAddress, hosts: List<String>): InetAddress {
        // The container has to be served on every address the other device may use to reach this device.
        return if (hosts.size > 1) InetAddress.getByName(WILDCARD_ADDRESS) else primary
    }
}

private fun hotspotRoute(hotspot: HotspotStartResult.Started, port: Int): TransferRoute {
    return TransferRoute.Hotspot(
        ssid = hotspot.ssid,
        passphrase = hotspot.passphrase,
        host = hotspot.address.hostAddress,
        port = port,
    )
}
