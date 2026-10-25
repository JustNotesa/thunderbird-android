package app.k9mail.feature.migration.qrcode.transfer

import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Inet4Address

/**
 * Local network addresses of the device, used to reach the sending device with the transfer container.
 */
internal fun interface LocalNetworkAddressProvider {
    /**
     * Returns the IPv4 address of the current Wi-Fi network.
     *
     * @return The address, or `null` when the device isn't on a Wi-Fi network that can be reached.
     */
    fun getWifiAddress(): Inet4Address?

    /**
     * Returns all IPv4 addresses of the device on the local network, the Wi-Fi ones first.
     */
    fun getAddresses(): List<Inet4Address> = listOfNotNull(getWifiAddress())
}

/**
 * Reads the local addresses of the device from the connectivity of the system.
 */
internal class AndroidLocalNetworkAddressProvider(
    private val connectivityManager: ConnectivityManager,
) : LocalNetworkAddressProvider {
    override fun getWifiAddress(): Inet4Address? {
        return wifiAddresses().firstOrNull()
    }

    override fun getAddresses(): List<Inet4Address> {
        return (wifiAddresses() + otherAddresses()).distinct()
    }

    private fun wifiAddresses(): List<Inet4Address> {
        return addressesOf(networks().filter { isWifiNetwork(it) })
    }

    private fun otherAddresses(): List<Inet4Address> {
        return addressesOf(networks().filterNot { isWifiNetwork(it) })
    }

    private fun networks(): List<Network> {
        // The registered networks are deprecated in favour of network callbacks, but a callback only observes changes
        // of networks that are registered for it, while the transfer needs the address of the Wi-Fi network right now.
        @Suppress("DEPRECATION")
        return connectivityManager.allNetworks.toList()
    }

    private fun isWifiNetwork(network: Network): Boolean {
        return connectivityManager.getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    private fun addressesOf(networks: List<Network>): List<Inet4Address> {
        return networks.flatMap { network ->
            connectivityManager.getLinkProperties(network)
                ?.linkAddresses
                ?.mapNotNull { reachableAddressOf(it) }
                .orEmpty()
        }
    }

    private fun reachableAddressOf(linkAddress: LinkAddress): Inet4Address? {
        val address = linkAddress.address as? Inet4Address ?: return null
        val isReachable = !address.isLoopbackAddress && !address.isLinkLocalAddress

        return address.takeIf { isReachable }
    }
}
