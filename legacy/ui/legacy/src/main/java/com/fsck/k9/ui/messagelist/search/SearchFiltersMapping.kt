package com.fsck.k9.ui.messagelist.search

import com.fsck.k9.ui.messagelist.MessageListItem
import java.util.Calendar
import java.util.TimeZone
import net.thunderbird.core.common.mail.Flag
import net.thunderbird.core.common.mail.SearchDate
import net.thunderbird.core.common.mail.ServerSearchCriteria

private const val DAYS_IN_LAST_7_DAYS = 7
private const val DAYS_IN_LAST_30_DAYS = 30

/**
 * Convert these filters to the criteria of a search on the server.
 *
 * Messages that are deleted on the server are never part of the search results.
 *
 * @param query The text the user entered in the search field.
 * @param today The current day, in the time zone of the device.
 */
fun SearchFilters.toServerSearchCriteria(query: String?, today: Calendar): ServerSearchCriteria {
    val bounds = period?.toBounds(today)
    val receivedSince = bounds?.first?.toSearchDate()
    val receivedBefore = if (period is SearchPeriod.Custom) bounds?.second?.toSearchDate() else null

    return ServerSearchCriteria(
        text = query,
        textScope = textScope.toServerTextScope(),
        from = from,
        to = to,
        subject = subject,
        receivedSince = receivedSince,
        receivedBefore = receivedBefore,
        largerThanBytes = largerThanBytes,
        hasAttachment = hasAttachment,
        requiredFlags = buildSet {
            if (isStarredOnly) add(Flag.FLAGGED)
            if (isAnsweredOnly) add(Flag.ANSWERED)
        },
        forbiddenFlags = buildSet {
            add(Flag.DELETED)
            if (isUnreadOnly) add(Flag.SEEN)
        },
    )
}

/**
 * Check whether the message in [item] matches these filters.
 *
 * The filters [to] and [largerThanBytes] aren't checked. A message in the message list doesn't know its recipients
 * and the size of its attachments. The server applies these filters when it searches.
 *
 * @param nowMillis The current time.
 * @param timeZone The time zone of the device.
 */
fun SearchFilters.matches(item: MessageListItem, nowMillis: Long, timeZone: TimeZone): Boolean {
    val matchesPeriod = period?.let { searchPeriod ->
        val (start, end) = searchPeriod.toBounds(timeZone.toStartOfDay(nowMillis))
        item.messageDate in start.timeInMillis until end.timeInMillis
    } ?: true

    return matchesFrom(item) &&
        matchesSubject(item) &&
        matchesFlags(item) &&
        matchesPeriod &&
        (!hasAttachment || item.hasAttachments)
}

private fun SearchFilters.matchesFrom(item: MessageListItem): Boolean {
    val filter = from ?: return true

    val address = item.displayAddress?.address
    return address?.contains(filter, ignoreCase = true) == true || item.displayName.contains(filter, ignoreCase = true)
}

private fun SearchFilters.matchesSubject(item: MessageListItem): Boolean {
    val filter = subject ?: return true

    return item.subject?.contains(filter, ignoreCase = true) == true
}

private fun SearchFilters.matchesFlags(item: MessageListItem): Boolean {
    return (!isUnreadOnly || !item.isRead) &&
        (!isStarredOnly || item.isStarred) &&
        (!isAnsweredOnly || item.isAnswered)
}

/**
 * The first day of this period and the day after its last day. Both are the start of a day.
 */
private fun SearchPeriod.toBounds(today: Calendar): Pair<Calendar, Calendar> {
    val zone = today.timeZone
    val endOfToday = zone.toStartOfDay(today.timeInMillis, 1)

    return when (this) {
        SearchPeriod.Today -> zone.toStartOfDay(today.timeInMillis) to endOfToday
        SearchPeriod.Last7Days -> zone.toStartOfDay(today.timeInMillis, 1 - DAYS_IN_LAST_7_DAYS) to endOfToday
        SearchPeriod.Last30Days -> zone.toStartOfDay(today.timeInMillis, 1 - DAYS_IN_LAST_30_DAYS) to endOfToday
        SearchPeriod.ThisYear -> zone.toStartOfYear(today.timeInMillis) to endOfToday
        is SearchPeriod.Custom -> zone.toStartOfDay(startMillis) to zone.toStartOfDay(endMillis)
    }
}

/**
 * The start of the day [millis] is in, moved by [days].
 */
private fun TimeZone.toStartOfDay(millis: Long, days: Int = 0): Calendar = Calendar.getInstance(this).apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
    add(Calendar.DAY_OF_MONTH, days)
}

/**
 * The start of the year the day [millis] is in.
 */
private fun TimeZone.toStartOfYear(millis: Long): Calendar = toStartOfDay(millis).apply {
    set(Calendar.DAY_OF_MONTH, 1)
    set(Calendar.MONTH, Calendar.JANUARY)
}

private fun Calendar.toSearchDate(): SearchDate = SearchDate(
    year = get(Calendar.YEAR),
    month = get(Calendar.MONTH) + 1,
    day = get(Calendar.DAY_OF_MONTH),
)

private fun SearchFilters.TextScope.toServerTextScope(): ServerSearchCriteria.TextScope = when (this) {
    SearchFilters.TextScope.HEADERS -> ServerSearchCriteria.TextScope.HEADERS
    SearchFilters.TextScope.BODY -> ServerSearchCriteria.TextScope.BODY
    SearchFilters.TextScope.EVERYWHERE -> ServerSearchCriteria.TextScope.EVERYWHERE
}
