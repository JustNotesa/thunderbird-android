package app.k9mail.feature.migration.qrcode.transfer

import android.net.Uri
import com.squareup.moshi.JsonReader
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.GrantTypeValues
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse

private const val KEY_ISSUER = "issuer"
private const val KEY_CLIENT_ID = "clientId"
private const val KEY_SCOPE = "scope"
private const val KEY_REFRESH_TOKEN = "refreshToken"

private const val CLIENT_SECRET_PARAMETER = "tb_client_secret"
private const val DESKTOP_REDIRECT_URI = "http://127.0.0.1"
private const val DESKTOP_AUTHORIZATION_CODE = "desktop-transfer"
private const val EXPIRED_ACCESS_TOKEN_TIME = 0L

private const val YAHOO_CLIENT_ID =
    "dj0yJmk9WVZUaWRNUUZSQTBNJmQ9WVdrOVNqbHJUMGhtTkU4bWNHbzlNQT09JnM9Y29uc3VtZXJzZWNyZXQmc3Y9MCZ4PTgz"
private const val AOL_CLIENT_ID =
    "dj0yJmk9MGNoTTQ0SjhIN1dSJmQ9WVdrOVdIVnFkVVp2UkZNbWNHbzlNQT09JnM9Y29uc3VtZXJzZWNyZXQmc3Y9MCZ4PTNm"

private val REQUIRED_FIELDS = listOf(KEY_ISSUER, KEY_CLIENT_ID, KEY_SCOPE, KEY_REFRESH_TOKEN)

/**
 * The sign-in of an account that was authorized with the OAuth client of Thunderbird desktop.
 *
 * The grant holds the refresh token of the account, so it must never be logged.
 */
internal data class DesktopOAuthGrant(
    val issuer: String,
    val clientId: String,
    val scope: String,
    val refreshToken: String,
)

/**
 * The OAuth client of a desktop application, as it is published in the source code of the application.
 *
 * The secret is `null` for the public clients. Their token is refreshed without client authentication.
 */
internal data class DesktopOAuthClient(
    val issuer: String,
    val clientId: String,
    val clientSecret: String?,
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
)

/**
 * The OAuth clients of Thunderbird desktop that are known to this app.
 *
 * A refresh token can only be refreshed with the client it was issued to. The accounts that signed in on the desktop
 * therefore have to be refreshed with the desktop client, which needs the client id and the secret of that client.
 * The clients without a secret are public clients, their token is refreshed without client authentication.
 */
