package com.fsck.k9.ui.settings.account

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import com.fsck.k9.K9RobolectricTest
import com.fsck.k9.ui.R
import net.thunderbird.feature.mail.folder.FolderType
import net.thunderbird.feature.mail.folder.api.RemoteFolder
import org.junit.Test

class FolderListPreferenceTest : K9RobolectricTest() {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val inbox = createRemoteFolder(id = 1L, name = "INBOX", serverId = "INBOX", type = FolderType.INBOX)
    private val trash = createRemoteFolder(id = 2L, name = "Trash", serverId = "INBOX.Trash", type = FolderType.TRASH)
    private val folders = listOf(inbox, trash)

    private val testSubject = FolderListPreference(context)

    @Test
    fun `folders should be displayed with their server id`() {
        // Arrange

        // Act
        testSubject.setFolders(folders) { it.serverId }

        // Assert
        assertThat(testSubject.entries.map { it.toString() }).containsExactly(
            context.getString(R.string.account_settings_no_folder_selected),
            "INBOX",
            "INBOX.Trash",
        )
    }

    @Test
    fun `automatic folder option should contain the server id of the automatic folder`() {
        // Arrange

        // Act
        testSubject.setFolders(folders, trash) { it.serverId }

        // Assert
        assertThat(testSubject.entries[0].toString()).contains("INBOX.Trash")
    }

    @Test
    fun `entry values should be unchanged when no automatic folder is selected`() {
        // Arrange

        // Act
        testSubject.setFolders(folders) { it.serverId }

        // Assert
        assertThat(testSubject.entryValues.map { it.toString() }).containsExactly(
            "MANUAL|",
            "MANUAL|1",
            "MANUAL|2",
        )
    }

    @Test
    fun `entry values should be unchanged when an automatic folder is selected`() {
        // Arrange

        // Act
        testSubject.setFolders(folders, trash) { it.serverId }

        // Assert
        assertThat(testSubject.entryValues.map { it.toString() }).containsExactly(
            "AUTOMATIC|2",
            "MANUAL|",
            "MANUAL|1",
            "MANUAL|2",
        )
    }

    @Test
    fun `automatic folder option should keep the spans of the folder text`() {
        // Arrange
        val boldFolderText: (RemoteFolder) -> CharSequence = { folder ->
            SpannableString(folder.serverId).apply { setSpan(StyleSpan(Typeface.BOLD), 0, length, 0) }
        }

        // Act
        testSubject.setFolders(folders, trash, boldFolderText)

        // Assert
        val automaticOption = testSubject.entries[0] as Spanned
        val boldSpans = automaticOption.getSpans(0, automaticOption.length, StyleSpan::class.java)
            .filter { it.style == Typeface.BOLD }
        assertThat(boldSpans).hasSize(1)
    }

    private fun createRemoteFolder(id: Long, name: String, serverId: String, type: FolderType): RemoteFolder {
        return RemoteFolder(id = id, serverId = serverId, name = name, type = type)
    }
}
