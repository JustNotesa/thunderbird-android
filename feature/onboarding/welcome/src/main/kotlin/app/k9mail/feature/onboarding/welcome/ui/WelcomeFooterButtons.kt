package app.k9mail.feature.onboarding.welcome.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.k9mail.feature.onboarding.welcome.R
import net.thunderbird.components.ui.bolt.atom.button.ButtonFilled
import net.thunderbird.components.ui.bolt.atom.button.ButtonOutlined
import net.thunderbird.components.ui.bolt.atom.button.ButtonText
import net.thunderbird.components.ui.bolt.theme.BoltTheme

@Composable
internal fun WelcomeFooterButtons(
    options: WelcomeOptions,
    modifier: Modifier = Modifier,
) {
    val hasExtraOptions = options.onScanQrCodeClick != null ||
        options.onPasteAccountsClick != null ||
        options.onThundermailClick != null

    if (hasExtraOptions) {
        WelcomeAllOptionsButtons(options = options, modifier = modifier)
    } else {
        WelcomeDefaultButtons(options = options, modifier = modifier)
    }
}

@Composable
private fun WelcomeAllOptionsButtons(
    options: WelcomeOptions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = BoltTheme.spacings.quadruple),
        verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.quarter),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ButtonFilled(
            text = stringResource(id = R.string.onboarding_welcome_add_account_button),
            onClick = options.onAddAccountClick,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("onboarding_welcome_start_button"),
        )
        options.onScanQrCodeClick?.let { onScanQrCodeClick ->
            ButtonOutlined(
                text = stringResource(id = R.string.onboarding_welcome_scan_qr_code_button),
                onClick = onScanQrCodeClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_welcome_scan_qr_code_button"),
            )
        }
        options.onThundermailClick?.let { onThundermailClick ->
            ButtonOutlined(
                text = stringResource(id = R.string.onboarding_welcome_thundermail_button),
                onClick = onThundermailClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_welcome_thundermail_button"),
            )
        }
        options.onPasteAccountsClick?.let { onPasteAccountsClick ->
            ButtonText(
                text = stringResource(id = R.string.onboarding_welcome_paste_accounts_button),
                onClick = onPasteAccountsClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_welcome_paste_accounts_button"),
            )
        }
        ButtonText(
            text = stringResource(id = R.string.onboarding_welcome_import_button),
            onClick = options.onImportClick,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun WelcomeDefaultButtons(
    options: WelcomeOptions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.quarter),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ButtonFilled(
            text = stringResource(id = R.string.onboarding_welcome_start_button),
            onClick = options.onAddAccountClick,
            modifier = Modifier.testTag("onboarding_welcome_start_button"),
        )
        ButtonText(
            text = stringResource(id = R.string.onboarding_welcome_import_button),
            onClick = options.onImportClick,
        )
    }
}
