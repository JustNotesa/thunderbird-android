package com.fsck.k9.ui.compose

/**
 * Lets only one draft be written to the server at a time.
 *
 * The id of a new draft is only known once its save is over. A second save that starts before that would not replace
 * the first one but create another draft. So a request that arrives while a save is running is remembered and is
 * answered with [Pending] when the running save is over; the caller then saves again, with everything that changed
 * meanwhile.
 */
class DraftSaveGate {
    private var isSaving = false
    private var pending = Pending.NONE

    /**
     * Whether a save has been handed over and its result is not known yet.
     */
    fun isSaving(): Boolean = isSaving

    /**
     * Asks to save now. Returns `false` if a save is running; the request is then kept and handed back by
     * [onSaveFinished]. An explicit request (the user wants the draft saved, perhaps to leave the screen) is not
     * downgraded by a later implicit one.
     */
    fun request(explicit: Boolean): Boolean {
        if (!isSaving) return true

        if (explicit || pending == Pending.NONE) {
            pending = if (explicit) Pending.EXPLICIT else Pending.IMPLICIT
        }
        return false
    }

    fun onSaveStarted() {
        isSaving = true
    }

    /**
     * To be called when the result of the running save (an id or a failure) is known.
     */
    fun onSaveFinished(): Pending {
        isSaving = false
        return pending.also { pending = Pending.NONE }
    }

    enum class Pending { NONE, IMPLICIT, EXPLICIT }
}
