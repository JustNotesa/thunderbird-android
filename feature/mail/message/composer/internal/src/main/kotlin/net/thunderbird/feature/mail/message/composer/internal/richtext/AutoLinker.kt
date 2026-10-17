package net.thunderbird.feature.mail.message.composer.internal.richtext

import net.thunderbird.feature.mail.message.composer.richtext.LinkRange
import net.thunderbird.feature.mail.message.composer.richtext.LinkUrlPolicy

/**
 * Finds the web addresses that stand as plain text, so that a link the user sees as text while writing is a link in
 * the message. Only `http`, `https` and `www.` are looked for: an address the user did not type as a link is not
 * turned into one, and an address [LinkUrlPolicy] refuses never reaches the message.
 */
internal object AutoLinker {

    /**
     * The links [text] holds as plain text, in the order they stand in it.
     */
    fun findLinks(text: String): List<LinkRange> {
        val links = mutableListOf<LinkRange>()

        for (match in URL_PATTERN.findAll(text)) {
            val address = addressOf(match.value)
            val url = address?.let(LinkUrlPolicy::normalize) ?: continue
            links.add(LinkRange(start = match.range.first, end = match.range.first + address.length, url = url))
        }

        return links
    }

    /**
     * The address at the start of [match]. A tag ends it, and punctuation that only closes the sentence it stands in
     * belongs to the sentence; a bracket that the address itself opened stays with the address.
     */
    private fun addressOf(match: String): String? {
        val end = match.indexOfFirst { it == '<' || it == '>' }.let { if (it < 0) match.length else it }
        var address = match.substring(0, end)

        while (address.isNotEmpty() && address.last() in TRAILING_CHARACTERS) {
            if (address.last() == ')' && address.count { it == '(' } >= address.count { it == ')' }) break
            address = address.dropLast(1)
        }

        return address.takeIf { it.isNotEmpty() }
    }

    private val URL_PATTERN = Regex("""(?:https?://|www\.)\S+""")
    private const val TRAILING_CHARACTERS = ".,;:!?)]}'\""
}
