package app.k9mail.feature.settings.import

import app.k9mail.feature.account.server.validation.domain.ServerValidationDomainContract
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.fsck.k9.mail.AuthType
import com.fsck.k9.mail.ConnectionSecurity
import com.fsck.k9.mail.ServerSettings
import com.fsck.k9.mail.oauth.AuthStateStorage
import com.fsck.k9.mail.server.ServerSettingsValidationResult
import java.io.IOException
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import net.thunderbird.core.android.account.LegacyAccount
import net.thunderbird.core.android.account.LegacyAccountManager
import net.thunderbird.feature.account.AccountIdFactory
import net.thunderbird.feature.account.storage.profile.AvatarDto
import net.thunderbird.feature.account.storage.profile.AvatarTypeDto
import net.thunderbird.feature.account.storage.profile.ProfileDto
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify

private const val ACCOUNT_UUID = "123e4567-e89b-12d3-a456-426655440000"
private const val OAUTH_STATE = "oauth state"
private const val UPDATED_OAUTH_STATE = "updated oauth state"

class ImportedAccountConnectionCheckerTest {
    private val accountManager = mock<LegacyAccountManager>()
    private val validateServerSettings = FakeValidateServerSettings()
    private val testSubject = ImportedAccountConnectionChecker(
        accountManager = accountManager,
        validateServerSettings = validateServerSettings,
        coroutineDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `account with working server connection should be connected`() = runTest {
        stubAccount(createAccount())
        validateServerSettings.result = { ServerSettingsValidationResult.Success }

        val result = testSubject.check(ACCOUNT_UUID)

        assertThat(result).isEqualTo(ConnectionCheckResult.Connected)
    }

    @Test
    fun `account with failing server connection should not be connected`() = runTest {
        stubAccount(createAccount())
        validateServerSettings.result = { ServerSettingsValidationResult.NetworkError(IOException()) }

        val result = testSubject.check(ACCOUNT_UUID)

        assertThat(result).isEqualTo(ConnectionCheckResult.Failed)
    }

    @Test
    fun `account with failing server validation should not be connected`() = runTest {
        stubAccount(createAccount())
        validateServerSettings.result = { throw IllegalStateException("Validation failed") }

        val result = testSubject.check(ACCOUNT_UUID)

        assertThat(result).isEqualTo(ConnectionCheckResult.Failed)
    }

    @Test
    fun `account that doesn't exist should not be checked`() = runTest {
        accountManager.stub {
            on { findById(any()) } doReturn null
        }

        val result = testSubject.check(ACCOUNT_UUID)

        assertThat(result).isEqualTo(ConnectionCheckResult.NotChecked)
    }

    @Test
    fun `account with invalid UUID should not be checked`() = runTest {
        val result = testSubject.check("not-a-valid-uuid")

        assertThat(result).isEqualTo(ConnectionCheckResult.NotChecked)
    }

    @Test
    fun `connection check that times out should not be connected`() = runTest {
        stubAccount(createAccount())
        validateServerSettings.result = { awaitCancellation() }
        val testSubject = ImportedAccountConnectionChecker(
            accountManager = accountManager,
            validateServerSettings = validateServerSettings,
            coroutineDispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = testSubject.check(ACCOUNT_UUID)

        assertThat(result).isEqualTo(ConnectionCheckResult.Failed)
    }

    @Test
    fun `validator should use authentication state of imported account`() = runTest {
        stubAccount(createAccount(authenticationType = AuthType.XOAUTH2, oAuthState = OAUTH_STATE))
        validateServerSettings.result = { ServerSettingsValidationResult.Success }

        testSubject.check(ACCOUNT_UUID)

        val authStateStorage = requireNotNull(validateServerSettings.authStateStorage)
        assertThat(authStateStorage.getAuthorizationState()).isEqualTo(OAUTH_STATE)
    }

    @Test
    fun `authentication state update should be saved to imported account`() = runTest {
        val account = createAccount(authenticationType = AuthType.XOAUTH2, oAuthState = OAUTH_STATE)
        stubAccount(account)
        validateServerSettings.result = { ServerSettingsValidationResult.Success }

        testSubject.check(ACCOUNT_UUID)

        val authStateStorage = requireNotNull(validateServerSettings.authStateStorage)
        authStateStorage.updateAuthorizationState(UPDATED_OAUTH_STATE)

        assertThat(authStateStorage.getAuthorizationState()).isEqualTo(UPDATED_OAUTH_STATE)

        val transform = argumentCaptor<(LegacyAccount) -> LegacyAccount>()
        verify(accountManager).updateSync(eq(account.id), transform.capture())
        assertThat(transform.firstValue(account).oAuthState).isEqualTo(UPDATED_OAUTH_STATE)
    }

    @Test
    fun `password account should be checked without authentication state`() = runTest {
        stubAccount(createAccount())
        validateServerSettings.result = { ServerSettingsValidationResult.Success }

        val result = testSubject.check(ACCOUNT_UUID)

        assertThat(result).isEqualTo(ConnectionCheckResult.Connected)
        val authStateStorage = requireNotNull(validateServerSettings.authStateStorage)
        assertThat(authStateStorage.getAuthorizationState()).isNull()
        verify(accountManager, never()).updateSync(any(), any())
    }

    private fun stubAccount(account: LegacyAccount) {
        accountManager.stub {
            on { findById(any()) } doReturn account
        }
    }

    private fun createAccount(
        authenticationType: AuthType = AuthType.PLAIN,
        oAuthState: String? = null,
    ): LegacyAccount {
        val accountId = AccountIdFactory.of(ACCOUNT_UUID)

        return LegacyAccount(
            id = accountId,
            name = "Account",
            email = "user@example.com",
            profile = ProfileDto(
                id = accountId,
                name = "Account",
                color = 0,
                avatar = AvatarDto(
                    accountId = accountId,
                    avatarType = AvatarTypeDto.MONOGRAM,
                    avatarMonogram = null,
                    avatarImageUri = null,
                    avatarIconName = null,
                ),
            ),
            incomingServerSettings = ServerSettings(
                type = "imap",
                host = "imap.example.com",
                port = 993,
                connectionSecurity = ConnectionSecurity.SSL_TLS_REQUIRED,
                authenticationType = authenticationType,
                username = "user@example.com",
                password = "password",
                clientCertificateAlias = null,
            ),
            outgoingServerSettings = ServerSettings(
                type = "smtp",
                host = "smtp.example.com",
                port = 465,
                connectionSecurity = ConnectionSecurity.SSL_TLS_REQUIRED,
                authenticationType = authenticationType,
                username = "user@example.com",
                password = "password",
                clientCertificateAlias = null,
            ),
            oAuthState = oAuthState,
            identities = emptyList(),
        )
    }
}

private class FakeValidateServerSettings : ServerValidationDomainContract.UseCase.ValidateServerSettings {
    var result: suspend (ServerSettings) -> ServerSettingsValidationResult = { ServerSettingsValidationResult.Success }

    var authStateStorage: AuthStateStorage? = null
        private set

    override suspend fun execute(settings: ServerSettings): ServerSettingsValidationResult = result(settings)

    override suspend fun execute(
        settings: ServerSettings,
        authStateStorage: AuthStateStorage,
    ): ServerSettingsValidationResult {
        this.authStateStorage = authStateStorage

        return result(settings)
    }
}
