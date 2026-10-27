package app.k9mail.feature.migration.qrcode.transfer

import com.fsck.k9.mail.ServerSettings

/**
 * Account of an account transfer, in the shape needed to write the Thunderbird desktop export payload.
 *
 * @param accountName Name of the account on the sending device. `null` when it was never set.
 * @param identities Addresses the account sends from. Attached to the outgoing server by the desktop format.
 * @param oAuthState Sign-in state of an OAuth account. Secret, `null` when the account doesn't use OAuth.
 */
internal data class TransferAccount(
    val accountName: String?,
    val incoming: ServerSettings,
    val outgoing: ServerSettings,
    val identities: List<TransferIdentity>,
    val oAuthState: String? = null,
)

internal data class TransferIdentity(
    val emailAddress: String,
    val displayName: String,
)
