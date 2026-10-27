package app.k9mail.feature.migration.qrcode.transfer

import app.k9mail.feature.migration.qrcode.domain.entity.AccountData
import app.k9mail.feature.migration.qrcode.domain.entity.AccountData.AuthenticationType

/**
 * Attaches the OAuth states of a transfer to the accounts it holds.
 *
 * The states are matched to the accounts by their position. Only accounts that sign in with OAuth get a state, because
 * the state of an account that signs in with a password has no meaning. The states hold refresh tokens, so they must
 * never be logged.
 */
internal fun AccountData.withOAuthStates(oAuthStates: List<String?>): AccountData {
    if (oAuthStates.isEmpty()) {
        return this
    }

    val accounts = accounts.mapIndexed { index, account ->
        val oAuthState = oAuthStates.getOrNull(index)
        if (oAuthState != null && account.usesOAuth()) {
            account.copy(oAuthState = oAuthState)
        } else {
            account.copy(oAuthState = null)
        }
    }

    return copy(accounts = accounts)
}

private fun AccountData.Account.usesOAuth(): Boolean {
    return incomingServer.authenticationType == AuthenticationType.OAuth2 ||
        outgoingServerGroups.any { it.outgoingServer.authenticationType == AuthenticationType.OAuth2 }
}
