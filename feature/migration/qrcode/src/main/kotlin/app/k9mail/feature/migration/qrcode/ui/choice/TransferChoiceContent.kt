package app.k9mail.feature.migration.qrcode.ui.choice

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.k9mail.feature.migration.qrcode.R
import net.thunderbird.components.ui.bolt.atom.Surface
import net.thunderbird.components.ui.bolt.atom.card.CardOutlined
import net.thunderbird.components.ui.bolt.atom.icon.Icon
import net.thunderbird.components.ui.bolt.atom.icon.Icons
import net.thunderbird.components.ui.bolt.atom.text.TextBodyMedium
import net.thunderbird.components.ui.bolt.atom.text.TextTitleLarge
import net.thunderbird.components.ui.bolt.atom.text.TextTitleMedium
import net.thunderbird.components.ui.bolt.template.ResponsiveContent
import net.thunderbird.components.ui.bolt.theme.BoltTheme

/**
 * Stateless content of the screen that lets the user choose how the accounts are sent to another device.
 */
@Composable
internal fun TransferChoiceContent(
    onQrCodeClick: () -> Unit,
    onPasswordManagerClick: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        ResponsiveContent(
            modifier = Modifier.testTag("TransferChoiceContent"),
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .padding(BoltTheme.spacings.double)
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState()),
            ) {
                TextTitleLarge(text = stringResource(R.string.migration_qrcode_transfer_choice_title))
                Spacer(modifier = Modifier.height(BoltTheme.spacings.double))

                ChoiceCard(
                    title = stringResource(R.string.migration_qrcode_transfer_choice_qr_code_title),
                    description = stringResource(R.string.migration_qrcode_transfer_choice_qr_code_description),
                    icon = Icons.Outlined.QrCode,
                    onClick = onQrCodeClick,
                    modifier = Modifier.testTag("QrCodeChoiceCard"),
                )
                Spacer(modifier = Modifier.height(BoltTheme.spacings.double))

                ChoiceCard(
                    title = stringResource(R.string.migration_qrcode_transfer_choice_password_manager_title),
                    description = stringResource(
                        R.string.migration_qrcode_transfer_choice_password_manager_description,
                    ),
                    icon = Icons.Outlined.Lock,
                    onClick = onPasswordManagerClick,
                    modifier = Modifier.testTag("PasswordManagerChoiceCard"),
                )
            }
        }
    }
}

/** One of the ways the accounts can be transferred, the user picks the one they want to use. */
@Composable
private fun ChoiceCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CardOutlined(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(BoltTheme.spacings.double),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                tint = BoltTheme.colors.primary,
                modifier = Modifier.size(BoltTheme.sizes.large),
            )
            Column(
                modifier = Modifier.padding(start = BoltTheme.spacings.double),
            ) {
                TextTitleMedium(text = title)
                TextBodyMedium(
                    text = description,
                    color = BoltTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}
