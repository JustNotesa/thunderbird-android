package net.thunderbird.feature.account.settings.impl.domain

import net.thunderbird.core.android.account.AccountSignature
import net.thunderbird.core.android.account.LegacyAccount

internal data class AccountSignatures(
    val signatures: List<AccountSignature>,
    val defaultSignatureId: String?,
)

internal fun LegacyAccount.toAccountSignatures() = AccountSignatures(
    signatures = signatures,
    defaultSignatureId = defaultSignature()?.id,
)

/**
 * The signature new messages start with is the one of the account's main identity. Making a saved signature the
 * default copies it there, so a saved signature is the default for as long as that copy still matches it.
 */
internal fun LegacyAccount.defaultSignature(): AccountSignature? {
    if (!signatureUse) return null

    return signatures.firstOrNull { it.text == signature && it.isHtml == signatureIsHtml }
}

internal fun LegacyAccount.withDefaultSignature(signature: AccountSignature): LegacyAccount = copy(
    signatureUse = true,
    signature = signature.text,
    signatureIsHtml = signature.isHtml,
)
