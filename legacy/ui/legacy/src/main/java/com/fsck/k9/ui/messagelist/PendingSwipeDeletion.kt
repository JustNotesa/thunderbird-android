package com.fsck.k9.ui.messagelist

import app.k9mail.legacy.message.controller.MessageReference

/**
 * Holds the deletion of a swiped message that is waiting for the user to decide whether to undo it.
 *
 * Only one deletion can be pending at a time. Every deletion gets an ID that tells the pending deletion apart from
 * older ones that have already been committed or undone.
 */
internal class PendingSwipeDeletion(
    private val deleteMessages: (messages: List<MessageReference>, threaded: Boolean) -> Unit,
) {
    private var nextId = FIRST_ID
    private var pendingDeletion: PendingDeletion? = null

    /**
     * Make a new deletion pending, carrying out an already pending deletion first.
     *
     * @return the ID of the new pending deletion
     */
    fun start(messages: List<MessageReference>, threaded: Boolean): Long {
        commitPending()

        val id = nextId
        nextId++
        pendingDeletion = PendingDeletion(id, messages, threaded)

        return id
    }

    /**
     * Delete the messages of the pending deletion with the given ID.
     *
     * Does nothing if there is no pending deletion or if the pending deletion has a different ID.
     */
    fun commit(id: Long) {
        val deletion = pendingDeletion
        if (deletion != null && deletion.id == id) {
            pendingDeletion = null
            deleteMessages(deletion.messages, deletion.threaded)
        }
    }

    /**
     * Delete the messages of the pending deletion, if there is one.
     */
    fun commitPending() {
        val deletion = pendingDeletion ?: return
        pendingDeletion = null
        deleteMessages(deletion.messages, deletion.threaded)
    }

    /**
     * Discard the pending deletion with the given ID without deleting the messages.
     *
     * @return true if the pending deletion was discarded
     */
    fun undo(id: Long): Boolean {
        val deletion = pendingDeletion
        if (deletion != null && deletion.id == id) {
            pendingDeletion = null
            return true
        }

        return false
    }

    /**
     * Check whether the given message is part of the pending deletion.
     */
    fun isPending(messageReference: MessageReference): Boolean {
        return pendingDeletion?.messages?.contains(messageReference) == true
    }

    /**
     * Whether a deletion is waiting to be committed or undone.
     */
    val hasPending: Boolean
        get() = pendingDeletion != null

    private data class PendingDeletion(
        val id: Long,
        val messages: List<MessageReference>,
        val threaded: Boolean,
    )

    private companion object {
        const val FIRST_ID = 1L
    }
}
