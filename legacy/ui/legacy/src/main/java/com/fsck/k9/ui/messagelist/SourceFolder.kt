package com.fsck.k9.ui.messagelist

import app.k9mail.legacy.message.controller.MessageReference

/**
 * The folder all of the messages are in, or null when they are in different folders or accounts.
 *
 * A list that shows the messages of several folders (e.g. the unified inbox) doesn't belong to a folder, but the
 * selected messages might still all be in the same one, which is the folder they are moved or copied from.
 */
internal fun commonFolderId(messages: List<MessageReference>): Long? {
    val first = messages.firstOrNull() ?: return null

    return first.folderId.takeIf { messages.all { it.folderId == first.folderId && it.accountId == first.accountId } }
}
