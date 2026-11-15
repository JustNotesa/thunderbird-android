package com.fsck.k9.ui.messagelist.search

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.fsck.k9.FakeLegacyAccount
import com.fsck.k9.mail.Address
import com.fsck.k9.ui.messagelist.MessageListItem
import java.util.Calendar
import java.util.TimeZone
import net.thunderbird.core.common.mail.Flag
import net.thunderbird.core.common.mail.SearchDate
import net.thunderbird.core.common.mail.ServerSearchCriteria
import org.junit.Test

class SearchFiltersMappingTest {
    private val timeZone = TimeZone.getTimeZone("UTC")

    private val today: Calendar = Calendar.getInstance(timeZone).apply {
        clear()
        set(2026, Calendar.OCTOBER, 27)
    }

    private val nowMillis = dateMillis(Calendar.OCTOBER, 27, 12)

    @Test
    fun `without filters the search should only look for the text`() {
        // Arrange
        val testSubject = SearchFilters()

        // Act
        val criteria = testSubject.toServerSearchCriteria("query", today)

        // Assert
        assertThat(criteria.text).isEqualTo("query")
        assertThat(criteria.textScope).isEqualTo(ServerSearchCriteria.TextScope.HEADERS)
        assertThat(criteria.from).isNull()
        assertThat(criteria.to).isNull()
        assertThat(criteria.subject).isNull()
        assertThat(criteria.receivedSince).isNull()
        assertThat(criteria.receivedBefore).isNull()
        assertThat(criteria.largerThanBytes).isNull()
        assertThat(criteria.hasAttachment).isFalse()
        assertThat(criteria.requiredFlags).isEqualTo(emptySet())
        assertThat(criteria.forbiddenFlags).isEqualTo(setOf(Flag.DELETED))
    }

    @Test
    fun `the text scope should be passed on to the server`() {
        // Arrange
        val testSubject = SearchFilters(textScope = SearchFilters.TextScope.BODY)

        // Act
        val criteria = testSubject.toServerSearchCriteria("query", today)

        // Assert
        assertThat(criteria.textScope).isEqualTo(ServerSearchCriteria.TextScope.BODY)
    }

    @Test
    fun `the text scope everywhere should be passed on to the server`() {
        // Arrange
        val testSubject = SearchFilters(textScope = SearchFilters.TextScope.EVERYWHERE)

        // Act
        val criteria = testSubject.toServerSearchCriteria("query", today)

        // Assert
        assertThat(criteria.textScope).isEqualTo(ServerSearchCriteria.TextScope.EVERYWHERE)
    }

    @Test
    fun `the address filters should be passed on to the server`() {
        // Arrange
        val testSubject = SearchFilters(from = "alice@example.com", to = "bob@example.com", subject = "invoice")

        // Act
        val criteria = testSubject.toServerSearchCriteria("query", today)

        // Assert
        assertThat(criteria.from).isEqualTo("alice@example.com")
        assertThat(criteria.to).isEqualTo("bob@example.com")
        assertThat(criteria.subject).isEqualTo("invoice")
    }

    @Test
    fun `the size and attachment filters should be passed on to the server`() {
        // Arrange
        val testSubject = SearchFilters(hasAttachment = true, largerThanBytes = 1024L)

        // Act
        val criteria = testSubject.toServerSearchCriteria("query", today)

        // Assert
        assertThat(criteria.hasAttachment).isTrue()
        assertThat(criteria.largerThanBytes).isEqualTo(1024L)
    }

    @Test
    fun `the period of today should limit the search to today`() {
        // Arrange
        val testSubject = SearchFilters(period = SearchPeriod.Today)

        // Act
        val criteria = testSubject.toServerSearchCriteria("query", today)

        // Assert
        assertThat(criteria.receivedSince).isEqualTo(SearchDate(2026, 10, 27))
        assertThat(criteria.receivedBefore).isNull()
    }

    @Test
    fun `the period of the last 7 days should limit the search to that period`() {
        // Arrange
        val testSubject = SearchFilters(period = SearchPeriod.Last7Days)

        // Act
        val criteria = testSubject.toServerSearchCriteria("query", today)

        // Assert
        assertThat(criteria.receivedSince).isEqualTo(SearchDate(2026, 10, 21))
        assertThat(criteria.receivedBefore).isNull()
    }

    @Test
    fun `the period of the last 30 days should limit the search to that period`() {
        // Arrange
        val testSubject = SearchFilters(period = SearchPeriod.Last30Days)

        // Act
        val criteria = testSubject.toServerSearchCriteria("query", today)

        // Assert
        assertThat(criteria.receivedSince).isEqualTo(SearchDate(2026, 9, 28))
        assertThat(criteria.receivedBefore).isNull()
    }

