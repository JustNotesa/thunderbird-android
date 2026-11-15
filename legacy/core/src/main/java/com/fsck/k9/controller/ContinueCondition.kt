package com.fsck.k9.controller

/**
 * Asked before every step of a long download, e.g. whether the device still has enough free storage.
 */
fun interface ContinueCondition {
    fun shouldContinue(): Boolean
}
