package net.thunderbird.core.common.mail

/**
 * What a search on the server looks for. All criteria that are set have to match (they are combined with AND).
 *
 * @param text Words that have to appear, see [splitSearchQuery]. Where they are looked for is [textScope].
 * @param receivedSince Only messages the server received on or after this day.
 * @param receivedBefore Only messages the server received before this day.
 * @param receivedWithinDays Only messages received in the last n days. Needs the server capability WITHIN; without it
 *   the search uses [receivedSince] computed by the caller instead.
 * @param hasAttachment Only messages that look like they have attachments (multipart/mixed). This is a rough check
 *   on the server; it can find messages without a real attachment.
 */
data class ServerSearchCriteria(
    val text: String? = null,
    val textScope: TextScope = TextScope.HEADERS,
    val from: String? = null,
    val to: String? = null,
    val subject: String? = null,
    val receivedSince: SearchDate? = null,
    val receivedBefore: SearchDate? = null,
    val receivedWithinDays: Int? = null,
    val largerThanBytes: Long? = null,
    val smallerThanBytes: Long? = null,
    val hasAttachment: Boolean = false,
    val requiredFlags: Set<Flag> = emptySet(),
    val forbiddenFlags: Set<Flag> = emptySet(),
) {
    enum class TextScope {
        /** Subject, From, To, Cc and Bcc. */
        HEADERS,

        /** The text of the message, without its headers. */
        BODY,

        /** Headers and text of the message (IMAP TEXT). */
        EVERYWHERE,
    }
}

/** A calendar day, month 1-12. */
data class SearchDate(val year: Int, val month: Int, val day: Int)
