package com.fsck.k9.search

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import com.fsck.k9.FakeLegacyAccount
import net.thunderbird.feature.account.AccountIdFactory
import net.thunderbird.feature.mail.folder.FolderType
import net.thunderbird.feature.search.legacy.LocalMessageSearch
import net.thunderbird.feature.search.legacy.LocalMessageSearchType
import net.thunderbird.feature.search.legacy.UnifiedFolderSelection
import net.thunderbird.feature.search.legacy.api.MessageSearchField
import net.thunderbird.feature.search.legacy.api.SearchAttribute
import net.thunderbird.feature.search.legacy.sql.SqlWhereClause
import org.junit.Test

private const val SENT_FOLDER_ID = 7L
private const val TRASH_FOLDER_ID = 42L

class LocalSearchExtensionsTest {

    private val accountWithSent = FakeLegacyAccount.create(sentFolderId = SENT_FOLDER_ID)
    private val accountWithTrash = FakeLegacyAccount.ACCOUNT.copy(
        id = AccountIdFactory.create(),
        trashFolderId = TRASH_FOLDER_ID,
    )
    private val accountWithoutSpecialFolders = FakeLegacyAccount.ACCOUNT.copy(id = AccountIdFactory.create())

    @Test
    fun `conditionsForAccount() with a regular search should return the search conditions`() {
        // Arrange
        val search = LocalMessageSearch().apply {
            addAccountId(accountWithSent.id)
            and(MessageSearchField.SUBJECT, "hello", SearchAttribute.CONTAINS)
        }
        val conditions = search.conditions

        // Act
        val result = search.conditionsForAccount(accountWithSent)

        // Assert
        assertThat(result).isSameInstanceAs(conditions)
        val whereClause = SqlWhereClause.Builder().withConditions(result).build()
        assertThat(whereClause.selection).isEqualTo("subject LIKE ?")
        assertThat(whereClause.selectionArgs).containsExactly("%hello%")
    }

    @Test
    fun `conditionsForAccount() with a unified inbox search should return the search conditions`() {
        // Arrange
        val search = createUnifiedSearch(FolderType.INBOX).apply {
            and(MessageSearchField.INTEGRATE, "1", SearchAttribute.EQUALS)
        }
        val conditions = search.conditions

        // Act
        val result = search.conditionsForAccount(accountWithSent)

        // Assert
        assertThat(result).isSameInstanceAs(conditions)
        val whereClause = SqlWhereClause.Builder().withConditions(result).build()
        assertThat(whereClause.selection).isEqualTo("integrate = ?")
        assertThat(whereClause.selectionArgs).containsExactly("1")
    }

    @Test
    fun `conditionsForAccount() with a unified Sent search should restrict to the sent folder of the account`() {
        // Arrange
        val search = createUnifiedSearch(FolderType.SENT)

        // Act
        val result = search.conditionsForAccount(accountWithSent)

        // Assert
        val whereClause = SqlWhereClause.Builder().withConditions(result).build()
        assertThat(whereClause.selection).isEqualTo("folder_id = ?")
        assertThat(whereClause.selectionArgs).containsExactly(SENT_FOLDER_ID.toString())

        // The search itself is left untouched
        assertThat(search.type).isEqualTo(
            LocalMessageSearchType.Unified(UnifiedFolderSelection.Special(FolderType.SENT)),
        )
        assertThat(search.leafSet).isEmpty()
    }

    @Test
    fun `conditionsForAccount() with a unified search with conditions should combine both`() {
        // Arrange
        val search = createUnifiedSearch(FolderType.TRASH).apply {
            and(MessageSearchField.READ, "0", SearchAttribute.EQUALS)
        }
        val conditions = search.conditions

        // Act
        val result = search.conditionsForAccount(accountWithTrash)

        // Assert
        val whereClause = SqlWhereClause.Builder().withConditions(result).build()
        assertThat(whereClause.selection).isEqualTo("(folder_id = ?) AND (read = ?)")
        assertThat(whereClause.selectionArgs).containsExactly(TRASH_FOLDER_ID.toString(), "0")

        // The search itself is left untouched
        assertThat(search.conditions).isSameInstanceAs(conditions)
        assertThat(search.leafSet).hasSize(1)
    }

    @Test
    fun `conditionsForAccount() with a unified search for a missing special folder should match no folder`() {
        // Arrange
        val search = createUnifiedSearch(FolderType.TRASH)

        // Act
        val result = search.conditionsForAccount(accountWithoutSpecialFolders)

        // Assert
        val whereClause = SqlWhereClause.Builder().withConditions(result).build()
        assertThat(whereClause.selection).isEqualTo("folder_id = ?")
        assertThat(whereClause.selectionArgs).containsExactly("-1")
    }

    @Test
    fun `unifiedSpecialFolderType with a unified inbox search should be null`() {
        // Arrange
        val search = createUnifiedSearch(FolderType.INBOX)

        // Act & Assert
        assertThat(search.unifiedSpecialFolderType).isNull()
    }

    @Test
    fun `unifiedSpecialFolderType with a unified Sent search should be SENT`() {
        // Arrange
        val search = createUnifiedSearch(FolderType.SENT)

        // Act & Assert
        assertThat(search.unifiedSpecialFolderType).isEqualTo(FolderType.SENT)
    }

    @Test
    fun `unifiedSpecialFolderType with a regular search should be null`() {
        // Arrange
        val search = LocalMessageSearch()

        // Act & Assert
        assertThat(search.unifiedSpecialFolderType).isNull()
    }

    private fun createUnifiedSearch(folderType: FolderType) = LocalMessageSearch().apply {
        type = LocalMessageSearchType.Unified(UnifiedFolderSelection.Special(folderType))
    }
}
