package app.k9mail.feature.migration.qrcode.transfer.auth

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class AccountPasswordVerifierTest {
    private var now = NOW
    private val account = passwordAccount()
    private val attemptStore = FakeTransferAuthAttemptStore()

    @Test
    fun `the password of an account is accepted`() = runTest {
        val testSubject = createVerifier()

        val result = testSubject.verify(accountId = account.id, password = PASSWORD.toCharArray())

        assertThat(result).isEqualTo(VerifyResult.Correct)
    }

    @Test
    fun `a wrong password counts down the remaining attempts`() = runTest {
        val testSubject = createVerifier()

        val firstResult = testSubject.verify(accountId = account.id, password = WRONG.toCharArray())
        val secondResult = testSubject.verify(accountId = account.id, password = WRONG.toCharArray())

        assertThat(firstResult).isEqualTo(VerifyResult.Wrong(remainingAttempts = 4))
        assertThat(secondResult).isEqualTo(VerifyResult.Wrong(remainingAttempts = 3))
    }

    @Test
    fun `five wrong passwords lock the transfer out`() = runTest {
        val testSubject = createVerifier()

        val result = lockOut(testSubject)

        assertThat(result).isEqualTo(VerifyResult.LockedOut(untilMillis = NOW + FIRST_LOCK_OUT_MILLIS))
    }

    @Test
    fun `the wrong passwords of all accounts are counted together`() = runTest {
        val otherAccount = passwordAccount(id = "account-2", displayName = "other@domain.example")
        val testSubject = createVerifier(accounts = listOf(account to PASSWORD, otherAccount to OTHER_PASSWORD))

        testSubject.verify(accountId = account.id, password = WRONG.toCharArray())
        val result = testSubject.verify(accountId = otherAccount.id, password = WRONG.toCharArray())

        assertThat(result).isEqualTo(VerifyResult.Wrong(remainingAttempts = 3))
    }

    @Test
    fun `a wrong password of an account locks every account out`() = runTest {
        val otherAccount = passwordAccount(id = "account-2", displayName = "other@domain.example")
        val testSubject = createVerifier(accounts = listOf(account to PASSWORD, otherAccount to OTHER_PASSWORD))

        repeat(4) {
            testSubject.verify(accountId = account.id, password = WRONG.toCharArray())
        }
        testSubject.verify(accountId = otherAccount.id, password = WRONG.toCharArray())
        val result = testSubject.verify(accountId = otherAccount.id, password = OTHER_PASSWORD.toCharArray())

        assertThat(result).isEqualTo(VerifyResult.LockedOut(untilMillis = NOW + FIRST_LOCK_OUT_MILLIS))
    }

    @Test
    fun `the correct password is refused while the transfer is locked out`() = runTest {
        val testSubject = createVerifier()

        lockOut(testSubject)
        val lockedResult = testSubject.verify(accountId = account.id, password = PASSWORD.toCharArray())

        assertThat(lockedResult).isEqualTo(VerifyResult.LockedOut(untilMillis = NOW + FIRST_LOCK_OUT_MILLIS))

        now += FIRST_LOCK_OUT_MILLIS
        val resultAfterLockOut = testSubject.verify(accountId = account.id, password = PASSWORD.toCharArray())

        assertThat(resultAfterLockOut).isEqualTo(VerifyResult.Correct)
    }

    @Test
    fun `a correct password resets the failed attempts`() = runTest {
        val testSubject = createVerifier()

        testSubject.verify(accountId = account.id, password = WRONG.toCharArray())
        testSubject.verify(accountId = account.id, password = WRONG.toCharArray())
        val correctResult = testSubject.verify(accountId = account.id, password = PASSWORD.toCharArray())
        val result = testSubject.verify(accountId = account.id, password = WRONG.toCharArray())

        assertThat(correctResult).isEqualTo(VerifyResult.Correct)
        assertThat(result).isEqualTo(VerifyResult.Wrong(remainingAttempts = 4))
    }

    @Test
    fun `every lock out lasts twice as long as the one before`() = runTest {
        val testSubject = createVerifier()

        val firstResult = lockOut(testSubject)
        now += FIRST_LOCK_OUT_MILLIS
        val secondResult = lockOut(testSubject)

        assertThat(firstResult).isEqualTo(VerifyResult.LockedOut(untilMillis = NOW + FIRST_LOCK_OUT_MILLIS))
        assertThat(secondResult).isEqualTo(VerifyResult.LockedOut(untilMillis = now + 2 * FIRST_LOCK_OUT_MILLIS))
    }

    @Test
    fun `a correct password resets the time of the next lock out`() = runTest {
        val testSubject = createVerifier()

        lockOut(testSubject)
        now += FIRST_LOCK_OUT_MILLIS
        val correctResult = testSubject.verify(accountId = account.id, password = PASSWORD.toCharArray())
        now += FIRST_LOCK_OUT_MILLIS
        val result = lockOut(testSubject)

        assertThat(correctResult).isEqualTo(VerifyResult.Correct)
        assertThat(result).isEqualTo(VerifyResult.LockedOut(untilMillis = now + FIRST_LOCK_OUT_MILLIS))
    }

    @Test
    fun `a lock out doesn't last longer than an hour`() = runTest {
        val testSubject = createVerifier()

        var result = lockOut(testSubject)
        repeat(LOCK_OUTS_UNTIL_CAPPED) {
            now += ONE_HOUR
            result = lockOut(testSubject)
        }

        assertThat(result).isEqualTo(VerifyResult.LockedOut(untilMillis = now + ONE_HOUR))
    }

    @Test
    fun `the lock out is kept when the app is started again`() = runTest {
        lockOut(createVerifier())

        val testSubject = createVerifier()
        val result = testSubject.verify(accountId = account.id, password = PASSWORD.toCharArray())

        assertThat(result).isEqualTo(VerifyResult.LockedOut(untilMillis = NOW + FIRST_LOCK_OUT_MILLIS))
    }

    @Test
    fun `only accounts with a password are offered`() = runTest {
        val oauthAccount = passwordAccount(id = "account-2", displayName = "oauth@domain.example")
        val blankPasswordAccount = passwordAccount(id = "account-3", displayName = "blank@domain.example")
        val accounts = listOf(account to PASSWORD, oauthAccount to null, blankPasswordAccount to " ")
        val testSubject = createVerifier(accounts = accounts)

        val result = testSubject.passwordAccounts()

        assertThat(result).isEqualTo(listOf(account))
    }

    @Test
    fun `an account without a password is never verified`() = runTest {
        val oauthAccount = passwordAccount(id = "account-2", displayName = "oauth@domain.example")
        val testSubject = createVerifier(accounts = listOf(account to PASSWORD, oauthAccount to null))

        val result = testSubject.verify(accountId = oauthAccount.id, password = "".toCharArray())

        assertThat(result).isEqualTo(VerifyResult.Wrong(remainingAttempts = 4))
    }

    @Test
    fun `the entered password is wiped after checking`() = runTest {
        val testSubject = createVerifier()
        val password = PASSWORD.toCharArray()

        testSubject.verify(accountId = account.id, password = password)

        assertWiped(password)
    }

    @Test
    fun `the entered password is wiped when it is wrong`() = runTest {
        val testSubject = createVerifier()
        val password = WRONG.toCharArray()

        testSubject.verify(accountId = account.id, password = password)

        assertWiped(password)
    }

    @Test
    fun `the entered password is wiped while the transfer is locked out`() = runTest {
        val testSubject = createVerifier()

        lockOut(testSubject)
        val password = PASSWORD.toCharArray()

        testSubject.verify(accountId = account.id, password = password)

        assertWiped(password)
    }

    private fun createVerifier(
        accounts: List<Pair<PasswordAccount, String?>> = listOf(account to PASSWORD),
        attemptStore: TransferAuthAttemptStore = this.attemptStore,
    ): AccountPasswordVerifier {
        return AccountPasswordVerifier(
            accountSource = FakePasswordAccountSource(accounts),
            attemptStore = attemptStore,
            clock = { now },
        )
    }

    /** Fills up the attempts with wrong passwords and reports what the attempt after the lock out answers. */
    private suspend fun lockOut(testSubject: AccountPasswordVerifier): VerifyResult {
        repeat(MAX_FAILED_ATTEMPTS) {
            testSubject.verify(accountId = account.id, password = WRONG.toCharArray())
        }

        return testSubject.verify(accountId = account.id, password = WRONG.toCharArray())
    }

    private fun assertWiped(password: CharArray) {
        assertThat(password.contentEquals(CharArray(password.size))).isTrue()
    }

    private companion object {
        private const val NOW = 1_792_300_000_000L
        private const val FIRST_LOCK_OUT_MILLIS = 30_000L
        private const val ONE_HOUR = 3_600_000L
        private const val MAX_FAILED_ATTEMPTS = 5
        private const val LOCK_OUTS_UNTIL_CAPPED = 8
        private const val PASSWORD = "imap-password"
        private const val OTHER_PASSWORD = "smtp-password"
        private const val WRONG = "wrong"
    }
}
