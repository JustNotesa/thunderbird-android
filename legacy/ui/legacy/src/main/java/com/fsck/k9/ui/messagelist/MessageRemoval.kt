package com.fsck.k9.ui.messagelist

import androidx.annotation.PluralsRes
import com.fsck.k9.ui.R

/**
 * An action that removes messages from the message list and can be undone for a few seconds.
 */
enum class MessageRemoval(@PluralsRes val undoText: Int) {
    DELETE(R.plurals.message_list_undo_deleted),
    ARCHIVE(R.plurals.message_list_undo_archived),
    MOVE(R.plurals.message_list_undo_moved),
    SPAM(R.plurals.message_list_undo_spam),
}
