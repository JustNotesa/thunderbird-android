package app.k9mail.feature.migration.qrcode.transfer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MILLISECONDS_PER_SECOND = 1000L

/**
 * Exports the accounts of this device as the plaintext of an account transfer, so the user can save it as a note in a
 * password manager and paste it into the app on the other device.
 *
 * The text isn't encrypted here, the password manager encrypts and synchronizes the note it is stored in. It is never
 * logged and only lives in the state of the screen until the user is done with it.
 */
internal class TransferTextExporter(
    private val accountSource: TransferAccountSource,
    private val clock: () -> Long = { System.currentTimeMillis() / MILLISECONDS_PER_SECOND },
) {
    /**
     * Writes the accounts of this device as the plaintext of an account transfer.
     *
     * @return [TransferTextResult.Success] with the text to save in the password manager,
     * [TransferTextResult.NoAccounts] when there is no account to export.
     */
    suspend fun exportText(): TransferTextResult = withContext(Dispatchers.IO) {
        val accounts = accountSource.getAccounts()

        if (accounts.isEmpty()) {
            TransferTextResult.NoAccounts
        } else {
            TransferTextResult.Success(
                TransferPayload.write(accountsJson = DesktopPayloadWriter.write(accounts), createdAt = clock()),
            )
        }
    }
}

internal sealed interface TransferTextResult {
    data class Success(val text: String) : TransferTextResult

    data object NoAccounts : TransferTextResult
}
