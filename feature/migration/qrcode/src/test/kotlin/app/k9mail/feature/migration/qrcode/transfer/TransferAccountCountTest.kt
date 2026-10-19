package app.k9mail.feature.migration.qrcode.transfer

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.BeforeTest
import kotlin.test.Test
import net.thunderbird.core.logging.testing.TestLogger
import net.thunderbird.legacy.logging.Log

private const val INCOMING_SERVER = """[0,"imap.domain.example",993,3,2,"username","My Account"]"""
private const val OUTGOING_SERVERS = """[[[0,"smtp.domain.example",587,2,1,"username"],""" +
    """["user@domain.example","Firstname Lastname"]]]"""

class TransferAccountCountTest {

    @BeforeTest
    fun setUp() {
        Log.logger = TestLogger()
    }

    @Test
    fun `the accounts of a payload are counted`() {
        val accountsJson = payload(accounts = 3)

        assertThat(countTransferAccounts(accountsJson)).isEqualTo(3)
    }

    @Test
    fun `a payload of a single account is counted`() {
        val accountsJson = payload(accounts = 1)

        assertThat(countTransferAccounts(accountsJson)).isEqualTo(1)
    }

    @Test
    fun `a payload without accounts has none`() {
        val accountsJson = """[1,[1,1]]"""

        assertThat(countTransferAccounts(accountsJson)).isEqualTo(0)
    }

    @Test
    fun `a payload that is cut off has no accounts`() {
        val accountsJson = """[1"""

        assertThat(countTransferAccounts(accountsJson)).isEqualTo(0)
    }

    @Test
    fun `text that isn't a payload has no accounts`() {
        val accountsJson = "The password of my mail account is …"

        assertThat(countTransferAccounts(accountsJson)).isEqualTo(0)
    }
}

private fun payload(accounts: Int): String {
    val account = "$INCOMING_SERVER,$OUTGOING_SERVERS"

    return "[1,[1,1]," + List(accounts) { account }.joinToString(separator = ",") + "]"
}
