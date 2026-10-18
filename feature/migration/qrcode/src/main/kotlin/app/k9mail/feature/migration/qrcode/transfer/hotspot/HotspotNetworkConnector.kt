package app.k9mail.feature.migration.qrcode.transfer.hotspot

import javax.net.SocketFactory

/**
 * Joins the local-only hotspot of the sending device, so the transfer container can be fetched from it without both
 * devices having to be on the same network.
 */
internal interface HotspotNetworkConnector {
    /**
     * Connects to the hotspot with [ssid] and [passphrase] and waits until it can be reached.
     *
     * @return [HotspotConnectResult.Connected] with a socket factory bound to the hotspot network and the release of
     * the connection, or [HotspotConnectResult.Unsupported] / [HotspotConnectResult.Failed] when there is no hotspot
     * to connect to.
     */
    suspend fun connect(ssid: String, passphrase: String): HotspotConnectResult
}

/**
 * Result of joining the local-only hotspot of the sending device.
 */
internal sealed interface HotspotConnectResult {
    /**
     * The hotspot is joined and can be reached over [socketFactory]. [release] leaves it again and has to be called
     * once the transfer is done.
     */
    data class Connected(
        val socketFactory: SocketFactory,
        val release: () -> Unit,
    ) : HotspotConnectResult

    /**
     * The device can't join a local-only hotspot.
     */
    data object Unsupported : HotspotConnectResult

    /**
     * The hotspot couldn't be joined.
     */
    data object Failed : HotspotConnectResult
}

/**
 * Used below Android 10, where the app can't join a local-only hotspot for the transfer.
 */
internal object UnsupportedHotspotNetworkConnector : HotspotNetworkConnector {
    override suspend fun connect(ssid: String, passphrase: String): HotspotConnectResult =
        HotspotConnectResult.Unsupported
}
