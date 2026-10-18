package app.k9mail.feature.account.setup.domain.discovery

import app.k9mail.autodiscovery.api.AuthenticationType
import app.k9mail.autodiscovery.api.AutoDiscoveryResult.NoUsableSettingsFound
import app.k9mail.autodiscovery.api.AutoDiscoveryResult.Settings
import app.k9mail.autodiscovery.api.ConnectionSecurity
import app.k9mail.autodiscovery.api.ImapServerSettings
import app.k9mail.autodiscovery.api.SmtpServerSettings
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.fsck.k9.mail.AuthType
import com.fsck.k9.mail.ServerSettings
import kotlinx.coroutines.test.runTest
import net.thunderbird.core.common.mail.toEmailAddressOrThrow
import net.thunderbird.core.common.net.toHostname
import net.thunderbird.core.common.net.toPort
import org.junit.Test
import com.fsck.k9.mail.ConnectionSecurity as MailConnectionSecurity

class ExistingAccountAutoDiscoveryTest {

    @Test
    fun `should reuse settings of existing account with same domain`() = runTest {
        val existingAccount = createExistingAccount(emailAddress = "user@Example.COM")
        val testSubject = createTestSubject(existingAccount)

        val result = testSubject.initDiscovery(NEW_EMAIL_ADDRESS.toEmailAddressOrThrow()).single().run()

        assertThat(result).isEqualTo(createExpectedSettings(username = NEW_EMAIL_ADDRESS))
    }

    @Test
    fun `should use new local part as username when existing username is local part`() = runTest {
        val existingAccount = createExistingAccount(
            incoming = createServerSettings(type = "imap", port = 993, username = "user"),
            outgoing = createServerSettings(type = "smtp", port = 465, username = "user"),
        )
        val testSubject = createTestSubject(existingAccount)

        val result = testSubject.initDiscovery(NEW_EMAIL_ADDRESS.toEmailAddressOrThrow()).single().run()

        assertThat(result).isEqualTo(createExpectedSettings(username = NEW_LOCAL_PART))
    }

    @Test
    fun `should use new email address as username when existing username is unrelated`() = runTest {
        val existingAccount = createExistingAccount(
            incoming = createServerSettings(type = "imap", port = 993, username = "alice.smith"),
            outgoing = createServerSettings(type = "smtp", port = 465, username = "alice.smith"),
        )
        val testSubject = createTestSubject(existingAccount)

        val result = testSubject.initDiscovery(NEW_EMAIL_ADDRESS.toEmailAddressOrThrow()).single().run()

        assertThat(result).isEqualTo(createExpectedSettings(username = NEW_EMAIL_ADDRESS))
    }

    @Test
    fun `should return NoUsableSettingsFound when no existing account of same domain`() = runTest {
        val existingAccount = createExistingAccount(emailAddress = "user@other.example.org")
        val testSubject = createTestSubject(existingAccount)

        val result = testSubject.initDiscovery(NEW_EMAIL_ADDRESS.toEmailAddressOrThrow()).single().run()

        assertThat(result).isEqualTo(NoUsableSettingsFound)
    }

    @Test
    fun `should return NoUsableSettingsFound when there are no existing accounts`() = runTest {
        val testSubject = createTestSubject()

        val result = testSubject.initDiscovery(NEW_EMAIL_ADDRESS.toEmailAddressOrThrow()).single().run()

        assertThat(result).isEqualTo(NoUsableSettingsFound)
    }

    @Test
    fun `should skip existing account using unencrypted connection`() = runTest {
        val unencryptedAccount = createExistingAccount(
            incoming = createServerSettings(
                type = "imap",
                port = 143,
                username = "user@example.com",
                connectionSecurity = MailConnectionSecurity.NONE,
            ),
        )
        val testSubject = createTestSubject(unencryptedAccount, createExistingAccount())

        val result = testSubject.initDiscovery(NEW_EMAIL_ADDRESS.toEmailAddressOrThrow()).single().run()

        assertThat(result).isEqualTo(createExpectedSettings(username = NEW_EMAIL_ADDRESS))
    }

