package app.k9mail.feature.settings.import

import app.k9mail.feature.account.server.validation.domain.ServerValidationDomainContract
import com.fsck.k9.mail.ServerSettings
import com.fsck.k9.mail.oauth.AuthStateStorage
import com.fsck.k9.mail.server.ServerSettingsValidationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.thunderbird.core.android.account.LegacyAccount
import net.thunderbird.core.android.account.LegacyAccountManager
import net.thunderbird.feature.account.AccountIdFactory

private const val CONNECTION_CHECK_TIMEOUT = 20_000L

/**
 * Checks whether the server connection of an imported account works.
 */
internal interface AccountConnectionChecker {
    suspend fun check(accountUuid: String): ConnectionCheckResult
}

/**
 * Checks the incoming server connection of an account that was just imported.
 */
internal class ImportedAccountConnectionChecker(
    private val accountManager: LegacyAccountManager,
    private val validateServerSettings: ServerValidationDomainContract.UseCase.ValidateServerSettings,
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AccountConnectionChecker {

    override suspend fun check(accountUuid: String): ConnectionCheckResult {
        val importedAccount = loadImportedAccount(accountUuid) ?: return ConnectionCheckResult.NotChecked

        return validateConnection(importedAccount)
    }

    private suspend fun loadImportedAccount(accountUuid: String): ImportedAccountData? {
        val accountId = try {
            AccountIdFactory.of(accountUuid)
        } catch (ignoredInvalidUuidException: IllegalArgumentException) {
            null
        } ?: return null

        return withContext(coroutineDispatcher) {
            try {
                accountManager.findById(accountId)?.let { account ->
                    ImportedAccountData(
                        account = account,
                        incomingServerSettings = account.incomingServerSettings,
                    )
                }
            } catch (ignoredException: Exception) {
                null
            }
        }
    }

    private fun createAuthStateStorage(account: LegacyAccount): AuthStateStorage {
        return ImportedAccountAuthStateStorage(
            account = account,
            accountManager = accountManager,
        )
    }

    private suspend fun validateConnection(importedAccount: ImportedAccountData): ConnectionCheckResult {
        val validationResult = withContext(coroutineDispatcher) {
            withTimeoutOrNull(CONNECTION_CHECK_TIMEOUT) {
                validateServerSettingsOrNull(importedAccount)
            }
        }

        return when (validationResult) {
            ServerSettingsValidationResult.Success -> ConnectionCheckResult.Connected
            else -> ConnectionCheckResult.Failed
        }
    }

    private suspend fun validateServerSettingsOrNull(
        importedAccount: ImportedAccountData,
    ): ServerSettingsValidationResult? {
        return try {
            validateServerSettings.execute(
                settings = importedAccount.incomingServerSettings,
                authStateStorage = createAuthStateStorage(importedAccount.account),
            )
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (ignoredException: Exception) {
            null
        }
    }
}

/**
 * The data of an imported account that's needed to check its server connection.
 */
private data class ImportedAccountData(
    val account: LegacyAccount,
    val incomingServerSettings: ServerSettings,
)

/**
 * Provides the authentication state of an imported account and stores an updated state in that account.
 */
private class ImportedAccountAuthStateStorage(
    private val account: LegacyAccount,
    private val accountManager: LegacyAccountManager,
) : AuthStateStorage {
    private var authorizationState: String? = account.oAuthState

    override fun getAuthorizationState(): String? = authorizationState

    override fun updateAuthorizationState(authorizationState: String?) {
        this.authorizationState = authorizationState

        // The account is immutable, so the new state is saved by copying the latest version of the account. This way
        // changes made to the account in the meantime aren't lost.
        accountManager.updateSync(account.id) { it.copy(oAuthState = authorizationState) }
    }
}

/**
 * The result of checking the server connection of an imported account.
 */
internal sealed interface ConnectionCheckResult {
    object Connected : ConnectionCheckResult
    object Failed : ConnectionCheckResult
    object NotChecked : ConnectionCheckResult
}
