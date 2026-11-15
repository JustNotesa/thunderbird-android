package app.k9mail.feature.migration.qrcode.transfer

import app.k9mail.feature.migration.qrcode.domain.entity.AccountData
import app.k9mail.feature.migration.qrcode.domain.entity.AccountData.AuthenticationType

/**
 * Attaches the OAuth states of a transfer to the accounts it holds.
 *
 * The states are matched to the accounts by their position. Only accounts that sign in with OAuth get a state, because
 * the state of an account that signs in with a password has no meaning. The states hold refresh tokens, so they must
 * never be logged.
 *
 * The accounts that signed in with the OAuth client of Thunderbird desktop don't have a state of their own. Their state
 * is created from the sign-in of the desktop client, so they keep their sign-in.
 */
internal fun AccountData.withOAuthStates(
    oAuthStates: List<String?>,
    desktopOAuthGrants: List<DesktopOAuthGrant?> = emptyList(),
): AccountData {
    if (oAuthStates.isEmpty() && desktopOAuthGrants.isEmpty()) {
        return this
    }

    val accounts = accounts.mapIndexed { index, account ->
        val oAuthState = oAuthStateFor(
            index = index,
            account = account,
            oAuthStates = oAuthStates,
            desktopOAuthGrants = desktopOAuthGrants,
        )

        account.copy(oAuthState = oAuthState)
    }

    return copy(accounts = accounts)
}

private fun oAuthStateFor(
    index: Int,
    account: AccountData.Account,
    oAuthStates: List<String?>,
    desktopOAuthGrants: List<DesktopOAuthGrant?>,
): String? {
    if (!account.usesOAuth()) {
        return null
    }

    val oAuthState = oAuthStates.getOrNull(index)
    return oAuthState ?: desktopOAuthGrants.getOrNull(index)?.toAuthStateJson()
}

private fun AccountData.Account.usesOAuth(): Boolean {
    return incomingServer.authenticationType == AuthenticationType.OAuth2 ||
        outgoingServerGroups.any { it.outgoingServer.authenticationType == AuthenticationType.OAuth2 }
}
