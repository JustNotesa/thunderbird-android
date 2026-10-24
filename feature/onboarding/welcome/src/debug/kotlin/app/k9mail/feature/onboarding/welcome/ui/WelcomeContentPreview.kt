package app.k9mail.feature.onboarding.welcome.ui

import androidx.compose.runtime.Composable
import net.thunderbird.components.ui.bolt.PreviewThemeType
import net.thunderbird.components.ui.bolt.PreviewWithTheme
import net.thunderbird.components.ui.bolt.common.annotation.PreviewDevices

@Composable
@PreviewDevices
internal fun WelcomeContentThunderbirdPreview() {
    PreviewWithTheme(themeType = PreviewThemeType.THUNDERBIRD) {
        WelcomeContent(
            options = WelcomeOptions(
                onAddAccountClick = {},
                onScanQrCodeClick = {},
                onPasteAccountsClick = {},
                onThundermailClick = {},
                onImportClick = {},
            ),
            appName = "Thunderbird Beta",
        )
    }
}

@Composable
@PreviewDevices
internal fun WelcomeContentK9MailPreview() {
    PreviewWithTheme(themeType = PreviewThemeType.K9MAIL) {
        WelcomeContent(
            options = WelcomeOptions(
                onAddAccountClick = {},
                onImportClick = {},
            ),
            appName = "K-9 Mail",
        )
    }
}
