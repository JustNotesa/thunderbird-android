package com.fsck.k9.ui.managefolders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import net.thunderbird.components.ui.bolt.atom.text.TextBodyLarge
import net.thunderbird.components.ui.bolt.atom.text.TextBodyMedium
import net.thunderbird.components.ui.bolt.theme.BoltTheme
import net.thunderbird.feature.account.avatar.ui.Avatar
import net.thunderbird.feature.account.avatar.ui.AvatarSize

private val MAX_WIDTH = 400.dp

/**
 * The accounts to choose from in the folder management, on a card that floats above the folders. They look like in
 * the folder drawer.
 */
@Composable
internal fun FolderManagementAccountList(
    accounts: ImmutableList<FolderManagementAccount>,
    selectedAccountUuid: String,
    onAccountClick: (FolderManagementAccount) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.half),
        modifier = modifier
            .padding(
                start = BoltTheme.spacings.double,
                end = BoltTheme.spacings.double,
                bottom = BoltTheme.spacings.double,
            )
            .widthIn(max = MAX_WIDTH)
            .shadow(elevation = BoltTheme.elevations.level3, shape = BoltTheme.shapes.large)
            .background(color = BoltTheme.colors.surfaceContainer, shape = BoltTheme.shapes.large)
            .padding(BoltTheme.spacings.default),
    ) {
        accounts.forEach { account ->
            AccountItem(
                account = account,
                selected = account.uuid == selectedAccountUuid,
                onClick = { onAccountClick(account) },
            )
        }
    }
}

@Composable
private fun AccountItem(
    account: FolderManagementAccount,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BoltTheme.spacings.oneHalf),
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(if (selected) BoltTheme.colors.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = BoltTheme.spacings.double, vertical = BoltTheme.spacings.default),
    ) {
        Avatar(
            avatar = account.avatar,
            color = Color(account.color),
            size = AvatarSize.MEDIUM,
        )
        val textColor = if (selected) BoltTheme.colors.onSecondaryContainer else BoltTheme.colors.onSurface
        Column(verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.half)) {
            TextBodyLarge(
                color = textColor,
                text = buildAnnotatedString {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(account.name)
                    }
                },
            )
            if (account.name != account.email) {
                TextBodyMedium(text = account.email, color = textColor)
            }
        }
    }
}
