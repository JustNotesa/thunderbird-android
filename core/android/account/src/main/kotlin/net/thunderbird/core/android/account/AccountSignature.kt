package net.thunderbird.core.android.account

/**
 * A named signature saved with an account. It is not tied to an [Identity]; the user picks one while
 * composing a message.
 */
data class AccountSignature(
    val id: String,
    val name: String,
    val text: String,
    val isHtml: Boolean = false,
)
