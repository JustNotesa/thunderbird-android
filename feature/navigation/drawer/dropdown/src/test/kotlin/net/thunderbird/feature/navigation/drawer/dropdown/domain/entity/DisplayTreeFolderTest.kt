package net.thunderbird.feature.navigation.drawer.dropdown.domain.entity

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.thunderbird.feature.mail.folder.FolderType
import net.thunderbird.feature.mail.folder.api.Folder

class DisplayTreeFolderTest {

    @Test
    fun `containsFolder should find a direct child`() {
        val child = createTreeFolder(folderId = 2)
        val testSubject = createTreeFolder(folderId = 1, children = persistentListOf(child))

        val result = testSubject.containsFolder(child.displayFolder?.id)

        assertThat(result).isTrue()
    }

    @Test
    fun `containsFolder should find a folder further down`() {
        val grandchild = createTreeFolder(folderId = 3)
        val child = createTreeFolder(folderId = 2, children = persistentListOf(grandchild))
        val testSubject = createTreeFolder(folderId = 1, children = persistentListOf(child))

        val result = testSubject.containsFolder(grandchild.displayFolder?.id)

        assertThat(result).isTrue()
    }

    @Test
    fun `containsFolder should not find the folder itself`() {
        val testSubject = createTreeFolder(folderId = 1, children = persistentListOf(createTreeFolder(folderId = 2)))

        val result = testSubject.containsFolder(testSubject.displayFolder?.id)

        assertThat(result).isFalse()
    }

    @Test
    fun `containsFolder should not find a folder located elsewhere`() {
        val other = createTreeFolder(folderId = 3)
        val testSubject = createTreeFolder(folderId = 1, children = persistentListOf(createTreeFolder(folderId = 2)))

        val result = testSubject.containsFolder(other.displayFolder?.id)

        assertThat(result).isFalse()
    }

    @Test
    fun `containsFolder should not find anything without a folder ID`() {
        val placeholder = DisplayTreeFolder(
            displayFolder = null,
            displayName = null,
            totalUnreadCount = 0,
            totalStarredCount = 0,
            children = persistentListOf(),
        )
        val testSubject = createTreeFolder(folderId = 1, children = persistentListOf(placeholder))

        val result = testSubject.containsFolder(null)

        assertThat(result).isFalse()
    }

    private fun createTreeFolder(
        folderId: Long,
        children: ImmutableList<DisplayTreeFolder> = persistentListOf(),
    ): DisplayTreeFolder {
        return DisplayTreeFolder(
            displayFolder = MailDisplayFolder(
                accountId = "account",
                folder = Folder(
                    id = folderId,
                    name = "Folder $folderId",
                    type = FolderType.REGULAR,
                    isLocalOnly = false,
                ),
                isInTopGroup = false,
                unreadMessageCount = 0,
                starredMessageCount = 0,
                pathDelimiter = "/",
            ),
            displayName = "Folder $folderId",
            totalUnreadCount = 0,
            totalStarredCount = 0,
            children = children,
        )
    }
}
