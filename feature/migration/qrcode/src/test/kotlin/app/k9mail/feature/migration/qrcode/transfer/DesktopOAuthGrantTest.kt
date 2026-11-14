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
        assertThat(authState.lastAuthorizationResponse?.request?.clientId).isEqualTo(DESKTOP_CLIENT_ID)
    }

    @Test
    fun `the state of a desktop sign-in holds the scope of the sign-in`() {
        val grant = desktopOAuthGrant()

        val result = testSubject(grant)

        val authState = authState(result)
        assertThat(authState.lastAuthorizationResponse?.request?.scope).isEqualTo(SCOPE)
    }

    @Test
    fun `the state of a desktop sign-in holds the secret of the client`() {
        val grant = desktopOAuthGrant()

        val result = testSubject(grant)

        val authState = authState(result)
        val clientSecret = authState.lastAuthorizationResponse?.request?.additionalParameters?.get("tb_client_secret")
        assertThat(clientSecret).isEqualTo(DESKTOP_CLIENT_SECRET)
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

    private fun desktopOAuthGrant(
        issuer: String = "accounts.google.com",
        clientId: String = DESKTOP_CLIENT_ID,
    ): DesktopOAuthGrant {
        return DesktopOAuthGrant(
            issuer = issuer,
            clientId = clientId,
            scope = SCOPE,
            refreshToken = REFRESH_TOKEN,
        )
    }

    private companion object {
        private const val DESKTOP_CLIENT_ID = "406964657835-aq8lmia8j95dhl1a2bvharmfk3t1hgqj.apps.googleusercontent.com"
        private const val DESKTOP_CLIENT_SECRET = "kSmqreRr0qwBWJgbf5Y-PjSU"
        private const val SCOPE = "https://mail.google.com/"
        private const val REFRESH_TOKEN = "refresh-token"
    }
}
