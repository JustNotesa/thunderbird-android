package net.thunderbird.feature.navigation.drawer.dropdown.ui.folder

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import net.thunderbird.components.ui.bolt.PreviewWithTheme
import net.thunderbird.feature.navigation.drawer.dropdown.ui.FakeData.DISPLAY_FOLDER
import net.thunderbird.feature.navigation.drawer.dropdown.ui.FakeData.DISPLAY_TREE_FOLDER
import net.thunderbird.feature.navigation.drawer.dropdown.ui.FakeData.DISPLAY_TREE_FOLDER_WITH_NESTED_FOLDERS
import net.thunderbird.feature.navigation.drawer.dropdown.ui.FakeData.DISPLAY_TREE_FOLDER_WITH_UNIFIED_FOLDER
import net.thunderbird.feature.navigation.drawer.dropdown.ui.FakeData.DISPLAY_TREE_FOLDER_WITH_UNIFIED_TREE
import net.thunderbird.feature.navigation.drawer.dropdown.ui.FakeData.EMPTY_DISPLAY_TREE_FOLDER
import net.thunderbird.feature.navigation.drawer.dropdown.ui.FakeData.PINNED_DISPLAY_TREE_FOLDERS

@Composable
@Preview(showBackground = true)
internal fun FolderListPreview() {
    PreviewWithTheme {
        FolderList(
            rootFolder = EMPTY_DISPLAY_TREE_FOLDER,
            selectedFolder = null,
            onFolderClick = {},
            showStarredCount = false,
        )
    }
}

@Composable
@Preview(showBackground = true)
internal fun FolderListPreviewSelected() {
    PreviewWithTheme {
        FolderList(
            rootFolder = DISPLAY_TREE_FOLDER,
            selectedFolder = DISPLAY_FOLDER,
            onFolderClick = {},
            showStarredCount = false,
        )
    }
}

@Composable
@Preview(showBackground = true)
internal fun FolderListWithUnifiedFolderPreview() {
    PreviewWithTheme {
        FolderList(
            rootFolder = DISPLAY_TREE_FOLDER_WITH_UNIFIED_FOLDER,
            selectedFolder = DISPLAY_FOLDER,
            onFolderClick = {},
            showStarredCount = false,
        )
    }
}

@Composable
@Preview(showBackground = true)
internal fun FolderListWithUnifiedFolderPreviewSelected() {
    PreviewWithTheme {
        FolderList(
            rootFolder = DISPLAY_TREE_FOLDER_WITH_NESTED_FOLDERS,
            selectedFolder = null,
            onFolderClick = {},
            showStarredCount = false,
        )
    }
}

@Composable
@Preview(showBackground = true)
internal fun FolderListWithUnifiedTreePreview() {
    PreviewWithTheme {
        FolderList(
            rootFolder = DISPLAY_TREE_FOLDER_WITH_UNIFIED_TREE,
            selectedFolder = DISPLAY_TREE_FOLDER_WITH_UNIFIED_TREE.children.first().displayFolder,
            onFolderClick = {},
            showStarredCount = false,
            isExpandedInitial = true,
        )
    }
}

@Composable
@Preview(showBackground = true)
internal fun FolderListWithPinnedFoldersPreview() {
    PreviewWithTheme {
        FolderList(
            rootFolder = DISPLAY_TREE_FOLDER_WITH_UNIFIED_FOLDER,
            selectedFolder = PINNED_DISPLAY_TREE_FOLDERS.first().displayFolder,
            onFolderClick = {},
            showStarredCount = false,
            pinnedFolders = PINNED_DISPLAY_TREE_FOLDERS,
            accountSectionTitle = "Account Name",
        )
    }
}
