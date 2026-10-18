package app.k9mail.feature.migration.qrcode.transfer.hotspot

import java.net.Inet4Address

/**
 * Local-only hotspot of the sending device, used to offer the transfer container to the receiving device without
 * both devices having to be on the same network.
 */
internal interface HotspotController {
    /**
     * Starts the hotspot and waits until it can be reached.
     *
     * @return [HotspotStartResult.Started] with the data the receiving device needs to connect, or
     * [HotspotStartResult.Unsupported] / [HotspotStartResult.Failed] when there is no usable hotspot.
     */
    suspend fun start(): HotspotStartResult

    /**
     * Stops the hotspot. Has no effect when it isn't running.
     */
    fun stop()
}

/**
 * Result of starting the local-only hotspot.
 */
internal sealed interface HotspotStartResult {
    /**
     * The hotspot is running and can be reached at [address] with [ssid] and [passphrase].
     */
    data class Started(
        val ssid: String,
        val passphrase: String,
        val address: Inet4Address,
    ) : HotspotStartResult

    /**
     * The device can't run a local-only hotspot, or the hotspot was started without a WPA2/WPA3 passphrase.
     */
    data object Unsupported : HotspotStartResult

    /**
     * The hotspot couldn't be started.
     */
    data object Failed : HotspotStartResult
}
