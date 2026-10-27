package app.k9mail.feature.migration.qrcode.transfer

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.fsck.k9.mail.AuthType
import com.fsck.k9.mail.ConnectionSecurity
import com.fsck.k9.mail.ServerSettings
import kotlin.test.Test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import net.thunderbird.core.android.account.Identity
import net.thunderbird.core.android.account.LegacyAccount
import net.thunderbird.core.android.account.LegacyAccountManager
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.AccountIdFactory
import net.thunderbird.feature.account.storage.profile.AvatarDto
import net.thunderbird.feature.account.storage.profile.AvatarTypeDto
import net.thunderbird.feature.account.storage.profile.ProfileDto

class LegacyTransferAccountSourceTest {

    @Test
    fun `the sign-in state of an OAuth account is transferred`() = runTest {
        val testSubject = LegacyTransferAccountSource(
            accountManager = FakeLegacyAccountManager(
                listOf(account(incomingAuthType = AuthType.XOAUTH2, oAuthState = OAUTH_STATE)),
            ),
        )

        val result = testSubject.getAccounts()

        assertThat(result.single().oAuthState).isEqualTo(OAUTH_STATE)
    }

    @Test
    fun `the sign-in state of an account that doesn't use OAuth isn't transferred`() = runTest {
        val testSubject = LegacyTransferAccountSource(
            accountManager = FakeLegacyAccountManager(
                listOf(account(incomingAuthType = AuthType.PLAIN, oAuthState = OAUTH_STATE)),
            ),
        )

        val result = testSubject.getAccounts()

        assertThat(result.single().oAuthState).isNull()
    }

    private fun account(incomingAuthType: AuthType, oAuthState: String?): LegacyAccount {
        return LegacyAccount(
            id = ACCOUNT_ID,
            name = "Personal",
            email = "user@domain.example",
            profile = ProfileDto(
                id = ACCOUNT_ID,
                name = "Personal",
                color = 0,
                avatar = AvatarDto(
                    avatarType = AvatarTypeDto.MONOGRAM,
                    avatarMonogram = null,
                    avatarImageUri = null,
                    avatarIconName = null,
                ),
            ),
            incomingServerSettings = ServerSettings(
                type = "imap",
                host = "imap.domain.example",
                port = 993,
                connectionSecurity = ConnectionSecurity.SSL_TLS_REQUIRED,
                authenticationType = incomingAuthType,
                username = USERNAME,
                password = null,
                clientCertificateAlias = null,
            ),
            outgoingServerSettings = ServerSettings(
                type = "smtp",
                host = "smtp.domain.example",
                port = 587,
                connectionSecurity = ConnectionSecurity.STARTTLS_REQUIRED,
                authenticationType = AuthType.PLAIN,
                username = USERNAME,
                password = null,
                clientCertificateAlias = null,
            ),
            oAuthState = oAuthState,
            identities = listOf(
                Identity(email = "user@domain.example", name = "Firstname Lastname"),
            ),
        )
    }

    private companion object {
        private const val USERNAME = "username"
        private const val OAUTH_STATE = """{"tokenType":"Bearer","accessToken":"access-token"}"""
        private val ACCOUNT_ID = AccountIdFactory.of("bc722927-9197-417d-919e-6fd702038de1")
    }
}

/**
 * Account manager that holds the accounts it was created with.
 */
private class FakeLegacyAccountManager(
    private val accounts: List<LegacyAccount>,
) : LegacyAccountManager {
    override fun getAccounts(): List<LegacyAccount> = accounts

    override fun getAccountsFlow(): Flow<List<LegacyAccount>> = flowOf(accounts)

    override fun getAll(): Flow<List<LegacyAccount>> = flowOf(accounts)

    override fun getById(accountId: AccountId): LegacyAccount? = accounts.firstOrNull { it.id == accountId }

    override fun observeById(accountId: AccountId): Flow<LegacyAccount?> = flowOf(getById(accountId))

    override fun moveAccount(account: LegacyAccount, newPosition: Int) = Unit

    override fun saveAccount(account: LegacyAccount) = Unit

    override suspend fun update(account: LegacyAccount) = Unit

    override fun updateSync(account: LegacyAccount) = Unit
}
