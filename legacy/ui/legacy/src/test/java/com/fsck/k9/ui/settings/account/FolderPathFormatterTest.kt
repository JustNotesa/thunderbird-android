package com.fsck.k9.ui.settings.account

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.fsck.k9.K9RobolectricTest
import net.thunderbird.feature.mail.folder.FolderType
import net.thunderbird.feature.mail.folder.api.RemoteFolder
import org.junit.Test

class FolderPathFormatterTest : K9RobolectricTest() {
    private val inbox = remoteFolder(id = 1, serverId = "INBOX", type = FolderType.INBOX)

    @Test
    fun `root folder should have one segment`() {
        // Arrange
        val sent = remoteFolder(id = 2, serverId = "Sent", type = FolderType.SENT)
        val testSubject = FolderPathFormatter(listOf(inbox, sent), pathDelimiter = "/", useServerPath = true)

        // Act
        val segments = testSubject.segments(sent)

        // Assert
        assertThat(segments).containsExactly(FolderPathSegment("Sent", FolderType.SENT))
    }

    @Test
    fun `nested folder should be split by the server's delimiter`() {
        // Arrange
        val archive = remoteFolder(id = 2, serverId = "Archiv", type = FolderType.REGULAR)
        val archive2024 = remoteFolder(id = 3, serverId = "Archiv.2024", type = FolderType.ARCHIVE)
        val testSubject = FolderPathFormatter(listOf(archive, archive2024), pathDelimiter = ".", useServerPath = true)

        // Act
        val segments = testSubject.segments(archive2024)

        // Assert
        assertThat(segments).containsExactly(
            FolderPathSegment("Archiv", FolderType.REGULAR),
            FolderPathSegment("2024", FolderType.ARCHIVE),
        )
    }

    @Test
    fun `parent level should use the type of the parent folder`() {
        // Arrange
        val invoices = remoteFolder(id = 2, serverId = "INBOX/Rechnungen", type = FolderType.REGULAR)
        val year = remoteFolder(id = 3, serverId = "INBOX/Rechnungen/2026", type = FolderType.REGULAR)
        val testSubject = FolderPathFormatter(listOf(inbox, invoices, year), pathDelimiter = "/", useServerPath = true)

        // Act
        val segments = testSubject.segments(year)

        // Assert
        assertThat(segments).containsExactly(
            FolderPathSegment("INBOX", FolderType.INBOX),
            FolderPathSegment("Rechnungen", FolderType.REGULAR),
            FolderPathSegment("2026", FolderType.REGULAR),
        )
    }

    @Test
    fun `parent level matching INBOX should ignore case`() {
        // Arrange
        val sent = remoteFolder(id = 2, serverId = "inbox.Sent", type = FolderType.SENT)
        val testSubject = FolderPathFormatter(listOf(inbox, sent), pathDelimiter = ".", useServerPath = true)

        // Act
        val segments = testSubject.segments(sent)

        // Assert
        assertThat(segments).containsExactly(
            FolderPathSegment("inbox", FolderType.INBOX),
            FolderPathSegment("Sent", FolderType.SENT),
        )
    }

    @Test
    fun `parent level missing from the folder list should be a regular folder`() {
        // Arrange
        val child = remoteFolder(id = 2, serverId = "Unknown/Child", type = FolderType.REGULAR)
        val testSubject = FolderPathFormatter(listOf(inbox, child), pathDelimiter = "/", useServerPath = true)

        // Act
        val segments = testSubject.segments(child)

        // Assert
        assertThat(segments).containsExactly(
            FolderPathSegment("Unknown", FolderType.REGULAR),
            FolderPathSegment("Child", FolderType.REGULAR),
        )
    }

    @Test
    fun `modified UTF-7 names should be decoded`() {
        // Arrange
        val drafts = remoteFolder(id = 2, serverId = "Entw&APw-rfe", type = FolderType.DRAFTS)
        val testSubject = FolderPathFormatter(listOf(drafts), pathDelimiter = "/", useServerPath = true)

        // Act
        val segments = testSubject.segments(drafts)

        // Assert
        assertThat(segments).containsExactly(FolderPathSegment("Entwürfe", FolderType.DRAFTS))
    }

    @Test
    fun `encoded ampersand should be decoded`() {
        // Arrange

        // Act
        val decoded = decodeModifiedUtf7("A&-B")

        // Assert
        assertThat(decoded).isEqualTo("A&B")
    }

    @Test
    fun `malformed modified UTF-7 should stay unchanged`() {
        // Arrange

        // Act
        val decoded = decodeModifiedUtf7("Entw&APw")

        // Assert
        assertThat(decoded).isEqualTo("Entw&APw")
    }

    @Test
    fun `empty delimiter should give one segment`() {
        // Arrange
        val folder = remoteFolder(id = 2, serverId = "A.B", type = FolderType.REGULAR)
        val testSubject = FolderPathFormatter(listOf(folder), pathDelimiter = "", useServerPath = true)

        // Act
        val segments = testSubject.segments(folder)

        // Assert
        assertThat(segments).containsExactly(FolderPathSegment("A.B", FolderType.REGULAR))
    }

    @Test
    fun `without server path the folder name should be used`() {
        // Arrange
        val folder = RemoteFolder(id = 2, serverId = "opaque-id-17", name = "Gesendet", type = FolderType.SENT)
        val testSubject = FolderPathFormatter(listOf(folder), pathDelimiter = "/", useServerPath = false)

        // Act
        val segments = testSubject.segments(folder)

        // Assert
        assertThat(segments).containsExactly(FolderPathSegment("Gesendet", FolderType.SENT))
    }

    @Test
    fun `special folders should have their special-use label`() {
        // Arrange
        val testSubject = FolderPathFormatter(emptyList(), pathDelimiter = "/", useServerPath = true)

        // Act
        val labels = listOf(
            FolderType.ARCHIVE,
            FolderType.DRAFTS,
            FolderType.SENT,
            FolderType.SPAM,
            FolderType.TRASH,
        ).map { testSubject.serverLabel(remoteFolder(id = 1, serverId = "x", type = it)) }

        // Assert
        assertThat(labels).containsExactly("\\Archive", "\\Drafts", "\\Sent", "\\Junk", "\\Trash")
    }

    @Test
    fun `regular folder and INBOX should have no label`() {
        // Arrange
        val testSubject = FolderPathFormatter(emptyList(), pathDelimiter = "/", useServerPath = true)

        // Act
        val regularLabel = testSubject.serverLabel(remoteFolder(id = 2, serverId = "x", type = FolderType.REGULAR))
        val inboxLabel = testSubject.serverLabel(inbox)

        // Assert
        assertThat(regularLabel).isNull()
        assertThat(inboxLabel).isNull()
    }

    private fun remoteFolder(id: Long, serverId: String, type: FolderType): RemoteFolder {
        return RemoteFolder(id = id, serverId = serverId, name = serverId, type = type)
    }
}
