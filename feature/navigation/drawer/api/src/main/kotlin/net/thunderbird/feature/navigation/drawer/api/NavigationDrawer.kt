package net.thunderbird.feature.navigation.drawer.api

import androidx.appcompat.app.AppCompatActivity
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.mail.folder.FolderType

interface NavigationDrawer {
    val parent: AppCompatActivity
    val isOpen: Boolean

    fun selectAccount(accountId: AccountId)

    fun selectFolder(accountId: AccountId, folderId: Long)

    fun selectUnifiedInbox()

    /**
     * Selects the unified folder of the given special folder [folderType].
     */
    fun selectUnifiedFolder(folderType: FolderType)

    fun deselect()

    fun open()

    fun close()

    fun lock()

    fun unlock()
}
