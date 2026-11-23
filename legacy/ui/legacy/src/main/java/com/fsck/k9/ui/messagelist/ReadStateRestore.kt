package com.fsck.k9.ui.messagelist

import net.thunderbird.feature.account.AccountId

/**
 * A message (or a whole thread) whose read state was changed by the user, together with the state it had before.
 */
data class ReadStateChange(
    val accountId: AccountId,
    /** Database ID of the message, or of the thread root if [isThread] is set. */
    val id: Long,
    val isThread: Boolean,
    val wasRead: Boolean,
)

/**
 * One call that sets the read state of several messages or threads of an account.
 */
internal data class ReadStateRestoreAction(
    val accountId: AccountId,
    val ids: List<Long>,
    val isThread: Boolean,
    val read: Boolean,
)

/**
 * Work out which calls bring every message back to its previous read state after marking them as [markedRead].
 *
 * Messages whose state didn't change aren't touched. The others are grouped by account, message/thread and previous
 * state, so a mixed selection is restored exactly.
 */
internal fun restoreReadStateActions(
    changes: List<ReadStateChange>,
    markedRead: Boolean,
): List<ReadStateRestoreAction> {
    return changes
        .filter { it.wasRead != markedRead }
        .groupBy { Triple(it.accountId, it.isThread, it.wasRead) }
        .map { (key, group) ->
            ReadStateRestoreAction(
                accountId = key.first,
                ids = group.map { it.id },
                isThread = key.second,
                read = key.third,
            )
        }
}
