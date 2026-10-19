package app.k9mail.feature.migration.qrcode.transfer.auth

/**
 * Keeps what was tried so far to authorize a transfer, so a wrong password can't be guessed over and over again, not
 * even by restarting the app.
 */
internal interface TransferAuthAttemptStore {
    /**
     * The attempts that were made so far, empty when nothing was tried yet.
     */
    fun load(): TransferAuthAttempts

    /**
     * Keeps the [attempts], so the next attempt counts on them.
     */
    fun save(attempts: TransferAuthAttempts)
}

/**
 * What was tried so far to authorize a transfer with the password of an account of this device.
 *
 * @param failedAttempts How many wrong passwords were entered since the last lock out.
 * @param escalationLevel How many lock outs there were, every one of them lasting twice as long as the one before.
 * @param lockedOutUntilMillis When the next attempt is possible, `0` while the attempts aren't locked out.
 */
internal data class TransferAuthAttempts(
    val failedAttempts: Int = 0,
    val escalationLevel: Int = 0,
    val lockedOutUntilMillis: Long = 0L,
)
