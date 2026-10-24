package app.k9mail.feature.migration.qrcode.transfer

import net.thunderbird.core.android.account.LegacyAccount
import net.thunderbird.core.android.account.LegacyAccountManager

/**
 * Accounts of the sending device, in the shape needed for an account transfer.
 */
internal fun interface TransferAccountSource {
    /**
     * Returns the accounts to send.
     */
    suspend fun getAccounts(): List<TransferAccount>
}

/**
 * Reads the accounts to send from the accounts of the device.
 */
internal class LegacyTransferAccountSource(
    private val accountManager: LegacyAccountManager,
) : TransferAccountSource {
    override suspend fun getAccounts(): List<TransferAccount> {
        return accountManager.findAll().map { it.toTransferAccount() }
    }

    private fun LegacyAccount.toTransferAccount(): TransferAccount {
        return TransferAccount(
            accountName = name,
            incoming = incomingServerSettings,
            outgoing = outgoingServerSettings,
            identities = identities.map { identity ->
                TransferIdentity(
                    emailAddress = identity.email ?: email,
                    displayName = identity.name.orEmpty(),
                )
            },
        )
    }
}
