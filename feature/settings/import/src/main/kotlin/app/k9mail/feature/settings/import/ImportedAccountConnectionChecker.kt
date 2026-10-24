package app.k9mail.feature.settings.import

import app.k9mail.feature.account.server.validation.domain.ServerValidationDomainContract
import com.fsck.k9.mail.ServerSettings
import com.fsck.k9.mail.server.ServerSettingsValidationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.thunderbird.core.android.account.LegacyAccountDtoManager
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
    private val accountManager: LegacyAccountDtoManager,
    private val validateServerSettings: ServerValidationDomainContract.UseCase.ValidateServerSettings,
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AccountConnectionChecker {

    override suspend fun check(accountUuid: String): ConnectionCheckResult {
        val serverSettings = loadIncomingServerSettings(accountUuid)
            ?: return ConnectionCheckResult.NotChecked

        return validateConnection(serverSettings)
    }

    private suspend fun loadIncomingServerSettings(accountUuid: String): ServerSettings? {
        val accountId = try {
            AccountIdFactory.of(accountUuid)
        } catch (ignoredInvalidUuidException: IllegalArgumentException) {
            null
        } ?: return null

        return withContext(coroutineDispatcher) {
            try {
                accountManager.getById(accountId)?.incomingServerSettings
            } catch (ignoredException: Exception) {
                null
            }
        }
    }

    private suspend fun validateConnection(serverSettings: ServerSettings): ConnectionCheckResult {
        val validationResult = withContext(coroutineDispatcher) {
            withTimeoutOrNull(CONNECTION_CHECK_TIMEOUT) {
                validateServerSettingsOrNull(serverSettings)
            }
        }

        return when (validationResult) {
            ServerSettingsValidationResult.Success -> ConnectionCheckResult.Connected
            else -> ConnectionCheckResult.Failed
        }
    }

    private suspend fun validateServerSettingsOrNull(serverSettings: ServerSettings): ServerSettingsValidationResult? {
        return try {
            validateServerSettings.execute(serverSettings)
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (ignoredException: Exception) {
            null
        }
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
