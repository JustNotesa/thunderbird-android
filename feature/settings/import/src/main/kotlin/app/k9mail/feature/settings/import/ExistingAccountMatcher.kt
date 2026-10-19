package app.k9mail.feature.settings.import

import com.fsck.k9.preferences.AccountDescription
import net.thunderbird.core.android.account.LegacyAccountDtoManager

/**
 * An account that is already set up on this device.
 */
internal data class ExistingAccount(
    val email: String?,
    val incomingServerHost: String?,
    val incomingServerUsername: String?,
)

internal interface ExistingAccountProvider {
    fun getExistingAccounts(): List<ExistingAccount>
}

internal class AccountManagerExistingAccountProvider(
    private val accountManager: LegacyAccountDtoManager,
) : ExistingAccountProvider {
    override fun getExistingAccounts(): List<ExistingAccount> {
        return accountManager.getAccounts().map { account ->
            ExistingAccount(
                email = account.identities.firstOrNull()?.email,
                incomingServerHost = account.incomingServerSettings.host,
                incomingServerUsername = account.incomingServerSettings.username,
            )
        }
    }
}

/**
 * Decides whether an account of an import file is already set up on this device. Imported accounts always get a new
 * UUID, so the account's email address, incoming server host and incoming server username are compared instead.
 */
internal class ExistingAccountMatcher(
    private val existingAccountProvider: ExistingAccountProvider,
) {
    fun isAlreadySetUp(account: AccountDescription): Boolean {
        return isAlreadySetUp(account.email, account.incomingServerHost, account.incomingServerUsername)
    }

    private fun isAlreadySetUp(email: String?, serverHost: String?, username: String?): Boolean {
        return existingAccountProvider.getExistingAccounts().any { matches(it, email, serverHost, username) }
    }

    private fun matches(account: ExistingAccount, email: String?, host: String?, username: String?): Boolean {
        return account.email?.equals(email, ignoreCase = true) == true &&
            account.incomingServerHost?.equals(host, ignoreCase = true) == true &&
            account.incomingServerUsername == username
    }
}
