package com.fsck.k9.message

import java.net.URLDecoder

private val CONTENT_ID_REFERENCE = Regex("""cid:([^"'\s>)]+)""", RegexOption.IGNORE_CASE)

/**
 * Chooses the inline attachments a message needs: an image the user deleted from the text must not be sent.
 */
object InlineAttachmentFilter {
    /**
     * The entries of [inlineAttachments] (keyed by Content-ID) that some of the [html] texts refer to as `cid:ID`.
     */
    @JvmStatic
    fun onlyReferenced(
        inlineAttachments: Map<String, Attachment>,
        vararg html: String?,
    ): Map<String, Attachment> {
        val referenced = html.filterNotNull().flatMap { text -> referencedIds(text) }.toSet()

        return inlineAttachments.filterKeys { contentId -> contentId in referenced }
    }

    // Some senders percent-encode the ID in the HTML, so both spellings count.
    private fun referencedIds(html: String): List<String> {
        return CONTENT_ID_REFERENCE.findAll(html).flatMap { match ->
            val id = match.groupValues[1]
            sequenceOf(id, decode(id))
        }.toList()
    }

    private fun decode(id: String): String {
        return try {
            URLDecoder.decode(id.replace("+", "%2B"), "UTF-8")
        } catch (@Suppress("SwallowedException") e: IllegalArgumentException) {
            id
        }
    }
}
