package com.fsck.k9.ui.managefolders

import net.thunderbird.feature.account.avatar.Avatar

/**
 * An account the folders of which can be managed.
 */
internal data class FolderManagementAccount(
    val uuid: String,
    val name: String,
    val email: String,
    val color: Int,
    val avatar: Avatar,
)
