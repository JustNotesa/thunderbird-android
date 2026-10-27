package app.k9mail.feature.migration.qrcode.transfer

import com.fsck.k9.mail.AuthType
import kotlinx.coroutines.flow.first
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
        return accountManager.getAll().first().map { it.toTransferAccount() }
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
            oAuthState = oAuthStateForTransfer(),
        )
    }

    /**
     * The sign-in state of the account, so the receiving device doesn't have to sign in again. Only OAuth accounts
     * have one.
     */
    private fun LegacyAccount.oAuthStateForTransfer(): String? {
        val isOAuthAccount = incomingServerSettings.authenticationType == AuthType.XOAUTH2 ||
            outgoingServerSettings.authenticationType == AuthType.XOAUTH2

        return oAuthState.takeIf { isOAuthAccount }
    }
}
