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
import assertk.assertions.doesNotContain
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.isEqualTo
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

    @Test
    fun `summary should contain the folder text and the hint when a hint is set`() {
        // Arrange
        testSubject.setFolders(folders) { it.serverId }
        testSubject.value = "MANUAL|2"

        // Act
        testSubject.summaryHint = "Hint text"

        // Assert
        val summary = testSubject.summary.toString()
        assertThat(summary).contains("INBOX.Trash")
        assertThat(summary).contains("Hint text")
    }

    @Test
    fun `summary should not contain a hint when no hint is set`() {
        // Arrange
        testSubject.setFolders(folders) { it.serverId }
        testSubject.value = "MANUAL|2"

        // Act
        val summary = testSubject.summary.toString()

        // Assert
        assertThat(summary).contains("INBOX.Trash")
        assertThat(summary).doesNotContain("\n")
    }

    @Test
    fun `automatic option and summary should show the label and the folder on one line`() {
        // Arrange
        testSubject.setFolders(folders, trash) { it.serverId }
        testSubject.value = "AUTOMATIC|2"

        // Act
        val summary = testSubject.summary.toString()

        // Assert
        val label = context.getString(R.string.account_settings_automatic_folder)
        assertThat(summary).isEqualTo("$label \u00B7 INBOX.Trash")
    }

    @Test
    fun `dialog rows should not contain the automatic entry`() {
        // Arrange

        // Act
        testSubject.setFolders(folders, trash) { it.serverId }

        // Assert
        assertThat(testSubject.dialogRows.map { it.first }).containsExactly("MANUAL|", "MANUAL|1", "MANUAL|2")
        assertThat(testSubject.automaticValue).isEqualTo("AUTOMATIC|2")
    }

    @Test
    fun `dialog rows should contain all entries and no automatic value without automatic option`() {
        // Arrange

        // Act
        testSubject.setFolders(folders) { it.serverId }

        // Assert
        assertThat(testSubject.dialogRows).hasSize(3)
        assertThat(testSubject.automaticValue).isNull()
    }

    @Test
    fun `radio dot should sit on the automatic folder while automatic is selected`() {
        assertThat(FolderSelection.rowValue("AUTOMATIC|2")).isEqualTo("MANUAL|2")
    }

    @Test
    fun `radio dot should sit on none when automatic resolves to no folder`() {
        assertThat(FolderSelection.rowValue("AUTOMATIC|")).isEqualTo("MANUAL|")
    }

    @Test
    fun `radio dot should sit on the manual selection`() {
        assertThat(FolderSelection.rowValue("MANUAL|1")).isEqualTo("MANUAL|1")
    }

    @Test
    fun `switching off should keep the folder as a manual selection`() {
        assertThat(FolderSelection.switchedOff("AUTOMATIC|2")).isEqualTo("MANUAL|2")
        assertThat(FolderSelection.switchedOff("AUTOMATIC|")).isEqualTo("MANUAL|")
    }

    @Test
    fun `switching on should select the automatic value`() {
        assertThat(FolderSelection.switchedOn("AUTOMATIC|2")).isEqualTo("AUTOMATIC|2")
        assertThat(FolderSelection.isAutomatic("AUTOMATIC|2")).isTrue()
        assertThat(FolderSelection.isAutomatic("MANUAL|2")).isFalse()
    }

    @Test
    fun `tapping a row should select it manually`() {
        assertThat(FolderSelection.rowSelected("MANUAL|1")).isEqualTo("MANUAL|1")
    }

    @Test
    fun `select should store the value`() {
        // Arrange
        testSubject.setFolders(folders, trash) { it.serverId }
        testSubject.value = "AUTOMATIC|2"

        // Act
        testSubject.select("MANUAL|1")

        // Assert
        assertThat(testSubject.value).isEqualTo("MANUAL|1")
    }

    private fun createRemoteFolder(id: Long, name: String, serverId: String, type: FolderType): RemoteFolder {
        return RemoteFolder(id = id, serverId = serverId, name = name, type = type)
    }
}
