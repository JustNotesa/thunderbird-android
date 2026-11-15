package app.k9mail.feature.migration.qrcode.transfer

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlin.test.Test
import net.openid.appauth.AuthState
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DesktopOAuthGrantTest {
    private val testSubject = DesktopOAuthGrant::toAuthStateJson

    @Test
    fun `the sign-in of an unknown client is not turned into a state`() {
        val grant = desktopOAuthGrant(clientId = "unknown-client-id")

        val result = testSubject(grant)

        assertThat(result).isNull()
    }

    @Test
    fun `the sign-in of an unknown issuer is not turned into a state`() {
        val grant = desktopOAuthGrant(issuer = "unknown-issuer")

        val result = testSubject(grant)

        assertThat(result).isNull()
    }

    @Test
    fun `every OAuth client of Thunderbird desktop is known to the app`() {
        DESKTOP_CLIENTS.forEach { client ->
            val result = DesktopOAuthClients.find(issuer = client.issuer, clientId = client.clientId)

            assertThat(result, name = client.issuer).isEqualTo(client)
        }
    }

    @Test
    fun `the sign-in of every OAuth client of Thunderbird desktop is turned into a state`() {
        DESKTOP_CLIENTS.forEach { client ->
            val grant = desktopOAuthGrant(issuer = client.issuer, clientId = client.clientId)

            val result = testSubject(grant)

            val authState = authState(result)
            val clientId = authState.lastAuthorizationResponse?.request?.clientId
            assertThat(clientId, name = client.issuer).isEqualTo(client.clientId)
        }
    }

    @Test
    fun `the state of a desktop sign-in holds the secret of the client`() {
        DESKTOP_CLIENTS.forEach { client ->
            val grant = desktopOAuthGrant(issuer = client.issuer, clientId = client.clientId)

            val result = testSubject(grant)

            val authState = authState(result)
            val clientSecret = clientSecret(authState)
            assertThat(clientSecret, name = client.issuer).isEqualTo(client.clientSecret)
        }
    }

    @Test
    fun `the state of a desktop sign-in holds the refresh token`() {
        val grant = desktopOAuthGrant()

        val result = testSubject(grant)

        val authState = authState(result)
        assertThat(authState.refreshToken).isEqualTo(REFRESH_TOKEN)
    }

    @Test
    fun `the state of a desktop sign-in holds the client of the desktop`() {
        val grant = desktopOAuthGrant()

        val result = testSubject(grant)

        val authState = authState(result)
        assertThat(authState.lastAuthorizationResponse?.request?.clientId).isEqualTo(GOOGLE_CLIENT_ID)
    }

    @Test
    fun `the state of a desktop sign-in holds the scope of the sign-in`() {
        val grant = desktopOAuthGrant()

        val result = testSubject(grant)

        val authState = authState(result)
        assertThat(authState.lastAuthorizationResponse?.request?.scope).isEqualTo(SCOPE)
    }

    @Test
    fun `the state of a Microsoft sign-in holds the scope of the sign-in`() {
        val grant = desktopOAuthGrant(
            issuer = MICROSOFT_ISSUER,
            clientId = MICROSOFT_CLIENT_ID,
            scope = MICROSOFT_SCOPE,
        )

        val result = testSubject(grant)

        val authState = authState(result)
        assertThat(authState.lastAuthorizationResponse?.request?.scope).isEqualTo(MICROSOFT_SCOPE)
    }

    @Test
    fun `the state of a desktop sign-in needs a token refresh`() {
        val grant = desktopOAuthGrant()

        val result = testSubject(grant)

        val authState = authState(result)
        assertThat(authState.needsTokenRefresh).isTrue()
    }

    private fun authState(result: String?): AuthState {
        assertThat(result).isNotNull()
        return AuthState.jsonDeserialize(checkNotNull(result))
    }

    private fun clientSecret(authState: AuthState): String? {
        return authState.lastAuthorizationResponse?.request?.additionalParameters?.get(CLIENT_SECRET_PARAMETER)
    }

    private fun desktopOAuthGrant(
        issuer: String = GOOGLE_ISSUER,
        clientId: String = GOOGLE_CLIENT_ID,
        scope: String = SCOPE,
    ): DesktopOAuthGrant {
        return DesktopOAuthGrant(
            issuer = issuer,
            clientId = clientId,
            scope = scope,
            refreshToken = REFRESH_TOKEN,
        )
    }

    private companion object {
        private const val CLIENT_SECRET_PARAMETER = "tb_client_secret"
        private const val GOOGLE_ISSUER = "accounts.google.com"
        private const val GOOGLE_CLIENT_ID = "406964657835-aq8lmia8j95dhl1a2bvharmfk3t1hgqj.apps.googleusercontent.com"
        private const val GOOGLE_CLIENT_SECRET = "kSmqreRr0qwBWJgbf5Y-PjSU"
        private const val MICROSOFT_ISSUER = "login.microsoftonline.com"
        private const val MICROSOFT_CLIENT_ID = "9e5f94bc-e8a4-4e73-b8be-63364c29d753"
        private const val MICROSOFT_SCOPE =
            "offline_access https://outlook.office.com/IMAP.AccessAsUser.All https://outlook.office.com/SMTP.Send"
        private const val YAHOO_CLIENT_ID =
            "dj0yJmk9WVZUaWRNUUZSQTBNJmQ9WVdrOVNqbHJUMGhtTkU4bWNHbzlNQT09JnM9Y29uc3VtZXJzZWNyZXQmc3Y9MCZ4PTgz"
        private const val AOL_CLIENT_ID =
            "dj0yJmk9MGNoTTQ0SjhIN1dSJmQ9WVdrOVdIVnFkVVp2UkZNbWNHbzlNQT09JnM9Y29uc3VtZXJzZWNyZXQmc3Y9MCZ4PTNm"
        private const val SCOPE = "https://mail.google.com/"
        private const val REFRESH_TOKEN = "refresh-token"

        /**
         * The OAuth clients of Thunderbird desktop, as they are published in its source code.
         */
        private val DESKTOP_CLIENTS = listOf(
            DesktopOAuthClient(
                issuer = GOOGLE_ISSUER,
                clientId = GOOGLE_CLIENT_ID,
                clientSecret = GOOGLE_CLIENT_SECRET,
                authorizationEndpoint = "https://accounts.google.com/o/oauth2/auth",
                tokenEndpoint = "https://www.googleapis.com/oauth2/v3/token",
            ),
            DesktopOAuthClient(
                issuer = "o2.mail.ru",
                clientId = "thunderbird",
                clientSecret = "I0dCAXrcaNFujaaY",
                authorizationEndpoint = "https://o2.mail.ru/login",
                tokenEndpoint = "https://o2.mail.ru/token",
            ),
            DesktopOAuthClient(
                issuer = "oauth.yandex.com",
                clientId = "2a00bba7374047a6ab79666485ffce31",
                clientSecret = "3ded85b4ec574c2187a55dc49d361280",
                authorizationEndpoint = "https://oauth.yandex.com/authorize",
                tokenEndpoint = "https://oauth.yandex.com/token",
            ),
            DesktopOAuthClient(
                issuer = "login.yahoo.com",
                clientId = YAHOO_CLIENT_ID,
                clientSecret = null,
                authorizationEndpoint = "https://api.login.yahoo.com/oauth2/request_auth",
                tokenEndpoint = "https://api.login.yahoo.com/oauth2/get_token",
            ),
            DesktopOAuthClient(
                issuer = "login.aol.com",
                clientId = AOL_CLIENT_ID,
                clientSecret = null,
                authorizationEndpoint = "https://api.login.aol.com/oauth2/request_auth",
                tokenEndpoint = "https://api.login.aol.com/oauth2/get_token",
            ),
            DesktopOAuthClient(
                issuer = MICROSOFT_ISSUER,
                clientId = MICROSOFT_CLIENT_ID,
                clientSecret = null,
                authorizationEndpoint = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize",
                tokenEndpoint = "https://login.microsoftonline.com/common/oauth2/v2.0/token",
            ),
            DesktopOAuthClient(
                issuer = "www.fastmail.com",
                clientId = "35f141ae",
                clientSecret = null,
                authorizationEndpoint = "https://api.fastmail.com/oauth/authorize",
                tokenEndpoint = "https://api.fastmail.com/oauth/refresh",
            ),
            DesktopOAuthClient(
                issuer = "comcast.net",
                clientId = "thunderbird-oauth",
                clientSecret = "fc5d0a314549bb3d059e0cec751fa4bd40a9cc7b",
                authorizationEndpoint = "https://oauth.xfinity.com/oauth/authorize",
                tokenEndpoint = "https://oauth.xfinity.com/oauth/token",
            ),
            DesktopOAuthClient(
                issuer = "auth.tb.pro",
                clientId = "desktop",
                clientSecret = null,
                authorizationEndpoint = "https://auth.tb.pro/realms/tbpro/protocol/openid-connect/auth",
                tokenEndpoint = "https://auth.tb.pro/realms/tbpro/protocol/openid-connect/token",
            ),
        )
    }
}