    @Test
    fun `should skip existing account using POP3`() = runTest {
        val pop3Account = createExistingAccount(
            incoming = createServerSettings(
                type = "pop3",
                port = 995,
                username = "user@example.com",
            ),
        )
        val testSubject = createTestSubject(pop3Account, createExistingAccount())

        val result = testSubject.initDiscovery(NEW_EMAIL_ADDRESS.toEmailAddressOrThrow()).single().run()

        assertThat(result).isEqualTo(createExpectedSettings(username = NEW_EMAIL_ADDRESS))
    }

    @Test
    fun `should skip existing account using unsupported authentication`() = runTest {
        val externalAuthAccount = createExistingAccount(
            incoming = createServerSettings(
                type = "imap",
                port = 993,
                username = "user@example.com",
                authType = AuthType.EXTERNAL,
            ),
        )
        val testSubject = createTestSubject(externalAuthAccount, createExistingAccount())

        val result = testSubject.initDiscovery(NEW_EMAIL_ADDRESS.toEmailAddressOrThrow()).single().run()

        assertThat(result).isEqualTo(createExpectedSettings(username = NEW_EMAIL_ADDRESS))
    }

    @Test
    fun `should return NoUsableSettingsFound when existing account uses unencrypted connection only`() = runTest {
        val unencryptedAccount = createExistingAccount(
            incoming = createServerSettings(
                type = "imap",
                port = 143,
                username = "user@example.com",
                connectionSecurity = MailConnectionSecurity.NONE,
            ),
        )
        val testSubject = createTestSubject(unencryptedAccount)

        val result = testSubject.initDiscovery(NEW_EMAIL_ADDRESS.toEmailAddressOrThrow()).single().run()

        assertThat(result).isEqualTo(NoUsableSettingsFound)
    }

    @Test
    fun `should use settings of first matching existing account`() = runTest {
        val firstAccount = createExistingAccount(emailAddress = "first@example.com")
        val secondAccount = createExistingAccount(
            emailAddress = "second@example.com",
            incoming = createServerSettings(type = "imap", port = 1143, username = "second@example.com"),
        )
        val testSubject = createTestSubject(firstAccount, secondAccount)

        val result = testSubject.initDiscovery(NEW_EMAIL_ADDRESS.toEmailAddressOrThrow()).single().run()

        assertThat(result).isEqualTo(createExpectedSettings(username = NEW_EMAIL_ADDRESS))
    }

    @Test
    fun `should map StartTLS and CRAM-MD5 of existing account`() = runTest {
        val existingAccount = createExistingAccount(
            incoming = createServerSettings(
                type = "imap",
                port = 143,
                username = "user@example.com",
                connectionSecurity = MailConnectionSecurity.STARTTLS_REQUIRED,
                authType = AuthType.CRAM_MD5,
            ),
            outgoing = createServerSettings(
                type = "smtp",
                port = 587,
                username = "user@example.com",
                connectionSecurity = MailConnectionSecurity.STARTTLS_REQUIRED,
                authType = AuthType.CRAM_MD5,
            ),
        )
        val testSubject = createTestSubject(existingAccount)

        val result = testSubject.initDiscovery(NEW_EMAIL_ADDRESS.toEmailAddressOrThrow()).single().run()

        assertThat(result).isEqualTo(
            Settings(
                incomingServerSettings = ImapServerSettings(
                    hostname = "imap.example.com".toHostname(),
                    port = 143.toPort(),
                    connectionSecurity = ConnectionSecurity.StartTLS,
                    authenticationTypes = listOf(AuthenticationType.PasswordEncrypted),
                    username = NEW_EMAIL_ADDRESS,
                ),
                outgoingServerSettings = SmtpServerSettings(
                    hostname = "smtp.example.com".toHostname(),
                    port = 587.toPort(),
                    connectionSecurity = ConnectionSecurity.StartTLS,
                    authenticationTypes = listOf(AuthenticationType.PasswordEncrypted),
                    username = NEW_EMAIL_ADDRESS,
                ),
                isTrusted = true,
                source = SETTINGS_SOURCE,
            ),
        )
    }

