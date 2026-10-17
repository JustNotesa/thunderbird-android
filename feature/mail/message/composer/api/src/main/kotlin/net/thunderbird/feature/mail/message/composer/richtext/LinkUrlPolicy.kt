package net.thunderbird.feature.mail.message.composer.richtext

/**
 * Decides which addresses the composer accepts as the target of a link, and how they are written.
 *
 * A link in a message is read by other programs and can be followed without the user seeing where it goes. So the
 * composer writes only `http`, `https` and `mailto:` - what looks like a link must never turn into a `javascript:`
 * script, a `data:` document, a `file:` on the machine of the recipient, or a `content:`/`intent:` Android URI
 * that asks another app to do something. Everything else is refused here, so it reaches neither a draft nor a sent
 * message; the same rule holds for an address the user types and for one read from a draft or pasted.
 */
object LinkUrlPolicy {

    /**
     * The address [input] stands for, or `null` if the composer refuses it. An address without a scheme gets one, so
     * the link is absolute in the message as it is in the editor.
     */
    fun normalize(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() || it.isISOControl() }) return null

        return when (val scheme = SCHEME_PATTERN.find(trimmed)) {
            null -> withoutScheme(trimmed)
            else -> withScheme(trimmed, scheme)
        }
    }

    /**
     * An address with a scheme: only `http`, `https` and `mailto:` are accepted, written with the scheme in lower
     * case.
     */
    private fun withScheme(input: String, scheme: MatchResult): String? {
        val name = scheme.groupValues[1].lowercase()
        val target = input.substring(scheme.value.length)

        return when (name) {
            "http", "https" -> "$name:$target".takeIf { target.hasHost() }
            "mailto" -> "$name:$target".takeIf { target.contains('@') }
            else -> null
        }
    }

    /**
     * An address without a scheme: something that looks like an address becomes `mailto:`, something that looks
     * like a host becomes `https:`.
     */
    private fun withoutScheme(input: String): String? {
        return when {
            ADDRESS_PATTERN.matches(input) -> "mailto:$input"
            input.substringBefore('/').contains('.') -> "https://$input"
            else -> null
        }
    }

    private fun String.hasHost(): Boolean {
        if (!startsWith("//")) return false
        val host = removePrefix("//").substringBefore('/').substringBefore('?').substringBefore('#')
        return host.isNotEmpty()
    }

    private val SCHEME_PATTERN = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):")
    private val ADDRESS_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
}
