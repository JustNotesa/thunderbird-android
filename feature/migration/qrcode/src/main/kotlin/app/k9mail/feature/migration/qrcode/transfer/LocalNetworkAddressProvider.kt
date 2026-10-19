package app.k9mail.feature.migration.qrcode.transfer

import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Inet4Address

/**
 * Local network address of the sending device, used to reach it with the transfer container.
 */
internal fun interface LocalNetworkAddressProvider {
    /**
     * Returns the IPv4 address of the current Wi-Fi network.
     *
     * @return The address, or `null` when the device isn't on a Wi-Fi network that can be reached.
     */
    fun getWifiAddress(): Inet4Address?
}

/**
 * Reads the Wi-Fi address of the device from the connectivity of the system.
 */
internal class AndroidLocalNetworkAddressProvider(
    private val connectivityManager: ConnectivityManager,
) : LocalNetworkAddressProvider {
    override fun getWifiAddress(): Inet4Address? {
        // The registered networks are deprecated in favour of network callbacks, but a callback only observes changes
        // of networks that are registered for it, while the transfer needs the address of the Wi-Fi network right now.
        @Suppress("DEPRECATION")
        val networks = connectivityManager.allNetworks

        return networks.firstNotNullOfOrNull { wifiAddressOf(it) }
    }

    private fun wifiAddressOf(network: Network): Inet4Address? {
        val linkAddresses = connectivityManager.getNetworkCapabilities(network)
            ?.takeIf { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }
            ?.let { connectivityManager.getLinkProperties(network) }
            ?.linkAddresses

        return linkAddresses?.firstNotNullOfOrNull { reachableAddressOf(it) }
    }

    private fun reachableAddressOf(linkAddress: LinkAddress): Inet4Address? {
        val address = linkAddress.address as? Inet4Address ?: return null
        val isReachable = !address.isLoopbackAddress && !address.isLinkLocalAddress

        return address.takeIf { isReachable }
    }
}
