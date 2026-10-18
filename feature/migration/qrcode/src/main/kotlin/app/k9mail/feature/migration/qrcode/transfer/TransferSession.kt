package app.k9mail.feature.migration.qrcode.transfer

/**
 * Running transfer of the accounts of this device.
 *
 * Has to be stopped when the user aborted the transfer, the container is only served while the transfer runs. The
 * [onStop] callback releases whatever the transfer needed besides the container, like the hotspot of this device.
 */
internal class TransferSession(
    val qrText: String,
    private val server: LanTransferServer,
    private val onStop: () -> Unit = {},
) {
    /**
     * Waits for a device to fetch the transfer container. Everything the transfer needed has ended by then, so it is
     * released before this returns.
     */
    suspend fun awaitTransfer(): LanTransferServerResult {
        val result = server.awaitTransfer()

        onStop()

        return result
    }

    /**
     * Stops serving the transfer container. To be called when the user aborted the transfer.
     */
    fun stop() {
        server.stop()
        onStop()
    }
}

internal sealed interface TransferSendResult {
    data class Ready(val session: TransferSession) : TransferSendResult

    data object NoWifi : TransferSendResult

    data object NoAccounts : TransferSendResult

    data object HotspotUnsupported : TransferSendResult

    data object Failed : TransferSendResult
}
