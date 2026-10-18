package app.k9mail.feature.migration.qrcode.transfer.auth

import android.content.Context

/**
 * Keeps the attempts to authorize a transfer in a private file, so they survive a restart of the app. It holds no
 * credentials, only counters and a point in time.
 */
internal class AndroidTransferAuthAttemptStore(
    context: Context,
) : TransferAuthAttemptStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun load(): TransferAuthAttempts {
        return TransferAuthAttempts(
            failedAttempts = preferences.getInt(KEY_FAILED_ATTEMPTS, 0),
            escalationLevel = preferences.getInt(KEY_ESCALATION_LEVEL, 0),
            lockedOutUntilMillis = preferences.getLong(KEY_LOCKED_OUT_UNTIL, 0L),
        )
    }

    override fun save(attempts: TransferAuthAttempts) {
        // Written synchronously: a failed attempt must be stored even if the app is killed right afterwards.
        preferences.edit()
            .putInt(KEY_FAILED_ATTEMPTS, attempts.failedAttempts)
            .putInt(KEY_ESCALATION_LEVEL, attempts.escalationLevel)
            .putLong(KEY_LOCKED_OUT_UNTIL, attempts.lockedOutUntilMillis)
            .commit()
    }

    private companion object {
        private const val PREFERENCES_NAME = "migration_qrcode_transfer_auth"
        private const val KEY_FAILED_ATTEMPTS = "failedAttempts"
        private const val KEY_ESCALATION_LEVEL = "escalationLevel"
        private const val KEY_LOCKED_OUT_UNTIL = "lockedOutUntilMillis"
    }
}
