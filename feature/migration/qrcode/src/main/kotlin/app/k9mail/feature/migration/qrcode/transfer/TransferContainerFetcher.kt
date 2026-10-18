package app.k9mail.feature.migration.qrcode.transfer

import javax.net.SocketFactory

/**
 * Fetches the encrypted container of an account transfer from the sending device.
 */
internal fun interface TransferContainerFetcher {
    /**
     * Asks the device described by [route] for the container belonging to [transferId], over a socket created by
     * [socketFactory].
     */
    suspend fun fetch(
        route: TransferRoute.Lan,
        transferId: ByteArray,
        socketFactory: SocketFactory,
    ): LanTransferClientResult
}
