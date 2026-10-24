package net.thunderbird.feature.search.legacy

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import kotlin.test.assertFailsWith
import net.thunderbird.feature.account.UnifiedAccountId
import net.thunderbird.feature.mail.folder.FolderType
import net.thunderbird.feature.search.legacy.api.MessageSearchField
import net.thunderbird.feature.search.legacy.api.SearchAttribute
import net.thunderbird.feature.search.legacy.api.SearchCondition
import org.junit.Test

class SearchAccountTest {

    @Test
    fun `createUnifiedSearch() with INBOX should create a unified inbox search`() {
        // Arrange & Act
        val testSubject = SearchAccount.createUnifiedSearch(
            folderType = FolderType.INBOX,
            title = "Unified Inbox",
            detail = "detail",
        )

        // Assert
        val expected = SearchAccount.createUnifiedInboxSearch(title = "Unified Inbox", detail = "detail")
        assertThat(testSubject.id).isEqualTo(UnifiedAccountId)
        assertThat(testSubject.name).isEqualTo("Unified Inbox")
        assertThat(testSubject.email).isEqualTo("detail")
        assertThat(testSubject.relatedSearch.id).isEqualTo(expected.relatedSearch.id)
        assertThat(testSubject.relatedSearch.type).isEqualTo(expected.relatedSearch.type)
        assertThat(testSubject.relatedSearch.leafSet.map { it.condition }).containsExactly(
            SearchCondition(MessageSearchField.INTEGRATE, SearchAttribute.EQUALS, "1"),
        )
    }

    @Test
    fun `createUnifiedSearch() with SENT should create a search without conditions`() {
        // Arrange & Act
        val testSubject = SearchAccount.createUnifiedSearch(
            folderType = FolderType.SENT,
            title = "Unified Sent",
            detail = "detail",
        )

        // Assert
        val search = testSubject.relatedSearch
        assertThat(testSubject.id).isEqualTo(UnifiedAccountId)
        assertThat(testSubject.name).isEqualTo("Unified Sent")
        assertThat(testSubject.email).isEqualTo("detail")
        assertThat(search.id).isEqualTo(UnifiedAccountId)
        assertThat(search.type).isEqualTo(
            LocalMessageSearchType.Unified(UnifiedFolderSelection.Special(FolderType.SENT)),
        )
        assertThat(search.leafSet).isEmpty()
    }

    @Test
    fun `createUnifiedSearch() with REGULAR should throw`() {
        // Arrange & Act
        val exception = assertFailsWith<IllegalArgumentException> {
            SearchAccount.createUnifiedSearch(
                folderType = FolderType.REGULAR,
                title = "Unified Regular",
                detail = "detail",
            )
        }

        // Assert
        assertThat(exception.message).isEqualTo("Unsupported unified folder type: REGULAR")
    }
}
