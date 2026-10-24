package net.thunderbird.feature.navigation.drawer.dropdown.ui.account

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import net.thunderbird.components.ui.bolt.atom.icon.Icon
import net.thunderbird.components.ui.bolt.atom.icon.Icons
import net.thunderbird.components.ui.bolt.atom.text.TextBodyLargeAutoResize
import net.thunderbird.components.ui.bolt.atom.text.TextBodyMedium
import net.thunderbird.components.ui.bolt.organism.drawer.NavigationDrawerItem
import net.thunderbird.components.ui.bolt.theme.BoltTheme
import net.thunderbird.feature.account.avatar.ui.Avatar
import net.thunderbird.feature.account.avatar.ui.AvatarSize
import net.thunderbird.feature.navigation.drawer.dropdown.R
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.DisplayAccount
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.MailDisplayAccount
import net.thunderbird.feature.navigation.drawer.dropdown.ui.common.getDisplayAccountAvatar
import net.thunderbird.feature.navigation.drawer.dropdown.ui.common.getDisplayAccountColor
import net.thunderbird.feature.navigation.drawer.dropdown.ui.common.getDisplayAccountName

@Composable
internal fun AccountListItem(
    account: DisplayAccount,
    onClick: (DisplayAccount) -> Unit,
    onAvatarClick: (DisplayAccount) -> Unit,
    selected: Boolean,
    showStarredCount: Boolean,
    modifier: Modifier = Modifier,
) {
    NavigationDrawerItem(
        label = { AccountLabel(account = account) },
        selected = selected,
        onClick = { onClick(account) },
        modifier = modifier
            .fillMaxWidth()
            .height(BoltTheme.sizes.large),
        icon = {
            AccountAvatarButton(
                account = account,
                onClick = onAvatarClick,
            )
        },
        badge = {
            Crossfade(account.hasError) { hasError ->
                if (hasError) {
                    Icon(
                        imageVector = Icons.DualTone.Warning,
                        tint = if (selected) {
                            BoltTheme.colors.onSecondaryContainer
                        } else {
                            BoltTheme.colors.error
                        },
                    )
                } else {
                    AccountListItemBadge(
                        unreadCount = account.unreadMessageCount,
                        starredCount = account.starredMessageCount,
                        showStarredCount = showStarredCount,
                    )
                }
            }
        },
    )
}

/**
 * Shows the account avatar as a click target of its own. Clicking the avatar opens the inbox of the account, while
 * clicking anywhere else on the account row shows the folders of the account.
 */
@Composable
private fun AccountAvatarButton(
    account: DisplayAccount,
    onClick: (DisplayAccount) -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = getDisplayAccountColor(account)
    val avatar = getDisplayAccountAvatar(account)
    val openInboxLabel = stringResource(R.string.navigation_drawer_dropdown_account_open_inbox_action)

    Box(
        modifier = modifier
            .clip(CircleShape)
            .clickable(
                onClickLabel = openInboxLabel,
                role = Role.Button,
            ) { onClick(account) }
            .semantics { contentDescription = openInboxLabel },
    ) {
        Avatar(
            avatar = avatar,
            color = color,
            size = AvatarSize.MEDIUM,
        )
    }
}

@Composable
private fun AccountLabel(
    account: DisplayAccount,
    modifier: Modifier = Modifier,
) {
    val name = getDisplayAccountName(account)

    Column(
        verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.half),
        modifier = modifier.fillMaxWidth(),
    ) {
        TextBodyLargeAutoResize(
            text = buildAnnotatedString {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(name)
                }
            },
            softWrap = true,
            maxLines = 2,
        )
        if (account is MailDisplayAccount && account.name != account.email) {
            TextBodyMedium(
                text = account.email,
            )
        }
    }
}