internal object DesktopOAuthClients {
    private val clients = listOf(
        DesktopOAuthClient(
            issuer = "accounts.google.com",
            clientId = "406964657835-aq8lmia8j95dhl1a2bvharmfk3t1hgqj.apps.googleusercontent.com",
            clientSecret = "kSmqreRr0qwBWJgbf5Y-PjSU",
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
            issuer = "login.microsoftonline.com",
            clientId = "9e5f94bc-e8a4-4e73-b8be-63364c29d753",
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

    /**
     * The desktop client that issued the sign-in of an account.
     *
     * @return The client, or `null` when the sign-in wasn't issued by a known desktop client.
     */
    fun find(issuer: String, clientId: String): DesktopOAuthClient? {
        return clients.firstOrNull { it.issuer == issuer && it.clientId == clientId }
    }
}

/**
 * Creates the sign-in state of an account that signed in with the OAuth client of Thunderbird desktop.
 *
 * The state holds the desktop client, so the refresh token of the account can be refreshed with the client it was
 * issued to. It also holds the secret of the desktop client, which is needed to refresh the token. The public clients
 * have no secret, their token is refreshed without client authentication.
 *
 * @return The serialized sign-in state, or `null` when the sign-in wasn't issued by a known desktop client.
 */
internal fun DesktopOAuthGrant.toAuthStateJson(): String? {
    val client = DesktopOAuthClients.find(issuer = issuer, clientId = clientId) ?: return null

    val request = createAuthorizationRequest(client = client, scope = scope)
    val response = createAuthorizationResponse(request = request)
    val tokenResponse = createTokenResponse(client = client, refreshToken = refreshToken)

    val authState = AuthState(response, tokenResponse, null)
    // The account doesn't have an access token yet, so its token has to be refreshed before it is used.
    authState.needsTokenRefresh = true

    return authState.jsonSerializeString()
}

/**
 * Reads the sign-ins of the accounts that signed in with the OAuth client of Thunderbird desktop.
 *
 * The sign-ins are aligned by index with the accounts of the payload, `null` for the accounts that don't have one. A
 * sign-in that isn't a valid grant is treated as if it wasn't there.
 */
internal fun readDesktopOAuthGrants(jsonReader: JsonReader): List<DesktopOAuthGrant?> {
    if (jsonReader.peek() != JsonReader.Token.BEGIN_ARRAY) {
        jsonReader.skipValue()
        return emptyList()
    }

    val grants = mutableListOf<DesktopOAuthGrant?>()
    jsonReader.beginArray()
    while (jsonReader.hasNext()) {
        grants.add(readDesktopOAuthGrant(jsonReader))
    }
    jsonReader.endArray()

    return grants
}

private fun createAuthorizationRequest(client: DesktopOAuthClient, scope: String): AuthorizationRequest {
    val serviceConfiguration = createServiceConfiguration(client)
    val clientId = client.clientId
    val redirectUri = Uri.parse(DESKTOP_REDIRECT_URI)
    val clientSecret = client.clientSecret
    // The public clients have no secret, their token is refreshed without client authentication.
    val additionalParameters = if (clientSecret != null) {
        mapOf(CLIENT_SECRET_PARAMETER to clientSecret)
    } else {
        emptyMap()
    }

    return AuthorizationRequest.Builder(
        serviceConfiguration,
        clientId,
        ResponseTypeValues.CODE,
        redirectUri,
    )
        .setScope(scope)
        .setAdditionalParameters(additionalParameters)
        .build()
}

private fun createAuthorizationResponse(request: AuthorizationRequest): AuthorizationResponse {
    return AuthorizationResponse.Builder(request)
        .setAuthorizationCode(DESKTOP_AUTHORIZATION_CODE)
        .build()
}

private fun createTokenResponse(client: DesktopOAuthClient, refreshToken: String): TokenResponse {
    val tokenRequest = TokenRequest.Builder(createServiceConfiguration(client), client.clientId)
        .setGrantType(GrantTypeValues.REFRESH_TOKEN)
        .setRefreshToken(refreshToken)
        .build()

    return TokenResponse.Builder(tokenRequest)
        .setRefreshToken(refreshToken)
        // The account doesn't have an access token, the expired time makes the state refresh its token.
        .setAccessTokenExpirationTime(EXPIRED_ACCESS_TOKEN_TIME)
        .build()
}

private fun createServiceConfiguration(client: DesktopOAuthClient): AuthorizationServiceConfiguration {
    return AuthorizationServiceConfiguration(
        Uri.parse(client.authorizationEndpoint),
        Uri.parse(client.tokenEndpoint),
    )
}

private fun readDesktopOAuthGrant(jsonReader: JsonReader): DesktopOAuthGrant? {
    if (jsonReader.peek() != JsonReader.Token.BEGIN_OBJECT) {
        jsonReader.skipValue()
        return null
    }

    val fields = mutableMapOf<String, String>()
    jsonReader.beginObject()
    while (jsonReader.hasNext()) {
        val name = jsonReader.nextName()
        val value = readString(jsonReader)
        if (value != null) {
            fields[name] = value
        }
    }
    jsonReader.endObject()

    return createGrant(fields)
}

private fun createGrant(fields: Map<String, String>): DesktopOAuthGrant? {
    if (!fields.keys.containsAll(REQUIRED_FIELDS)) {
        return null
    }

    return DesktopOAuthGrant(
        issuer = fields.getValue(KEY_ISSUER),
        clientId = fields.getValue(KEY_CLIENT_ID),
        scope = fields.getValue(KEY_SCOPE),
        refreshToken = fields.getValue(KEY_REFRESH_TOKEN),
    )
}

private fun readString(jsonReader: JsonReader): String? {
    return if (jsonReader.peek() == JsonReader.Token.STRING) {
        jsonReader.nextString()
    } else {
        jsonReader.skipValue()
        null
    }
}
