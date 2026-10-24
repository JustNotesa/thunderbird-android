package app.k9mail.feature.settings.import

import app.k9mail.feature.account.server.validation.domain.ServerValidationDomainContract
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.fsck.k9.mail.AuthType
import com.fsck.k9.mail.ConnectionSecurity
import com.fsck.k9.mail.ServerSettings
import com.fsck.k9.mail.server.ServerSettingsValidationResult
import java.io.IOException
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import net.thunderbird.core.android.account.LegacyAccount
import net.thunderbird.core.android.account.LegacyAccountManager
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

private const val ACCOUNT_UUID = "123e4567-e89b-12d3-a456-426655440000"

class ImportedAccountConnectionCheckerTest {
    private val accountManager = mock<LegacyAccountManager>()
    private val validateServerSettings = FakeValidateServerSettings()
    private val checker = ImportedAccountConnectionChecker(
        accountManager = accountManager,
        validateServerSettings = validateServerSettings,
        coroutineDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `account with working server connection should be connected`() = runTest {
        stubAccount(createAccount())
        validateServerSettings.result = { ServerSettingsValidationResult.Success }

        val result = checker.check(ACCOUNT_UUID)

        assertThat(result).isEqualTo(ConnectionCheckResult.Connected)
    }

    @Test
    fun `account with failing server connection should not be connected`() = runTest {
        stubAccount(createAccount())
        validateServerSettings.result = { ServerSettingsValidationResult.NetworkError(IOException()) }

        val result = checker.check(ACCOUNT_UUID)

        assertThat(result).isEqualTo(ConnectionCheckResult.Failed)
    }

    @Test
    fun `account with failing server validation should not be connected`() = runTest {
        stubAccount(createAccount())
        validateServerSettings.result = { throw IllegalStateException("Validation failed") }

        val result = checker.check(ACCOUNT_UUID)

        assertThat(result).isEqualTo(ConnectionCheckResult.Failed)
    }

    @Test
    fun `account that doesn't exist should not be checked`() = runTest {
        accountManager.stub {
            on { findById(any()) } doReturn null
        }

        val result = checker.check(ACCOUNT_UUID)

        assertThat(result).isEqualTo(ConnectionCheckResult.NotChecked)
    }

    @Test
    fun `account with invalid UUID should not be checked`() = runTest {
        val result = checker.check("not-a-valid-uuid")

        assertThat(result).isEqualTo(ConnectionCheckResult.NotChecked)
    }

    @Test
    fun `connection check that times out should not be connected`() = runTest {
        stubAccount(createAccount())
        validateServerSettings.result = { awaitCancellation() }
        val checker = ImportedAccountConnectionChecker(
            accountManager = accountManager,
            validateServerSettings = validateServerSettings,
            coroutineDispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = checker.check(ACCOUNT_UUID)

        assertThat(result).isEqualTo(ConnectionCheckResult.Failed)
    }

    private fun stubAccount(account: LegacyAccount) {
        accountManager.stub {
            on { findById(any()) } doReturn account
        }
    }

    private fun createAccount(): LegacyAccount {
        return mock<LegacyAccount> {
            on { incomingServerSettings } doReturn ServerSettings(
                type = "imap",
                host = "imap.example.com",
                port = 993,
                connectionSecurity = ConnectionSecurity.SSL_TLS_REQUIRED,
                authenticationType = AuthType.PLAIN,
                username = "user@example.com",
                password = "password",
                clientCertificateAlias = null,
            )
        }
    }
}

private class FakeValidateServerSettings : ServerValidationDomainContract.UseCase.ValidateServerSettings {
    var result: suspend (ServerSettings) -> ServerSettingsValidationResult = { ServerSettingsValidationResult.Success }

    override suspend fun execute(settings: ServerSettings): ServerSettingsValidationResult = result(settings)
}
