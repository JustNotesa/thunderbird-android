package app.k9mail.feature.migration.qrcode.transfer.hotspot

/**
 * Used below Android 8, where the system can't run a local-only hotspot for the transfer.
 */
internal object UnsupportedHotspotController : HotspotController {
    override suspend fun start(): HotspotStartResult = HotspotStartResult.Unsupported

    /** Stops the hotspot. Has no effect, the device can't run one. */
    override fun stop() = Unit
}
