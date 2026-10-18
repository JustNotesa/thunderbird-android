package app.k9mail.feature.migration.qrcode.transfer

import net.thunderbird.legacy.logging.Log

/**
 * Receives an account transfer: fetches the encrypted container of the transfer from the sending device, decrypts it
 * with the key material of the QR code and reads the accounts it holds.
 *
 * The key material can only be used for a single transfer, so it is cleared and the decrypted container is wiped when
 * the transfer has been received, successfully or not.
 */
internal class TransferReceiver(
    private val fetcher: TransferContainerFetcher,
) {
    /**
     * Receives the transfer described by [qrCode].
     *
     * @return [TransferReceiveResult.Success] with the accounts JSON of the transfer.
     */
    suspend fun receive(qrCode: TransferQrCode): TransferReceiveResult {
        var plaintext: ByteArray? = null

        return try {
            val route = qrCode.route as? TransferRoute.Lan

            if (route == null) {
                Log.d("Unsupported transfer route for receiving an account transfer")
                return TransferReceiveResult.UnsupportedRoute
            }

            val container = fetcher.fetch(route, qrCode.secrets.transferId)

            if (container is LanTransferClientResult.Success) {
                val decrypted = TransferContainer.decrypt(container.container, qrCode.secrets)

                if (decrypted is TransferContainerResult.Success) {
                    plaintext = decrypted.plaintext
                    readAccounts(plaintext)
                } else {
                    containerFailure(decrypted)
                }
            } else {
                fetchFailure(container)
            }
        } finally {
            plaintext?.fill(0)
            qrCode.secrets.clear()
        }
    }

    private fun fetchFailure(result: LanTransferClientResult): TransferReceiveResult {
        return when (result) {
            LanTransferClientResult.Unreachable -> TransferReceiveResult.Unreachable
            else -> TransferReceiveResult.TransferFailed
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

internal sealed interface TransferReceiveResult {
    data class Success(val accountsJson: String) : TransferReceiveResult

    data object Unreachable : TransferReceiveResult

    data object TransferFailed : TransferReceiveResult

    data object Corrupted : TransferReceiveResult

    data object UnsupportedFormat : TransferReceiveResult

    data object UnsupportedRoute : TransferReceiveResult
}
