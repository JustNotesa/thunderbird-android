package net.thunderbird.feature.navigation.drawer.dropdown.domain.entity

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.UnifiedAccountId
import net.thunderbird.feature.mail.folder.api.FolderPathDelimiter

internal data class UnifiedDisplayFolder(
    override val folderId: String,
    val unifiedType: UnifiedDisplayFolderType,
    override val unreadMessageCount: Int,
    override val starredMessageCount: Int,
    val children: ImmutableList<UnifiedAccountFolder> = persistentListOf(),
) : DisplayFolder {
    override val accountId: AccountId = UnifiedAccountId
    override val pathDelimiter: FolderPathDelimiter = "/"
}
