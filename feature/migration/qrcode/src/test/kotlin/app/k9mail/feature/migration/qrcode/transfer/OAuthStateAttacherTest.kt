package app.k9mail.feature.migration.qrcode.transfer

import app.k9mail.feature.migration.qrcode.domain.entity.AccountData
import app.k9mail.feature.migration.qrcode.domain.entity.AccountData.AuthenticationType
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlin.test.Test
import net.thunderbird.core.android.account.DeletePolicy
import net.thunderbird.core.common.mail.toUserEmailAddress
import net.thunderbird.core.common.net.toHostname
import net.thunderbird.core.common.net.toPort
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OAuthStateAttacherTest {
    private val testSubject = AccountData::withOAuthStates

    @Test
    fun `the states are attached to the accounts by their position`() {
        val accountData = accountData(accounts = listOf(oAuthAccount(), oAuthAccount()))

        val result = testSubject(accountData, listOf("first-state", "second-state"), emptyList())

        assertThat(result.accounts.map { it.oAuthState }).isEqualTo(listOf("first-state", "second-state"))
    }

    @Test
    fun `an account that signs in with OAuth gets the state of its position`() {
        val accountData = accountData(accounts = listOf(passwordAccount(), oAuthAccount()))

        val result = testSubject(accountData, listOf("first-state", "second-state"), emptyList())

        assertThat(result.accounts.map { it.oAuthState }).isEqualTo(listOf(null, "second-state"))
    }

    @Test
    fun `an account that signs in with a password gets no state`() {
        val accountData = accountData(accounts = listOf(passwordAccount()))

        val result = testSubject(accountData, listOf("state"), emptyList())

        assertThat(result.accounts.first().oAuthState).isNull()
    }

    @Test
    fun `an account with an OAuth outgoing server gets the state`() {
        val accountData = accountData(accounts = listOf(oAuthOutgoingAccount()))

        val result = testSubject(accountData, listOf("state"), emptyList())

        assertThat(result.accounts.first().oAuthState).isEqualTo("state")
    }

    @Test
    fun `an account without a state of its position gets none`() {
        val accountData = accountData(accounts = listOf(oAuthAccount(), oAuthAccount()))

        val result = testSubject(accountData, listOf("state"), emptyList())

        assertThat(result.accounts.map { it.oAuthState }).isEqualTo(listOf("state", null))
    }

    @Test
    fun `an empty list of states leaves the accounts unchanged`() {
        val accountData = accountData(accounts = listOf(oAuthAccount()))

        val result = testSubject(accountData, emptyList(), emptyList())

        assertThat(result).isEqualTo(accountData)
        assertThat(result.accounts.first().oAuthState).isNull()
    }

    @Test
    fun `an account without a state gets the state of its desktop sign-in`() {
        val accountData = accountData(accounts = listOf(oAuthAccount()))

        val result = testSubject(accountData, emptyList(), listOf(desktopOAuthGrant()))

        assertThat(result.accounts.first().oAuthState).isNotNull()
    }

    @Test
    fun `the state of the transfer is preferred over the desktop sign-in`() {
        val accountData = accountData(accounts = listOf(oAuthAccount()))

        val result = testSubject(accountData, listOf("state"), listOf(desktopOAuthGrant()))

        assertThat(result.accounts.first().oAuthState).isEqualTo("state")
    }

    @Test
    fun `an account that signs in with a password gets no state of a desktop sign-in`() {
        val accountData = accountData(accounts = listOf(passwordAccount()))

        val result = testSubject(accountData, emptyList(), listOf(desktopOAuthGrant()))

        assertThat(result.accounts.first().oAuthState).isNull()
    }

    @Test
    fun `an account whose desktop sign-in is unknown gets no state`() {
        val accountData = accountData(accounts = listOf(oAuthAccount()))
        val grant = desktopOAuthGrant(clientId = "unknown-client-id")

        val result = testSubject(accountData, emptyList(), listOf(grant))

        assertThat(result.accounts.first().oAuthState).isNull()
    }

    private companion object {
        private const val DESKTOP_CLIENT_ID = "406964657835-aq8lmia8j95dhl1a2bvharmfk3t1hgqj.apps.googleusercontent.com"

        private fun desktopOAuthGrant(
            issuer: String = "accounts.google.com",
            clientId: String = DESKTOP_CLIENT_ID,
        ): DesktopOAuthGrant {
            return DesktopOAuthGrant(
                issuer = issuer,
                clientId = clientId,
                scope = "https://mail.google.com/",
                refreshToken = "refresh-token",
            )
        }

        private fun accountData(accounts: List<AccountData.Account>): AccountData {
            return AccountData(
                sequenceNumber = 1,
                sequenceEnd = 1,
                accounts = accounts,
            )
        }

        private fun oAuthAccount(): AccountData.Account {
            return account(
                incomingAuthenticationType = AuthenticationType.OAuth2,
                outgoingAuthenticationType = AuthenticationType.OAuth2,
            )
        }

        private fun oAuthOutgoingAccount(): AccountData.Account {
            return account(
                incomingAuthenticationType = AuthenticationType.PasswordCleartext,
                outgoingAuthenticationType = AuthenticationType.OAuth2,
            )
        }

        private fun passwordAccount(): AccountData.Account {
            return account(
                incomingAuthenticationType = AuthenticationType.PasswordCleartext,
                outgoingAuthenticationType = AuthenticationType.PasswordCleartext,
            )
        }

        private fun account(
            incomingAuthenticationType: AuthenticationType,
            outgoingAuthenticationType: AuthenticationType,
        ): AccountData.Account {
            return AccountData.Account(
                accountName = "Account name",
                deletePolicy = DeletePolicy.ON_DELETE,
                incomingServer = AccountData.IncomingServer(
                    protocol = AccountData.IncomingServerProtocol.Imap,
                    hostname = "imap.domain.example".toHostname(),
                    port = 993.toPort(),
                    connectionSecurity = AccountData.ConnectionSecurity.Tls,
                    authenticationType = incomingAuthenticationType,
                    username = "user@domain.example",
                    password = null,
                ),
                outgoingServerGroups = listOf(
                    AccountData.OutgoingServerGroup(
                        outgoingServer = AccountData.OutgoingServer(
                            protocol = AccountData.OutgoingServerProtocol.Smtp,
                            hostname = "smtp.domain.example".toHostname(),
                            port = 465.toPort(),
                            connectionSecurity = AccountData.ConnectionSecurity.Tls,
                            authenticationType = outgoingAuthenticationType,
                            username = "user@domain.example",
                            password = null,
                        ),
                        identities = listOf(
                            AccountData.Identity(
                                emailAddress = "user@domain.example".toUserEmailAddress(),
                                displayName = "Firstname Lastname",
                            ),
                        ),
                    ),
                ),
            )
        }
    }
}
