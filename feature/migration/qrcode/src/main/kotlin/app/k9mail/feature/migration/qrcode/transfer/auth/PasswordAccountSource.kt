package app.k9mail.feature.migration.qrcode.transfer.auth

import kotlinx.coroutines.flow.first
import net.thunderbird.core.android.account.LegacyAccountManager

/**
 * The accounts of this device, each with the password of its incoming server, if it has one.
 */
internal fun interface PasswordAccountSource {
    /**
     * Returns all accounts of this device.
     */
    suspend fun getAll(): List<Pair<PasswordAccount, String?>>
}

/**
 * Reads the accounts with the password of their incoming server from the accounts of the device.
 */
internal class LegacyPasswordAccountSource(
    private val accountManager: LegacyAccountManager,
) : PasswordAccountSource {
    override suspend fun getAll(): List<Pair<PasswordAccount, String?>> {
        return accountManager.getAll().first().map { account ->
            val displayName = account.name?.takeIf { it.isNotBlank() } ?: account.email
            val password = account.incomingServerSettings.password

            PasswordAccount(id = account.id.toString(), displayName = displayName) to password
        }
    }
}
