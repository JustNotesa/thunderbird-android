package app.k9mail.feature.migration.qrcode.transfer.auth

import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Checks the password the user entered against the passwords of the accounts of this device.
 *
 * Wrong passwords are counted for all accounts together, so guessing doesn't become easier by trying another account,
 * and the counts are kept over restarts of the app. Every lock out lasts twice as long as the one before it, so a
 * password can't be guessed by waiting.
 *
 * The passwords are never logged and the entered one is wiped as soon as it was checked.
 */
internal class AccountPasswordVerifier(
    private val accountSource: PasswordAccountSource,
    private val attemptStore: TransferAuthAttemptStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /**
     * Returns the accounts of this device whose incoming server has a password.
     */
    suspend fun passwordAccounts(): List<PasswordAccount> {
        return accountSource.getAll()
            .filter { (_, password) -> !password.isNullOrBlank() }
            .map { (account, _) -> account }
    }

    /**
     * Checks the [password] entered for the account [accountId]. The [password] is wiped before this returns.
     */
    suspend fun verify(accountId: String, password: CharArray): VerifyResult {
        try {
            return checkPassword(accountId, password)
        } finally {
            password.fill(NO_CHAR)
        }
    }

    private suspend fun checkPassword(accountId: String, password: CharArray): VerifyResult {
        val now = clock()
        val attempts = attemptStore.load()
        if (attempts.lockedOutUntilMillis > now) {
            return VerifyResult.LockedOut(untilMillis = attempts.lockedOutUntilMillis)
        }

        val storedPassword = accountSource.getAll()
            .firstOrNull { (account, _) -> account.id == accountId }
            ?.second

        return if (matches(storedPassword = storedPassword, enteredPassword = password)) {
            onCorrectPassword()
        } else {
            onWrongPassword(attempts = attempts, now = now)
        }
    }

    private fun matches(storedPassword: String?, enteredPassword: CharArray): Boolean {
        if (storedPassword.isNullOrEmpty()) {
            return false
        }

        val storedBytes = CharBuffer.wrap(storedPassword).toUtf8Bytes()
        val enteredBytes = CharBuffer.wrap(enteredPassword).toUtf8Bytes()
        try {
            return MessageDigest.isEqual(storedBytes, enteredBytes)
        } finally {
            storedBytes.fill(0)
            enteredBytes.fill(0)
        }
    }

    private fun onCorrectPassword(): VerifyResult {
        attemptStore.save(TransferAuthAttempts())

        return VerifyResult.Correct
    }

    private fun onWrongPassword(attempts: TransferAuthAttempts, now: Long): VerifyResult {
        val failedAttempts = attempts.failedAttempts + 1
        if (failedAttempts < MAX_FAILED_ATTEMPTS) {
            attemptStore.save(attempts.copy(failedAttempts = failedAttempts, lockedOutUntilMillis = NO_LOCK_OUT))

            return VerifyResult.Wrong(remainingAttempts = MAX_FAILED_ATTEMPTS - failedAttempts)
        }

        val lockedUntil = now + lockOutMillis(escalationLevel = attempts.escalationLevel)
        attemptStore.save(
            TransferAuthAttempts(
                failedAttempts = 0,
                escalationLevel = attempts.escalationLevel + 1,
                lockedOutUntilMillis = lockedUntil,
            ),
        )

        return VerifyResult.LockedOut(untilMillis = lockedUntil)
    }

    /** Every lock out doubles the time the next one holds the accounts back, at most [MAX_LOCK_OUT_MILLIS]. */
    private fun lockOutMillis(escalationLevel: Int): Long {
        val millis = BASE_LOCK_OUT_MILLIS * (1L shl minOf(escalationLevel, MAX_ESCALATION_LEVEL))

        return minOf(millis, MAX_LOCK_OUT_MILLIS)
    }

    private fun CharBuffer.toUtf8Bytes(): ByteArray {
        val byteBuffer = StandardCharsets.UTF_8.encode(this)
        return ByteArray(byteBuffer.remaining()).also(byteBuffer::get)
    }

    private companion object {
        private const val MAX_FAILED_ATTEMPTS = 5
        private const val BASE_LOCK_OUT_MILLIS = 30_000L
        private const val MAX_LOCK_OUT_MILLIS = 3_600_000L
        private const val MAX_ESCALATION_LEVEL = 10
        private const val NO_LOCK_OUT = 0L
        private const val NO_CHAR = '\u0000'
    }
}

/**
 * Result of checking the password entered for an account.
 */
internal sealed interface VerifyResult {
    /**
     * The password of the account was correct.
     */
    data object Correct : VerifyResult

    /**
     * The password was wrong and there are [remainingAttempts] attempts left before the accounts are locked out.
     */
    data class Wrong(val remainingAttempts: Int) : VerifyResult

    /**
     * The accounts are locked out until [untilMillis], no matter which password is entered.
     */
    data class LockedOut(val untilMillis: Long) : VerifyResult
}
