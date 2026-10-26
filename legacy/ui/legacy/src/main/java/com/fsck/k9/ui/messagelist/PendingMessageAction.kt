package com.fsck.k9.ui.messagelist

import app.k9mail.legacy.message.controller.MessageReference

/**
 * Holds an action that removes messages from the message list and is waiting for the user to decide whether to undo
 * it.
 *
 * The action can be a deletion, an archive, a move or marking messages as spam.
 *
 * Only one action can be pending at a time. Every action gets an ID that tells the pending action apart from older
 * ones that have already been committed or undone.
 */
internal class PendingMessageAction {
    private var nextId = FIRST_ID
    private var pendingAction: PendingAction? = null

    /**
     * Make a new action pending, carrying out an already pending action first.
     *
     * @return the ID of the new pending action
     */
    fun start(messages: List<MessageReference>, perform: () -> Unit): Long {
        commitPending()

        val id = nextId
        nextId++
        pendingAction = PendingAction(id, messages, perform)

        return id
    }

    /**
     * Carry out the pending action with the given ID.
     *
     * Does nothing if there is no pending action or if the pending action has a different ID.
     */
    fun commit(id: Long) {
        val action = pendingAction
        if (action != null && action.id == id) {
            pendingAction = null
            action.perform()
        }
    }

    /**
     * Carry out the pending action, if there is one.
     */
    fun commitPending() {
        val action = pendingAction ?: return
        pendingAction = null
        action.perform()
    }

    /**
     * Discard the pending action with the given ID without carrying it out.
     *
     * @return true if the pending action was discarded
     */
    fun undo(id: Long): Boolean {
        val action = pendingAction
        if (action != null && action.id == id) {
            pendingAction = null
            return true
        }

        return false
    }

    /**
     * Check whether the given message is part of the pending action.
     */
    fun isPending(messageReference: MessageReference): Boolean {
        return pendingAction?.messages?.contains(messageReference) == true
    }

    /**
     * The messages of the pending action. Empty if there's no pending action.
     */
    val pendingMessages: List<MessageReference>
        get() = pendingAction?.messages ?: emptyList()

    /**
     * Whether an action is waiting to be committed or undone.
     */
    val hasPending: Boolean
        get() = pendingAction != null

    private data class PendingAction(
        val id: Long,
        val messages: List<MessageReference>,
        val perform: () -> Unit,
    )

    private companion object {
        const val FIRST_ID = 1L
    }
}
