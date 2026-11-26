package com.fsck.k9.activity.compose.signature

import net.thunderbird.core.android.account.AccountSignature

/**
 * What the signature chip above the signature in a message says.
 *
 * @param id The id of the saved signature, only for [Kind.Saved].
 * @param name The name of the saved signature, only for [Kind.Saved].
 * @param isEdited Whether the signature of this message differs from the saved one it came from.
 * @param isHtml Whether the signature is HTML, which is shown rendered and edited in its own sheet.
 */
data class SignatureChipState(
    val kind: Kind,
    val id: String? = null,
    val name: String? = null,
    val isEdited: Boolean = false,
    val isHtml: Boolean = false,
) {
    enum class Kind {
        /** The account has no saved signature and the message has none: the chip offers to create one. */
        Create,

        /** The default signature of the identity. */
        Default,

        /** A saved signature of the account. */
        Saved,

        /** A signature that is neither the default one nor a saved one, e.g. one from a draft. */
        Custom,

        /** The message goes without signature although there are some to choose from. */
        None,
    }
}

/**
 * What the signature of the message currently is, as far as the chip needs to know.
 *
 * @param inUse Whether the message has a signature at all.
 * @param text The signature as it is in the message (for an HTML signature its HTML).
 * @param appliedSignatureId The saved signature the user chose for this message, if any.
 * @param defaultText The default signature of the identity, `null` if the identity has none.
 */
data class MessageSignature(
    val inUse: Boolean,
    val text: String,
    val isHtml: Boolean,
    val appliedSignatureId: String?,
    val defaultText: String?,
    val defaultIsHtml: Boolean,
)

object SignatureChip {
    @JvmStatic
    fun stateOf(signature: MessageSignature, saved: List<AccountSignature>): SignatureChipState {
        return if (signature.inUse) stateOfSignatureInUse(signature, saved) else stateWithoutSignature(saved)
    }

    private fun stateWithoutSignature(saved: List<AccountSignature>): SignatureChipState {
        return SignatureChipState(if (saved.isEmpty()) SignatureChipState.Kind.Create else SignatureChipState.Kind.None)
    }

    private fun stateOfSignatureInUse(signature: MessageSignature, saved: List<AccountSignature>): SignatureChipState {
        val html = signature.isHtml
        val chosen = saved.firstOrNull { it.id == signature.appliedSignatureId }
        val matching = saved.firstOrNull { sameSignature(it.text, it.isHtml, signature) }
        val isDefault = signature.defaultText != null &&
            sameSignature(signature.defaultText, signature.defaultIsHtml, signature)

        return when {
            chosen != null -> SignatureChipState(
                kind = SignatureChipState.Kind.Saved,
                id = chosen.id,
                name = chosen.name,
                isEdited = !sameSignature(chosen.text, chosen.isHtml, signature),
                isHtml = html,
            )

            isDefault -> SignatureChipState(SignatureChipState.Kind.Default, isHtml = html)

            matching != null -> SignatureChipState(
                kind = SignatureChipState.Kind.Saved,
                id = matching.id,
                name = matching.name,
                isHtml = html,
            )

            else -> SignatureChipState(SignatureChipState.Kind.Custom, isHtml = html)
        }
    }

    private fun sameSignature(text: String, isHtml: Boolean, other: MessageSignature): Boolean {
        return isHtml == other.isHtml && normalized(text) == normalized(other.text)
    }

    private fun normalized(text: String) = text.replace("\r\n", "\n").trim()
}
