package net.thunderbird.feature.navigation.drawer.dropdown.ui.folder

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import app.k9mail.core.ui.compose.testing.ComposeTest
import app.k9mail.core.ui.compose.testing.onNodeWithText
import app.k9mail.core.ui.compose.testing.setContentWithTheme
import kotlin.test.Test
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.thunderbird.feature.mail.folder.FolderType
import net.thunderbird.feature.mail.folder.api.Folder
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.DisplayFolder
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.DisplayTreeFolder
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.MailDisplayFolder

internal class FolderListKtTest : ComposeTest() {

    @Test
    fun `should not show nested folders by default`() = runComposeTest {
        val child = createTreeFolder(folderId = 2, name = "Parent/Child")
        val rootFolder = createRootFolder(
            createTreeFolder(folderId = 1, name = "Parent", children = persistentListOf(child)),
        )

        setContentWithTheme {
            FolderList(
                rootFolder = rootFolder,
                selectedFolder = null,
                onFolderClick = {},
                showStarredCount = false,
            )
        }

        onNodeWithText("Parent").assertIsDisplayed()
        onNodeWithText("Child").assertDoesNotExist()
    }

    @Test
    fun `should expand the folders above the selected folder`() = runComposeTest {
        val grandchild = createTreeFolder(folderId = 3, name = "Parent/Child/Grandchild")
        val child = createTreeFolder(folderId = 2, name = "Parent/Child", children = persistentListOf(grandchild))
        val rootFolder = createRootFolder(
            createTreeFolder(folderId = 1, name = "Parent", children = persistentListOf(child)),
        )

        setContentWithTheme {
            FolderList(
                rootFolder = rootFolder,
                selectedFolder = grandchild.displayFolder,
                onFolderClick = {},
                showStarredCount = false,
            )
        }

        onNodeWithText("Grandchild").assertIsDisplayed()
    }

    @Test
    fun `should scroll to the selected folder`() = runComposeTest {
        val child = createTreeFolder(folderId = 1001, name = "Last/Child")
        val rootFolder = createRootFolderWithManyFolders(lastFolderChild = child)

        setContentWithTheme {
            FolderList(
                rootFolder = rootFolder,
                selectedFolder = child.displayFolder,
                onFolderClick = {},
                showStarredCount = false,
            )
        }

        onNodeWithText("Child").assertIsDisplayed()
    }

    @Test
    fun `should scroll to a folder that is selected later`() = runComposeTest {
        val child = createTreeFolder(folderId = 1001, name = "Last/Child")
        val rootFolder = createRootFolderWithManyFolders(lastFolderChild = child)
        var selectedFolder by mutableStateOf<DisplayFolder?>(null)

        setContentWithTheme {
            FolderList(
                rootFolder = rootFolder,
                selectedFolder = selectedFolder,
                onFolderClick = {},
                showStarredCount = false,
            )
        }
        onNodeWithText("Child").assertDoesNotExist()

        selectedFolder = child.displayFolder

        onNodeWithText("Child").assertIsDisplayed()
    }

    private fun createRootFolderWithManyFolders(lastFolderChild: DisplayTreeFolder): DisplayTreeFolder {
        val folders = (1L..FOLDER_COUNT).map { createTreeFolder(folderId = it, name = "Folder $it") }
        val lastFolder = createTreeFolder(
            folderId = FOLDER_COUNT + 1,
            name = "Last",
            children = persistentListOf(lastFolderChild),
        )

        return createRootFolder(*folders.toTypedArray(), lastFolder)
    }

    private fun createRootFolder(vararg folders: DisplayTreeFolder): DisplayTreeFolder {
        return DisplayTreeFolder(
            displayFolder = null,
            displayName = null,
            totalUnreadCount = 0,
            totalStarredCount = 0,
            children = folders.toList().toImmutableList(),
        )
    }

    private fun createTreeFolder(
        folderId: Long,
        name: String,
        children: ImmutableList<DisplayTreeFolder> = persistentListOf(),
    ): DisplayTreeFolder {
        return DisplayTreeFolder(
            displayFolder = MailDisplayFolder(
                accountId = "account",
                folder = Folder(
                    id = folderId,
                    name = name,
                    type = FolderType.REGULAR,
                    isLocalOnly = false,
                ),
                isInTopGroup = false,
                unreadMessageCount = 0,
                starredMessageCount = 0,
                pathDelimiter = "/",
            ),
            displayName = name,
            totalUnreadCount = 0,
            totalStarredCount = 0,
            children = children,
        )
    }

    private companion object {
        const val FOLDER_COUNT = 60L
    }
}
