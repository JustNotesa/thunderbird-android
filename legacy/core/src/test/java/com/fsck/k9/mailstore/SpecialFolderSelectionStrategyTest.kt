package com.fsck.k9.mailstore

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import net.thunderbird.feature.mail.folder.FolderType
import net.thunderbird.feature.mail.folder.api.RemoteFolder
import org.junit.Test

class SpecialFolderSelectionStrategyTest {
    private val testSubject = SpecialFolderSelectionStrategy()

    @Test
    fun `folder with matching type should win over folder with matching name`() {
        // Arrange
        val sentItemsFolder = createFolder(id = 1, name = "Sent Items")
        val sentFolder = createFolder(id = 2, name = "Sent", type = FolderType.SENT)

        // Act
        val result = testSubject.selectSpecialFolder(listOf(sentItemsFolder, sentFolder), FolderType.SENT)

        // Assert
        assertThat(result).isEqualTo(sentFolder)
    }

    @Test
    fun `name fallback should find well known name`() {
        // Arrange
        val otherFolder = createFolder(id = 1, name = "Reise")
        val sentFolder = createFolder(id = 2, name = "Gesendet")

        // Act
        val result = testSubject.selectSpecialFolder(listOf(otherFolder, sentFolder), FolderType.SENT)

        // Assert
        assertThat(result).isEqualTo(sentFolder)
    }

    @Test
    fun `name fallback should find folder with dot separated inbox prefix`() {
        // Arrange
        val sentFolder = createFolder(id = 1, name = "INBOX.Sent")

        // Act
        val result = testSubject.selectSpecialFolder(listOf(sentFolder), FolderType.SENT)

        // Assert
        assertThat(result).isEqualTo(sentFolder)
    }

    @Test
    fun `name fallback should find folder with slash separated inbox prefix`() {
        // Arrange
        val draftsFolder = createFolder(id = 1, name = "INBOX/Drafts")

        // Act
        val result = testSubject.selectSpecialFolder(listOf(draftsFolder), FolderType.DRAFTS)

        // Assert
        assertThat(result).isEqualTo(draftsFolder)
    }

    @Test
    fun `name fallback should ignore case`() {
        // Arrange
        val spamFolder = createFolder(id = 1, name = "Junk E-Mail")

        // Act
        val result = testSubject.selectSpecialFolder(listOf(spamFolder), FolderType.SPAM)

        // Assert
        assertThat(result).isEqualTo(spamFolder)
    }

    @Test
    fun `name fallback should find folder with non-ASCII name`() {
        // Arrange
        val trashFolder = createFolder(id = 1, name = "Papierkorb")

        // Act
        val result = testSubject.selectSpecialFolder(listOf(trashFolder), FolderType.TRASH)

        // Assert
        assertThat(result).isEqualTo(trashFolder)
    }

    @Test
    fun `name fallback should not match folder nested in another folder`() {
        // Arrange
        val nestedFolder = createFolder(id = 1, name = "Projects/Sent")

        // Act
        val result = testSubject.selectSpecialFolder(listOf(nestedFolder), FolderType.SENT)

        // Assert
        assertThat(result).isNull()
    }

    @Test
    fun `name fallback should not match folder with additional hierarchy level below inbox`() {
        // Arrange
        val nestedFolder = createFolder(id = 1, name = "INBOX.Archive.2024")

        // Act
        val result = testSubject.selectSpecialFolder(listOf(nestedFolder), FolderType.ARCHIVE)

        // Assert
        assertThat(result).isNull()
    }

    @Test
    fun `name fallback should not match folder that is not of type REGULAR`() {
        // Arrange
        val archiveFolder = createFolder(id = 1, name = "Trash", type = FolderType.ARCHIVE)

        // Act
        val result = testSubject.selectSpecialFolder(listOf(archiveFolder), FolderType.TRASH)

        // Assert
        assertThat(result).isNull()
    }

    @Test
    fun `name fallback should prefer name that comes first in the list of well known names`() {
        // Arrange
        val deletedItemsFolder = createFolder(id = 1, name = "Deleted Items")
        val trashFolder = createFolder(id = 2, name = "Trash")

        // Act
        val result = testSubject.selectSpecialFolder(listOf(deletedItemsFolder, trashFolder), FolderType.TRASH)

        // Assert
        assertThat(result).isEqualTo(trashFolder)
    }

    @Test
    fun `name fallback should prefer top level folder over folder in inbox`() {
        // Arrange
        val inboxSentFolder = createFolder(id = 1, name = "INBOX.Sent")
        val sentFolder = createFolder(id = 2, name = "Sent")

        // Act
        val result = testSubject.selectSpecialFolder(listOf(inboxSentFolder, sentFolder), FolderType.SENT)

        // Assert
        assertThat(result).isEqualTo(sentFolder)
    }

    @Test
    fun `name fallback should not be used for INBOX`() {
        // Arrange
        val sentFolder = createFolder(id = 1, name = "Sent")

        // Act
        val result = testSubject.selectSpecialFolder(listOf(sentFolder), FolderType.INBOX)

        // Assert
        assertThat(result).isNull()
    }

    @Test
    fun `name fallback should not be used for REGULAR`() {
        // Arrange
        val sentFolder = createFolder(id = 1, name = "Sent", type = FolderType.SENT)

        // Act
        val result = testSubject.selectSpecialFolder(listOf(sentFolder), FolderType.REGULAR)

        // Assert
        assertThat(result).isNull()
    }

    private fun createFolder(id: Long, name: String, type: FolderType = FolderType.REGULAR): RemoteFolder {
        return RemoteFolder(id = id, serverId = name, name = name, type = type)
    }
}
