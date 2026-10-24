package app.k9mail.feature.migration.qrcode.ui.choice

import android.app.Application
import androidx.compose.ui.test.performClick
import app.k9mail.core.ui.compose.testing.ComposeTest
import app.k9mail.core.ui.compose.testing.onNodeWithTag
import app.k9mail.core.ui.compose.testing.onNodeWithText
import app.k9mail.core.ui.compose.testing.setContentWithTheme
import app.k9mail.feature.migration.qrcode.R
import assertk.assertThat
import assertk.assertions.containsExactly
import org.junit.Test
import org.robolectric.annotation.Config

@Config(application = Application::class)
class TransferChoiceContentKtTest : ComposeTest() {

    @Test
    fun `when the screen is shown, the title should be displayed`() = runComposeTest {
        setContentWithTheme {
            TransferChoiceContent(onQrCodeClick = {}, onPasswordManagerClick = {})
        }

        onNodeWithText(R.string.migration_qrcode_transfer_choice_title).assertExists()
    }

    @Test
    fun `when the screen is shown, both ways to transfer the accounts should be offered`() = runComposeTest {
        setContentWithTheme {
            TransferChoiceContent(onQrCodeClick = {}, onPasswordManagerClick = {})
        }

        onNodeWithText(R.string.migration_qrcode_transfer_choice_qr_code_title).assertExists()
        onNodeWithText(R.string.migration_qrcode_transfer_choice_qr_code_description).assertExists()
        onNodeWithText(R.string.migration_qrcode_transfer_choice_password_manager_title).assertExists()
        onNodeWithText(R.string.migration_qrcode_transfer_choice_password_manager_description).assertExists()
    }

    @Test
    fun `when the QR code card is clicked, the transfer should start`() = runComposeTest {
        val clicks = mutableListOf<String>()

        setContentWithTheme {
            TransferChoiceContent(
                onQrCodeClick = { clicks.add("qrCode") },
                onPasswordManagerClick = { clicks.add("passwordManager") },
            )
        }

        onNodeWithTag("QrCodeChoiceCard").performClick()

        assertThat(clicks).containsExactly("qrCode")
    }

    @Test
    fun `when the password manager card is clicked, the transfer should start`() = runComposeTest {
        val clicks = mutableListOf<String>()

        setContentWithTheme {
            TransferChoiceContent(
                onQrCodeClick = { clicks.add("qrCode") },
                onPasswordManagerClick = { clicks.add("passwordManager") },
            )
        }

        onNodeWithTag("PasswordManagerChoiceCard").performClick()

        assertThat(clicks).containsExactly("passwordManager")
    }
}