    @Test
    fun `should map OAuth2 of existing account`() = runTest {
        val existingAccount = createExistingAccount(
            incoming = createServerSettings(
                type = "imap",
                port = 993,
                username = "user@example.com",
                authType = AuthType.XOAUTH2,
            ),
            outgoing = createServerSettings(
                type = "smtp",
                port = 465,
                username = "user@example.com",
                authType = AuthType.XOAUTH2,
            ),
        )
        val testSubject = createTestSubject(existingAccount)

        val result = testSubject.initDiscovery(NEW_EMAIL_ADDRESS.toEmailAddressOrThrow()).single().run()

        assertThat(result).isEqualTo(
            Settings(
                incomingServerSettings = ImapServerSettings(
                    hostname = "imap.example.com".toHostname(),
                    port = 993.toPort(),
                    connectionSecurity = ConnectionSecurity.TLS,
                    authenticationTypes = listOf(AuthenticationType.OAuth2),
                    username = NEW_EMAIL_ADDRESS,
                ),
                outgoingServerSettings = SmtpServerSettings(
                    hostname = "smtp.example.com".toHostname(),
                    port = 465.toPort(),
                    connectionSecurity = ConnectionSecurity.TLS,
                    authenticationTypes = listOf(AuthenticationType.OAuth2),
                    username = NEW_EMAIL_ADDRESS,
                ),
                isTrusted = true,
                source = SETTINGS_SOURCE,
            ),
        )
    }

    private fun createTestSubject(vararg existingAccounts: ExistingAccountServerSettings) =
        ExistingAccountAutoDiscovery(
            serverSettingsProvider = ExistingAccountServerSettingsProvider { existingAccounts.toList() },
        )

    private fun createExistingAccount(
        emailAddress: String = "user@example.com",
        incoming: ServerSettings = createServerSettings(type = "imap", port = 993, username = emailAddress),
        outgoing: ServerSettings = createServerSettings(type = "smtp", port = 465, username = emailAddress),
    ) = ExistingAccountServerSettings(
        emailAddress = emailAddress,
        incoming = incoming,
        outgoing = outgoing,
    )

    private fun createServerSettings(
        type: String,
        port: Int,
        username: String,
        host: String = "$type.example.com",
        connectionSecurity: MailConnectionSecurity = MailConnectionSecurity.SSL_TLS_REQUIRED,
        authType: AuthType = AuthType.PLAIN,
    ) = ServerSettings(
        type = type,
        host = host,
        port = port,
        connectionSecurity = connectionSecurity,
        authenticationType = authType,
        username = username,
        password = "password",
        clientCertificateAlias = null,
    )

    private fun createExpectedSettings(username: String) = Settings(
        incomingServerSettings = ImapServerSettings(
            hostname = "imap.example.com".toHostname(),
            port = 993.toPort(),
            connectionSecurity = ConnectionSecurity.TLS,
            authenticationTypes = listOf(AuthenticationType.PasswordCleartext),
            username = username,
        ),
        outgoingServerSettings = SmtpServerSettings(
            hostname = "smtp.example.com".toHostname(),
            port = 465.toPort(),
            connectionSecurity = ConnectionSecurity.TLS,
            authenticationTypes = listOf(AuthenticationType.PasswordCleartext),
            username = username,
        ),
        isTrusted = true,
        source = SETTINGS_SOURCE,
    )

    private companion object {
        const val NEW_EMAIL_ADDRESS = "new-user@example.com"
        const val NEW_LOCAL_PART = "new-user"
        const val SETTINGS_SOURCE = "existing-account"
    }
}
