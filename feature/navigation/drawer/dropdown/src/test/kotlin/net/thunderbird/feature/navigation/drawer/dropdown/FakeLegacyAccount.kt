package net.thunderbird.feature.navigation.drawer.dropdown

import com.fsck.k9.mail.AuthType
import com.fsck.k9.mail.ConnectionSecurity
import com.fsck.k9.mail.ServerSettings
import net.thunderbird.core.android.account.Identity
import net.thunderbird.core.android.account.LegacyAccount
import net.thunderbird.core.common.mail.Protocols
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.AccountIdFactory
import net.thunderbird.feature.account.storage.profile.AvatarDto
import net.thunderbird.feature.account.storage.profile.AvatarTypeDto
import net.thunderbird.feature.account.storage.profile.ProfileDto

private const val EMAIL_ADDRESS = "user@example.com"

/**
 * Creates a [LegacyAccount] with the special folder ids that are relevant for the unified folders.
 */
internal fun createLegacyAccount(
    id: AccountId = AccountIdFactory.create(),
    name: String = "Account Name",
    inboxFolderId: Long? = null,
    draftsFolderId: Long? = null,
    sentFolderId: Long? = null,
    archiveFolderId: Long? = null,
    spamFolderId: Long? = null,
    trashFolderId: Long? = null,
): LegacyAccount {
    return LegacyAccount(
        id = id,
        name = name,
        profile = ProfileDto(
            id = id,
            name = name,
            color = 0,
            avatar = AvatarDto(
                accountId = id,
                avatarType = AvatarTypeDto.MONOGRAM,
                avatarMonogram = "us",
                avatarImageUri = null,
                avatarIconName = null,
            ),
        ),
        identities = listOf(Identity(email = EMAIL_ADDRESS)),
        email = EMAIL_ADDRESS,
        incomingServerSettings = ServerSettings(
            type = Protocols.IMAP,
            host = "imap.example.com",
            port = 993,
            connectionSecurity = ConnectionSecurity.SSL_TLS_REQUIRED,
            authenticationType = AuthType.PLAIN,
            username = "user",
            password = "pass",
            clientCertificateAlias = null,
        ),
        outgoingServerSettings = ServerSettings(
            type = Protocols.SMTP,
            host = "smtp.example.com",
            port = 465,
            connectionSecurity = ConnectionSecurity.SSL_TLS_REQUIRED,
            authenticationType = AuthType.PLAIN,
            username = "user",
            password = "pass",
            clientCertificateAlias = null,
        ),
        inboxFolderId = inboxFolderId,
        draftsFolderId = draftsFolderId,
        sentFolderId = sentFolderId,
        archiveFolderId = archiveFolderId,
        spamFolderId = spamFolderId,
        trashFolderId = trashFolderId,
    )
}
