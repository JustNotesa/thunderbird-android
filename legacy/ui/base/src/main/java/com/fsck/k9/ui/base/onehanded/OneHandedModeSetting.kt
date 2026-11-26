package com.fsck.k9.ui.base.onehanded

/**
 * Tells whether the one-handed mode is switched on, i.e. whether screens put their toolbar at the bottom edge.
 */
fun interface OneHandedModeSetting {
    fun isEnabled(): Boolean
}