    @Test
    fun `the period of this year should limit the search to this year`() {
        // Arrange
        val testSubject = SearchFilters(period = SearchPeriod.ThisYear)

        // Act
        val criteria = testSubject.toServerSearchCriteria("query", today)

        // Assert
        assertThat(criteria.receivedSince).isEqualTo(SearchDate(2026, 1, 1))
        assertThat(criteria.receivedBefore).isNull()
    }

    @Test
    fun `a custom period should limit the search to that period`() {
        // Arrange
        val startMillis = dateMillis(Calendar.OCTOBER, 1)
        val endMillis = dateMillis(Calendar.NOVEMBER, 1)
        val testSubject = SearchFilters(period = SearchPeriod.Custom(startMillis, endMillis))

        // Act
        val criteria = testSubject.toServerSearchCriteria("query", today)

        // Assert
        assertThat(criteria.receivedSince).isEqualTo(SearchDate(2026, 10, 1))
        assertThat(criteria.receivedBefore).isEqualTo(SearchDate(2026, 11, 1))
    }

    @Test
    fun `unread messages should be searched for`() {
        // Arrange
        val testSubject = SearchFilters(isUnreadOnly = true)

        // Act
        val criteria = testSubject.toServerSearchCriteria("query", today)

        // Assert
        assertThat(criteria.forbiddenFlags).isEqualTo(setOf(Flag.DELETED, Flag.SEEN))
    }

    @Test
    fun `starred messages should be searched for`() {
        // Arrange
        val testSubject = SearchFilters(isStarredOnly = true)

        // Act
        val criteria = testSubject.toServerSearchCriteria("query", today)

        // Assert
        assertThat(criteria.requiredFlags).isEqualTo(setOf(Flag.FLAGGED))
    }

    @Test
    fun `answered messages should be searched for`() {
        // Arrange
        val testSubject = SearchFilters(isAnsweredOnly = true)

        // Act
        val criteria = testSubject.toServerSearchCriteria("query", today)

        // Assert
        assertThat(criteria.requiredFlags).isEqualTo(setOf(Flag.ANSWERED))
    }

    @Test
    fun `a message with the sender in its address should match the from filter`() {
        // Arrange
        val testSubject = SearchFilters(from = "alice@example.com")
        val item = createMessageListItem(displayAddress = Address.parse("alice@example.com").first())

        // Act
        val result = testSubject.matches(item, nowMillis, timeZone)

        // Assert
        assertThat(result).isTrue()
    }

    @Test
    fun `a message with the sender in its display name should match the from filter`() {
        // Arrange
        val testSubject = SearchFilters(from = "Alice")
        val item = createMessageListItem(displayName = "Alice Example")

        // Act
        val result = testSubject.matches(item, nowMillis, timeZone)

        // Assert
        assertThat(result).isTrue()
    }

    @Test
    fun `a message from another sender should not match the from filter`() {
        // Arrange
        val testSubject = SearchFilters(from = "alice@example.com")
        val item = createMessageListItem(displayAddress = Address.parse("bob@example.com").first())

        // Act
        val result = testSubject.matches(item, nowMillis, timeZone)

        // Assert
        assertThat(result).isFalse()
    }

    @Test
    fun `the from filter should ignore case`() {
        // Arrange
        val testSubject = SearchFilters(from = "ALICE@EXAMPLE.COM")
        val item = createMessageListItem(displayAddress = Address.parse("alice@example.com").first())

        // Act
        val result = testSubject.matches(item, nowMillis, timeZone)

        // Assert
        assertThat(result).isTrue()
    }

    @Test
    fun `a message with the text in its subject should match the subject filter`() {
        // Arrange
        val testSubject = SearchFilters(subject = "invoice")
        val item = createMessageListItem(subject = "Your invoice for October")

        // Act
        val result = testSubject.matches(item, nowMillis, timeZone)

        // Assert
        assertThat(result).isTrue()
    }

    @Test
    fun `the subject filter should ignore case`() {
        // Arrange
        val testSubject = SearchFilters(subject = "INVOICE")
        val item = createMessageListItem(subject = "Your invoice for October")

        // Act
        val result = testSubject.matches(item, nowMillis, timeZone)

        // Assert
        assertThat(result).isTrue()
    }

