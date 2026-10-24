package app.k9mail.feature.onboarding.welcome.ui

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import app.k9mail.core.ui.compose.testing.ComposeTest
import app.k9mail.core.ui.compose.testing.onNodeWithText
import app.k9mail.core.ui.compose.testing.setContentWithTheme
import app.k9mail.feature.onboarding.welcome.R
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test
import org.robolectric.annotation.Config

private const val FOOTER_ITEM_INDEX = 2

@Config(qualifiers = "w411dp-h1200dp")
class WelcomeContentTest : ComposeTest() {
    @Test
    fun `pressing the add account button should call onAddAccountClick`() = runComposeTest {
        var addAccountClickCounter = 0
        setContentWithTheme {
            WelcomeContent(
                options = WelcomeOptions(
                    onAddAccountClick = { addAccountClickCounter++ },
                    onScanQrCodeClick = { error("Should not be called") },
                    onPasteAccountsClick = { error("Should not be called") },
                    onThundermailClick = { error("Should not be called") },
                    onImportClick = { error("Should not be called") },
                ),
                appName = "Thunderbird",
            )
        }

        scrollToFooter()
        composeTestRule.onNodeWithTag("onboarding_welcome_start_button").performClick()

        assertThat(addAccountClickCounter).isEqualTo(1)
    }

    @Test
    fun `pressing the scan QR code button should call onScanQrCodeClick`() = runComposeTest {
        var scanQrCodeClickCounter = 0
        setContentWithTheme {
            WelcomeContent(
                options = WelcomeOptions(
                    onAddAccountClick = { error("Should not be called") },
                    onScanQrCodeClick = { scanQrCodeClickCounter++ },
                    onPasteAccountsClick = { error("Should not be called") },
                    onThundermailClick = { error("Should not be called") },
                    onImportClick = { error("Should not be called") },
                ),
                appName = "Thunderbird",
            )
        }

        scrollToFooter()
        composeTestRule.onNodeWithTag("onboarding_welcome_scan_qr_code_button").performClick()

        assertThat(scanQrCodeClickCounter).isEqualTo(1)
    }

    @Test
    fun `pressing the thundermail button should call onThundermailClick`() = runComposeTest {
        var thundermailClickCounter = 0
        setContentWithTheme {
            WelcomeContent(
                options = WelcomeOptions(
                    onAddAccountClick = { error("Should not be called") },
                    onScanQrCodeClick = { error("Should not be called") },
                    onPasteAccountsClick = { error("Should not be called") },
                    onThundermailClick = { thundermailClickCounter++ },
                    onImportClick = { error("Should not be called") },
                ),
                appName = "Thunderbird",
            )
        }

        scrollToFooter()
        composeTestRule.onNodeWithTag("onboarding_welcome_thundermail_button").performClick()

        assertThat(thundermailClickCounter).isEqualTo(1)
    }

    @Test
    fun `pressing the paste accounts button should call onPasteAccountsClick`() = runComposeTest {
        var pasteAccountsClickCounter = 0
        setContentWithTheme {
            WelcomeContent(
                options = WelcomeOptions(
                    onAddAccountClick = { error("Should not be called") },
                    onScanQrCodeClick = { error("Should not be called") },
                    onPasteAccountsClick = { pasteAccountsClickCounter++ },
                    onThundermailClick = { error("Should not be called") },
                    onImportClick = { error("Should not be called") },
                ),
                appName = "Thunderbird",
            )
        }

        scrollToFooter()
        composeTestRule.onNodeWithTag("onboarding_welcome_paste_accounts_button").performClick()

        assertThat(pasteAccountsClickCounter).isEqualTo(1)
    }

    @Test
    fun `pressing the import button should call onImportClick`() = runComposeTest {
        var importClickCounter = 0
        setContentWithTheme {
            WelcomeContent(
                options = WelcomeOptions(
                    onAddAccountClick = { error("Should not be called") },
                    onScanQrCodeClick = { error("Should not be called") },
                    onPasteAccountsClick = { error("Should not be called") },
                    onThundermailClick = { error("Should not be called") },
                    onImportClick = { importClickCounter++ },
                ),
                appName = "Thunderbird",
            )
        }

        scrollToFooter()
        onNodeWithText(R.string.onboarding_welcome_import_button).performClick()

        assertThat(importClickCounter).isEqualTo(1)
    }

    @Test
    fun `without additional options the additional buttons should not exist`() = runComposeTest {
        setContentWithTheme {
            WelcomeContent(
                options = WelcomeOptions(
                    onAddAccountClick = {},
                    onImportClick = {},
                ),
                appName = "K-9 Mail",
            )
        }

        composeTestRule.onNodeWithTag("onboarding_welcome_start_button").assertExists()
        composeTestRule.onNodeWithTag("onboarding_welcome_scan_qr_code_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("onboarding_welcome_thundermail_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("onboarding_welcome_paste_accounts_button").assertDoesNotExist()
    }

    private fun ComposeContentTestRule.scrollToFooter() {
        onAllNodes(hasScrollAction()).onFirst().performScrollToIndex(FOOTER_ITEM_INDEX)
        waitForIdle()
    }
}
