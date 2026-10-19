package app.k9mail.feature.settings.import

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.fsck.k9.preferences.AccountDescription
import kotlin.test.Test

private const val EMAIL = "user@example.com"
private const val HOST = "imap.example.com"
private const val USERNAME = "user@example.com"

class ExistingAccountMatcherTest {
    private val existingAccountProvider = FakeExistingAccountProvider()
    private val testSubject = ExistingAccountMatcher(existingAccountProvider)

    @Test
    fun `account with same email, host and username should be already set up`() {
        existingAccountProvider.accounts = listOf(existingAccount())

        assertThat(testSubject.isAlreadySetUp(importedAccount())).isTrue()
    }

    @Test
    fun `account with different email should not be already set up`() {
        existingAccountProvider.accounts = listOf(existingAccount())

        assertThat(testSubject.isAlreadySetUp(importedAccount(email = "other@example.com"))).isFalse()
    }

    @Test
    fun `account with different incoming server host should not be already set up`() {
        existingAccountProvider.accounts = listOf(existingAccount())

        assertThat(testSubject.isAlreadySetUp(importedAccount(host = "imap.other.example.com"))).isFalse()
    }

    @Test
    fun `account with different incoming server username should not be already set up`() {
        existingAccountProvider.accounts = listOf(existingAccount())

        assertThat(testSubject.isAlreadySetUp(importedAccount(username = "other@example.com"))).isFalse()
    }

    @Test
    fun `email and host comparison should be case-insensitive`() {
        existingAccountProvider.accounts = listOf(
            existingAccount(email = "User@Example.com", host = "IMAP.example.com"),
        )

        assertThat(
            testSubject.isAlreadySetUp(importedAccount(email = "user@example.COM", host = "imap.EXAMPLE.com")),
        ).isTrue()
    }

    @Test
    fun `imported account without email address should not be already set up`() {
        existingAccountProvider.accounts = listOf(existingAccount())

        assertThat(testSubject.isAlreadySetUp(importedAccount(email = null))).isFalse()
    }

    @Test
    fun `imported account without incoming server host should not be already set up`() {
        existingAccountProvider.accounts = listOf(existingAccount())

        assertThat(testSubject.isAlreadySetUp(importedAccount(host = null))).isFalse()
    }

    @Test
    fun `account should not be already set up when no accounts exist`() {
        existingAccountProvider.accounts = emptyList()

        assertThat(testSubject.isAlreadySetUp(importedAccount())).isFalse()
    }
}

private fun existingAccount(email: String? = EMAIL, host: String? = HOST, username: String? = USERNAME) =
    ExistingAccount(
        email = email,
        incomingServerHost = host,
        incomingServerUsername = username,
    )

private fun importedAccount(email: String? = EMAIL, host: String? = HOST, username: String? = USERNAME) =
    AccountDescription(
        name = "Account",
        uuid = "uuid",
        email = email,
        incomingServerHost = host,
        incomingServerUsername = username,
    )

private class FakeExistingAccountProvider : ExistingAccountProvider {
    var accounts: List<ExistingAccount> = emptyList()

    override fun getExistingAccounts(): List<ExistingAccount> = accounts
}