    @Test
    fun `a message without the text in its subject should not match the subject filter`() {
        // Arrange
        val testSubject = SearchFilters(subject = "invoice")
        val item = createMessageListItem(subject = "Your receipt")

        // Act
        val result = testSubject.matches(item, nowMillis, timeZone)

        // Assert
        assertThat(result).isFalse()
    }

    @Test
    fun `an unread message should match the unread filter`() {
        // Arrange
        val testSubject = SearchFilters(isUnreadOnly = true)
        val item = createMessageListItem(isRead = false)

        // Act
        val result = testSubject.matches(item, nowMillis, timeZone)

        // Assert
        assertThat(result).isTrue()
    }

    @Test
    fun `a read message should not match the unread filter`() {
        // Arrange
        val testSubject = SearchFilters(isUnreadOnly = true)
        val item = createMessageListItem(isRead = true)

        // Act
        val result = testSubject.matches(item, nowMillis, timeZone)

        // Assert
        assertThat(result).isFalse()
    }

    @Test
    fun `a starred message should match the starred filter`() {
        // Arrange
        val testSubject = SearchFilters(isStarredOnly = true)
        val item = createMessageListItem(isStarred = true)

        // Act
        val result = testSubject.matches(item, nowMillis, timeZone)

        // Assert
        assertThat(result).isTrue()
    }

    @Test
    fun `a message that isn't starred should not match the starred filter`() {
        // Arrange
        val testSubject = SearchFilters(isStarredOnly = true)
        val item = createMessageListItem(isStarred = false)

        // Act
        val result = testSubject.matches(item, nowMillis, timeZone)

        // Assert
        assertThat(result).isFalse()
    }

    @Test
    fun `an answered message should match the answered filter`() {
        // Arrange
        val testSubject = SearchFilters(isAnsweredOnly = true)
        val item = createMessageListItem(isAnswered = true)

        // Act
        val result = testSubject.matches(item, nowMillis, timeZone)

        // Assert
        assertThat(result).isTrue()
    }

    @Test
    fun `a message that isn't answered should not match the answered filter`() {
        // Arrange
        val testSubject = SearchFilters(isAnsweredOnly = true)
        val item = createMessageListItem(isAnswered = false)

        // Act
        val result = testSubject.matches(item, nowMillis, timeZone)

        // Assert
        assertThat(result).isFalse()
    }

    @Test
    fun `a message with an attachment should match the attachment filter`() {
        // Arrange
        val testSubject = SearchFilters(hasAttachment = true)
        val item = createMessageListItem(hasAttachments = true)

        // Act
        val result = testSubject.matches(item, nowMillis, timeZone)

        // Assert
        assertThat(result).isTrue()
    }

    @Test
    fun `a message without an attachment should not match the attachment filter`() {
        // Arrange
        val testSubject = SearchFilters(hasAttachment = true)
        val item = createMessageListItem(hasAttachments = false)

        // Act
        val result = testSubject.matches(item, nowMillis, timeZone)

        // Assert
        assertThat(result).isFalse()
    }

    @Test
    fun `a message should match when no filters are set`() {
        // Arrange
        val testSubject = SearchFilters()
        val item = createMessageListItem()

        // Act
        val result = testSubject.matches(item, nowMillis, timeZone)

        // Assert
        assertThat(result).isTrue()
    }

    @Test
    fun `a message received today should match the period of today`() {
        // Arrange
        val testSubject = SearchFilters(period = SearchPeriod.Today)
        val item = createMessageListItem(messageDate = dateMillis(Calendar.OCTOBER, 27, 8))

        // Act
        val result = testSubject.matches(item, nowMillis, timeZone)

        // Assert
        assertThat(result).isTrue()
    }

    @Test
    fun `a message received yesterday should not match the period of today`() {
        // Arrange
        val testSubject = SearchFilters(period = SearchPeriod.Today)
        val item = createMessageListItem(messageDate = dateMillis(Calendar.OCTOBER, 26, 8))

        // Act
        val result = testSubject.matches(item, nowMillis, timeZone)

        // Assert
        assertThat(result).isFalse()
    }

    @Test
    fun `a message received in the last 7 days should match the period of the last 7 days`() {
        // Arrange
        val testSubject = SearchFilters(period = SearchPeriod.Last7Days)
        val item = createMessageListItem(messageDate = dateMillis(Calendar.OCTOBER, 21, 8))

        // Act
        val result = testSubject.matches(item, nowMillis, timeZone)

        // Assert
        assertThat(result).isTrue()
    }

