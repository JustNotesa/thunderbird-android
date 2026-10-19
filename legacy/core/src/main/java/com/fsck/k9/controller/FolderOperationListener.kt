package com.fsck.k9.controller

/**
 * Notified when creating or deleting a folder on the server has ended.
 */
fun interface FolderOperationListener {
    /**
     * @param error Why the operation has failed, or `null` if it has succeeded.
     */
    fun onFinished(error: String?)
}
