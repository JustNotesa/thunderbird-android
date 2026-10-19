package app.k9mail.feature.migration.qrcode.transfer.hotspot

internal class FakeHotspotController(
    private val result: HotspotStartResult,
) : HotspotController {
    var startCount = 0
        private set

    var stopCount = 0
        private set

    override suspend fun start(): HotspotStartResult {
        startCount++

        return result
    }

    override fun stop() {
        stopCount++
    }
}