    @Test
    fun `a message received 7 days ago should not match the period of the last 7 days`() {
        // Arrange
        val testSubject = SearchFilters(period = SearchPeriod.Last7Days)
        val item = createMessageListItem(messageDate = dateMillis(Calendar.OCTOBER, 20, 8))

        // Act
        val result = testSubject.matches(item, nowMillis, timeZone)

        // Assert
        assertThat(result).isFalse()
    }

    @Test
    fun `a message received in a custom period should match that period`() {
        // Arrange
        val startMillis = dateMillis(Calendar.OCTOBER, 1)
        val endMillis = dateMillis(Calendar.NOVEMBER, 1)
        val testSubject = SearchFilters(period = SearchPeriod.Custom(startMillis, endMillis))
        val item = createMessageListItem(messageDate = dateMillis(Calendar.OCTOBER, 15, 8))

        // Act
        val result = testSubject.matches(item, nowMillis, timeZone)

        // Assert
        assertThat(result).isTrue()
    }

    @Test
    fun `a message received before a custom period should not match that period`() {
        // Arrange
        val startMillis = dateMillis(Calendar.OCTOBER, 1)
        val endMillis = dateMillis(Calendar.NOVEMBER, 1)
        val testSubject = SearchFilters(period = SearchPeriod.Custom(startMillis, endMillis))
        val item = createMessageListItem(messageDate = dateMillis(Calendar.SEPTEMBER, 30, 8))

        // Act
        val result = testSubject.matches(item, nowMillis, timeZone)

        // Assert
        assertThat(result).isFalse()
    }

    @Test
    fun `the recipient and size filters should not filter the messages in the message list`() {
        // Arrange
        val testSubject = SearchFilters(to = "bob@example.com", largerThanBytes = 1024L)
        val item = createMessageListItem()

        // Act
        val result = testSubject.matches(item, nowMillis, timeZone)

        // Assert
        assertThat(result).isTrue()
    }

    @Test
    fun `without filters no filter should be active`() {
        // Arrange
        val testSubject = SearchFilters()

        // Act
        val activeFilterCount = testSubject.activeFilterCount

        // Assert
        assertThat(activeFilterCount).isEqualTo(0)
        assertThat(testSubject.isEmpty).isTrue()
    }

    @Test
    fun `the text scope should not be counted as an active filter`() {
        // Arrange
        val testSubject = SearchFilters(textScope = SearchFilters.TextScope.EVERYWHERE)

        // Act
        val activeFilterCount = testSubject.activeFilterCount

        // Assert
        assertThat(activeFilterCount).isEqualTo(0)
    }

    @Test
    fun `every filter that is set should be counted as an active filter`() {
        // Arrange
        val testSubject = SearchFilters(
            textScope = SearchFilters.TextScope.BODY,
            from = "alice@example.com",
            to = "bob@example.com",
            subject = "invoice",
            period = SearchPeriod.Today,
            isUnreadOnly = true,
            isStarredOnly = true,
            isAnsweredOnly = true,
            hasAttachment = true,
            largerThanBytes = 1024L,
        )

        // Act
        val activeFilterCount = testSubject.activeFilterCount

        // Assert
        assertThat(activeFilterCount).isEqualTo(9)
        assertThat(testSubject.isEmpty).isFalse()
    }

    private fun dateMillis(month: Int, day: Int, hour: Int = 0): Long = Calendar.getInstance(timeZone).apply {
        clear()
        set(2026, month, day, hour, 0, 0)
    }.timeInMillis

    private fun createMessageListItem(
        subject: String? = "Subject",
        displayName: CharSequence = "Display Name",
        displayAddress: Address? = Address.parse("alice@example.com").first(),
        messageDate: Long = dateMillis(Calendar.OCTOBER, 27, 8),
        isRead: Boolean = false,
        isStarred: Boolean = false,
        isAnswered: Boolean = false,
        hasAttachments: Boolean = false,
    ): MessageListItem {
        return MessageListItem(
            account = FakeLegacyAccount.create(),
            subject = subject,
            threadCount = 1,
            messageDate = messageDate,
            internalDate = messageDate,
            displayName = displayName,
            displayAddress = displayAddress,
            displayMessageDateTime = "12:34",
            previewText = "Preview",
            isMessageEncrypted = false,
            isRead = isRead,
            isStarred = isStarred,
            isAnswered = isAnswered,
            isForwarded = false,
            hasAttachments = hasAttachments,
            uniqueId = 0L,
            folderId = 0L,
            messageUid = "uid",
            databaseId = 0L,
            threadRoot = 0L,
            contactColor = 0,
        )
    }
}
