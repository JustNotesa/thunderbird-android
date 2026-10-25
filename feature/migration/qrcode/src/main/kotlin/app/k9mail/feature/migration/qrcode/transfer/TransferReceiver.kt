package app.k9mail.feature.migration.qrcode.transfer

import app.k9mail.feature.migration.qrcode.transfer.hotspot.HotspotConnectResult
import app.k9mail.feature.migration.qrcode.transfer.hotspot.HotspotNetworkConnector
import javax.net.SocketFactory
import net.thunderbird.legacy.logging.Log

private const val ADDRESS_OCTET_COUNT = 4
private const val SUBNET_OCTET_COUNT = 3

/**
 * Receives an account transfer: fetches the encrypted container of the transfer from the sending device, decrypts it
 * with the key material of the QR code and reads the accounts it holds. The container is fetched from the local network
 * both devices are connected to, or from the local-only hotspot of the sending device.
 *
 * The key material can only be used for a single transfer, so it is cleared and the decrypted container is wiped when
 * the transfer has been received, successfully or not.
 */
internal class TransferReceiver(
    private val fetcher: TransferContainerFetcher,
    private val hotspotConnector: HotspotNetworkConnector,
    private val addressProvider: LocalNetworkAddressProvider = LocalNetworkAddressProvider { null },
) {
    /**
     * Receives the transfer described by [qrCode].
     *
     * @return [TransferReceiveResult.Success] with the accounts JSON of the transfer.
     */
    suspend fun receive(qrCode: TransferQrCode): TransferReceiveResult {
        var plaintext: ByteArray? = null

        return try {
            when (val fetch = fetchContainer(qrCode)) {
                is ContainerFetch.Failed -> {
                    fetch.result
                }

                is ContainerFetch.Container -> {
                    val decrypted = TransferContainer.decrypt(fetch.container, qrCode.secrets)

                    if (decrypted is TransferContainerResult.Success) {
                        plaintext = decrypted.plaintext
                        readAccounts(plaintext)
                    } else {
                        containerFailure(decrypted)
                    }
                }
            }
        } finally {
            plaintext?.fill(0)
            qrCode.secrets.clear()
        }
    }

    private suspend fun fetchContainer(qrCode: TransferQrCode): ContainerFetch {
        return when (val route = qrCode.route) {
            is TransferRoute.Lan -> fetchOverLan(route = route, transferId = qrCode.secrets.transferId)

            is TransferRoute.Hotspot -> fetchOverHotspot(route = route, qrCode = qrCode)

            else -> {
                Log.d("Unsupported transfer route for receiving an account transfer")
                ContainerFetch.Failed(TransferReceiveResult.UnsupportedRoute)
            }
        }
    }

    private suspend fun fetchOverLan(route: TransferRoute.Lan, transferId: ByteArray): ContainerFetch {
        val result = fetcher.fetch(
            route = route.copy(hosts = orderedHosts(route.hosts)),
            transferId = transferId,
            socketFactory = SocketFactory.getDefault(),
        )

        return container(result)
    }

    private fun orderedHosts(hosts: List<String>): List<String> {
        val localAddresses = addressProvider.getAddresses().map { it.hostAddress }

        return orderHostsBySubnet(hosts = hosts, localAddresses = localAddresses)
    }

    private suspend fun fetchOverHotspot(route: TransferRoute.Hotspot, qrCode: TransferQrCode): ContainerFetch {
        val connected = hotspotConnector.connect(ssid = route.ssid, passphrase = route.passphrase)

        if (connected !is HotspotConnectResult.Connected) {
            Log.d("The hotspot of the sending device couldn't be joined")
            return ContainerFetch.Failed(TransferReceiveResult.HotspotUnavailable)
        }

        return try {
            val result = fetcher.fetch(
                route = TransferRoute.Lan(host = route.host, port = route.port),
                transferId = qrCode.secrets.transferId,
                socketFactory = connected.socketFactory,
            )

            container(result)
        } finally {
            // The hotspot stays joined until the transfer is over, so the connection has to be given up either way.
            connected.release()
        }
    }

    private fun container(result: LanTransferClientResult): ContainerFetch {
        return when (result) {
            is LanTransferClientResult.Success -> ContainerFetch.Container(result.container)
            LanTransferClientResult.Unreachable -> ContainerFetch.Failed(TransferReceiveResult.Unreachable)
            LanTransferClientResult.Failed -> ContainerFetch.Failed(TransferReceiveResult.TransferFailed)
        }
    }

    private fun containerFailure(result: TransferContainerResult): TransferReceiveResult {
        return when (result) {
            TransferContainerResult.UnsupportedFormat -> TransferReceiveResult.UnsupportedFormat
            else -> TransferReceiveResult.Corrupted
        }
    }

    private fun readAccounts(plaintext: ByteArray): TransferReceiveResult {
        val content = TransferPayload.read(String(plaintext, Charsets.UTF_8))

        return if (content == null) {
            TransferReceiveResult.UnsupportedFormat
        } else {
            TransferReceiveResult.Success(content.accountsJson)
        }
    }
}

/**
 * Orders the hosts of a LAN route so that the addresses in the same network as this device are tried first. The order
 * of the hosts is kept otherwise.
 */
internal fun orderHostsBySubnet(hosts: List<String>, localAddresses: List<String>): List<String> {
    val subnets = localAddresses.mapNotNull { subnetOf(it) }.toSet()
    val (inSubnet, others) = hosts.partition { subnetOf(it) in subnets }

    return inSubnet + others
}

private fun subnetOf(address: String): String? {
    val octets = address.split('.')
    val isIpv4 = octets.size == ADDRESS_OCTET_COUNT

    return if (isIpv4) octets.take(SUBNET_OCTET_COUNT).joinToString(".") else null
}

/** The outcome of asking the sending device for the container of the transfer. */
private sealed interface ContainerFetch {
    data class Container(val container: ByteArray) : ContainerFetch

    data class Failed(val result: TransferReceiveResult) : ContainerFetch
}

internal sealed interface TransferReceiveResult {
    data class Success(val accountsJson: String) : TransferReceiveResult

    data object Unreachable : TransferReceiveResult

    data object TransferFailed : TransferReceiveResult

    data object Corrupted : TransferReceiveResult

    data object UnsupportedFormat : TransferReceiveResult

    data object UnsupportedRoute : TransferReceiveResult

    data object HotspotUnavailable : TransferReceiveResult
}
