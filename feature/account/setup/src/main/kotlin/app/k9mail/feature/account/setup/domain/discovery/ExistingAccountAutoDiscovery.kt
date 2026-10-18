package app.k9mail.feature.account.setup.domain.discovery

import app.k9mail.autodiscovery.api.AuthenticationType
import app.k9mail.autodiscovery.api.AutoDiscovery
import app.k9mail.autodiscovery.api.AutoDiscoveryResult
import app.k9mail.autodiscovery.api.AutoDiscoveryResult.NoUsableSettingsFound
import app.k9mail.autodiscovery.api.AutoDiscoveryResult.Settings
import app.k9mail.autodiscovery.api.AutoDiscoveryRunnable
import app.k9mail.autodiscovery.api.ImapServerSettings
import app.k9mail.autodiscovery.api.SmtpServerSettings
import com.fsck.k9.mail.AuthType
import com.fsck.k9.mail.ConnectionSecurity
import com.fsck.k9.mail.ServerSettings
import net.thunderbird.core.common.mail.EmailAddress
import net.thunderbird.core.common.mail.Protocols
import net.thunderbird.core.common.net.toHostname
import net.thunderbird.core.common.net.toPort

private typealias AutoDiscoveryConnectionSecurity = app.k9mail.autodiscovery.api.ConnectionSecurity

/**
 * Reuses the mail server settings of an existing account of the same email domain.
 *
 * Only accounts using IMAP and SMTP over an encrypted connection are used. Passwords are never part of the returned
 * settings.
 */
internal class ExistingAccountAutoDiscovery(
    private val serverSettingsProvider: ExistingAccountServerSettingsProvider,
) : AutoDiscovery {

    override fun initDiscovery(email: EmailAddress): List<AutoDiscoveryRunnable> {
        return listOf(AutoDiscoveryRunnable { discover(email) })
    }

    private suspend fun discover(email: EmailAddress): AutoDiscoveryResult {
        val existingAccounts = serverSettingsProvider.getAll()
        val account = existingAccounts.firstOrNull { it.isReusableFor(email) } ?: return NoUsableSettingsFound

        return Settings(
            incomingServerSettings = account.toImapServerSettings(email),
            outgoingServerSettings = account.toSmtpServerSettings(email),
            isTrusted = true,
            source = SETTINGS_SOURCE,
        )
    }

    private companion object {
        const val SETTINGS_SOURCE = "existing-account"
    }
}

private fun ExistingAccountServerSettings.isReusableFor(email: EmailAddress): Boolean {
    val domain = emailAddress.substringAfterLast('@')
    return domain.equals(email.domain.value, ignoreCase = true) && isImapAndSmtp() && hasUsableSecurity()
}

private fun ExistingAccountServerSettings.isImapAndSmtp(): Boolean {
    return incoming.type == Protocols.IMAP && outgoing.type == Protocols.SMTP
}

private fun ExistingAccountServerSettings.hasUsableSecurity(): Boolean {
    return listOf(incoming, outgoing).all { serverSettings ->
        serverSettings.connectionSecurity != ConnectionSecurity.NONE &&
            serverSettings.authenticationType.toAuthenticationType() != null
    }
}

private fun ExistingAccountServerSettings.toImapServerSettings(email: EmailAddress): ImapServerSettings {
    return ImapServerSettings(
        hostname = incoming.host.toHostname(),
        port = incoming.port.toPort(),
        connectionSecurity = incoming.connectionSecurity.toAutoDiscoveryConnectionSecurity(),
        authenticationTypes = listOfNotNull(incoming.authenticationType.toAuthenticationType()),
        username = toUsername(incoming, email),
    )
}

private fun ExistingAccountServerSettings.toSmtpServerSettings(email: EmailAddress): SmtpServerSettings {
    return SmtpServerSettings(
        hostname = outgoing.host.toHostname(),
        port = outgoing.port.toPort(),
        connectionSecurity = outgoing.connectionSecurity.toAutoDiscoveryConnectionSecurity(),
        authenticationTypes = listOfNotNull(outgoing.authenticationType.toAuthenticationType()),
        username = toUsername(outgoing, email),
    )
}

/**
 * Replace the username of the existing account with the one of the new email address: the new address is used when the
 * existing username is the existing address or something else entirely, the new local part when the existing username
 * is the local part of the existing address.
 */
private fun ExistingAccountServerSettings.toUsername(settings: ServerSettings, email: EmailAddress): String {
    return when {
        settings.username.equals(emailAddress, ignoreCase = true) -> email.address
        settings.username == emailAddress.substringBefore('@') -> email.localPart
        else -> email.address
    }
}

private fun ConnectionSecurity.toAutoDiscoveryConnectionSecurity(): AutoDiscoveryConnectionSecurity {
    return when (this) {
        ConnectionSecurity.STARTTLS_REQUIRED -> AutoDiscoveryConnectionSecurity.StartTLS
        ConnectionSecurity.SSL_TLS_REQUIRED -> AutoDiscoveryConnectionSecurity.TLS
        else -> throw IllegalArgumentException("Unencrypted connection can't be used for auto discovery: $this")
    }
}

private fun AuthType.toAuthenticationType(): AuthenticationType? {
    return when (this) {
        AuthType.PLAIN -> AuthenticationType.PasswordCleartext
        AuthType.CRAM_MD5 -> AuthenticationType.PasswordEncrypted
        AuthType.XOAUTH2 -> AuthenticationType.OAuth2
        AuthType.EXTERNAL, AuthType.NONE -> null
    }
}
