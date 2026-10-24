package net.thunderbird.feature.navigation.drawer.dropdown.ui.folder

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import app.k9mail.legacy.ui.folder.FolderNameFormatter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.first
import net.thunderbird.components.ui.bolt.atom.DividerHorizontal
import net.thunderbird.components.ui.bolt.atom.text.TextLabelSmall
import net.thunderbird.components.ui.bolt.theme.BoltTheme
import net.thunderbird.feature.navigation.drawer.dropdown.R
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.DisplayFolder
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.DisplayTreeFolder

private const val PINNED_SECTION_HEADER_KEY = "pinnedSectionHeader"
private const val PINNED_DIVIDER_KEY = "pinnedDivider"
private const val ACCOUNT_SECTION_HEADER_KEY = "accountSectionHeader"
private const val PINNED_FOLDER_KEY_PREFIX = "pinned:"

@Suppress("LongMethod")
@Composable
internal fun FolderList(
    rootFolder: DisplayTreeFolder,
    selectedFolder: DisplayFolder?,
    onFolderClick: (DisplayFolder) -> Unit,
    showStarredCount: Boolean,
    modifier: Modifier = Modifier,
    isExpandedInitial: Boolean = false,
    pinnedFolders: ImmutableList<DisplayTreeFolder> = persistentListOf(),
    accountSectionTitle: String? = null,
) {
    val resources = LocalResources.current
    val folderNameFormatter = remember { FolderNameFormatter(resources) }
    val listState = rememberLazyListState()

    // Bring the selected folder into view whenever another folder is selected, e.g. when a folder was opened from
    // somewhere else than this list.
    val selectedFolderId = selectedFolder?.folderId
    val selectedItemIndex = remember(rootFolder, selectedFolderId) {
        rootFolder.children.indexOfFirst { folder ->
            selectedFolderId != null &&
                (folder.displayFolder?.folderId == selectedFolderId || folder.containsFolder(selectedFolderId))
        }
    }
    val isSelectedFolderListed = selectedItemIndex >= 0
    var folderIdToReveal by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedFolderId, isSelectedFolderListed) {
        if (isSelectedFolderListed) {
            val visibleItems = snapshotFlow { listState.layoutInfo.visibleItemsInfo }.first { it.isNotEmpty() }
            if (visibleItems.none { it.index == selectedItemIndex }) {
                listState.scrollToItem(selectedItemIndex)
            }
            folderIdToReveal = selectedFolderId
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = BoltTheme.spacings.default),
    ) {
        if (pinnedFolders.isNotEmpty()) {
            item(key = PINNED_SECTION_HEADER_KEY) {
                FolderListSectionHeader(
                    title = stringResource(R.string.navigation_drawer_dropdown_pinned_unified_header),
                )
            }
            items(
                items = pinnedFolders,
                key = { "$PINNED_FOLDER_KEY_PREFIX${it.displayFolder?.folderId ?: ""}" },
            ) { folder ->
                FolderListItem(
                    displayFolder = requireNotNull(folder.displayFolder) {
                        "Null DisplayFolder for folder ${folder.displayName}"
                    },
                    treeFolder = folder,
                    showStarredCount = showStarredCount,
                    onClick = onFolderClick,
                    folderNameFormatter = folderNameFormatter,
                    selectedFolderId = selectedFolderId,
                    isExpandInitial = isExpandedInitial,
                )
            }
            item(key = PINNED_DIVIDER_KEY) {
                DividerHorizontal()
            }
            if (accountSectionTitle != null) {
                item(key = ACCOUNT_SECTION_HEADER_KEY) {
                    FolderListSectionHeader(title = accountSectionTitle)
                }
            }
        }
        items(
            items = rootFolder.children,
            key = { it.displayFolder?.folderId ?: '0' },
        ) { folder ->
            val currentDisplayFolder = folder.displayFolder
            FolderListItem(
                displayFolder = requireNotNull(currentDisplayFolder) {
                    "Null DisplayFolder for folder ${folder.displayName}"
                },
                treeFolder = folder,
                showStarredCount = showStarredCount,
                onClick = onFolderClick,
                folderNameFormatter = folderNameFormatter,
                selectedFolderId = selectedFolderId,
                isExpandInitial = isExpandedInitial,
                folderIdToReveal = folderIdToReveal,
                onFolderReveal = { folderIdToReveal = null },
            )
        }
    }
}

@Composable
private fun FolderListSectionHeader(title: String) {
    TextLabelSmall(
        text = title,
        color = BoltTheme.colors.onSurfaceVariant,
        overflow = TextOverflow.Ellipsis,
        maxLines = 1,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = BoltTheme.spacings.double,
                end = BoltTheme.spacings.double,
                top = BoltTheme.spacings.default,
                bottom = BoltTheme.spacings.half,
            ),
    )
}
