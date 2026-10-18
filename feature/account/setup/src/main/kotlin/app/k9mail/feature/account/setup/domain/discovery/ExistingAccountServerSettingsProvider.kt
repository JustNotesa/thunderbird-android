package app.k9mail.feature.account.setup.domain.discovery

import com.fsck.k9.mail.ServerSettings

/**
 * Provides the mail server settings of the accounts already configured on this device.
 */
internal fun interface ExistingAccountServerSettingsProvider {
    suspend fun getAll(): List<ExistingAccountServerSettings>
}

/**
 * Mail server settings of an existing account.
 *
 * The [ServerSettings.password] of [incoming] and [outgoing] is never read.
 */
internal data class ExistingAccountServerSettings(
    val emailAddress: String,
    val incoming: ServerSettings,
    val outgoing: ServerSettings,
)
