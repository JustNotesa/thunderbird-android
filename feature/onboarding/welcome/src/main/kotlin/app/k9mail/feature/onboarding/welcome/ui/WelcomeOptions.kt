package app.k9mail.feature.onboarding.welcome.ui

/**
 * The ways of adding an account that are offered on the welcome screen.
 *
 * A `null` callback means that the according option isn't offered on the welcome screen.
 */
data class WelcomeOptions(
    val onAddAccountClick: () -> Unit,
    val onScanQrCodeClick: (() -> Unit)? = null,
    val onPasteAccountsClick: (() -> Unit)? = null,
    val onThundermailClick: (() -> Unit)? = null,
    val onImportClick: () -> Unit,
)
