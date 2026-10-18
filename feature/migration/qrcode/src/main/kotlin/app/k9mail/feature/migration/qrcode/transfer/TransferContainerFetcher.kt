package app.k9mail.feature.migration.qrcode.transfer

/**
 * Fetches the encrypted container of an account transfer from the sending device.
 */
internal fun interface TransferContainerFetcher {
    /**
     * Asks the device described by [route] for the container belonging to [transferId].
     */
    suspend fun fetch(route: TransferRoute.Lan, transferId: ByteArray): LanTransferClientResult
}
