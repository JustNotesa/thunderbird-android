package com.fsck.k9.backends

import android.net.Uri
import assertk.assertThat
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlin.test.Test
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ClientSecretPost
import net.openid.appauth.ResponseTypeValues
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RealOAuth2TokenProviderTest {
    private val testSubject = ::clientAuthenticationFor

    @Test
    fun `an account without a client secret needs no client authentication`() {
        val authState = authState(additionalParameters = emptyMap())

        val result = testSubject(authState)

        assertThat(result).isNull()
    }

    @Test
    fun `an account with a client secret needs a client authentication`() {
        val authState = authState(additionalParameters = mapOf(CLIENT_SECRET_PARAMETER to CLIENT_SECRET))

        val result = testSubject(authState)

        assertThat(result).isNotNull().isInstanceOf(ClientSecretPost::class)
    }

    private fun authState(additionalParameters: Map<String, String>): AuthState {
        val serviceConfiguration = AuthorizationServiceConfiguration(
            Uri.parse("https://accounts.google.com/o/oauth2/auth"),
            Uri.parse("https://www.googleapis.com/oauth2/v3/token"),
        )
        val clientId = CLIENT_ID
        val redirectUri = Uri.parse("http://127.0.0.1")
        val request = AuthorizationRequest.Builder(
            serviceConfiguration,
            clientId,
            ResponseTypeValues.CODE,
            redirectUri,
        )
            .setAdditionalParameters(additionalParameters)
            .build()
        val response = AuthorizationResponse.Builder(request)
            .setAuthorizationCode("authorization-code")
            .build()

        return AuthState(response, null)
    }

    private companion object {
        private const val CLIENT_SECRET_PARAMETER = "tb_client_secret"
        private const val CLIENT_SECRET = "kSmqreRr0qwBWJgbf5Y-PjSU"
        private const val CLIENT_ID = "406964657835-aq8lmia8j95dhl1a2bvharmfk3t1hgqj.apps.googleusercontent.com"
    }
}
