package app.k9mail.feature.migration.qrcode.ui.send

import android.app.Application
import androidx.compose.ui.test.performClick
import app.k9mail.core.ui.compose.testing.ComposeTest
import app.k9mail.core.ui.compose.testing.onNodeWithTag
import app.k9mail.core.ui.compose.testing.onNodeWithText
import app.k9mail.core.ui.compose.testing.setContentWithTheme
import app.k9mail.feature.migration.qrcode.R
import app.k9mail.feature.migration.qrcode.ui.send.TransferSendContract.Event
import app.k9mail.feature.migration.qrcode.ui.send.TransferSendContract.Step
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isTrue
import org.junit.Test
import org.robolectric.annotation.Config

@Config(application = Application::class)
class TransferSendContentKtTest : ComposeTest() {

    @Test
    fun `when the QR code is shown, the title should be displayed`() = runComposeTest {
        setContentWithTheme {
            TransferSendContent(
                step = Step.ShowingQrCode(qrText = QR_TEXT),
                onEvent = {},
                onUsePasswordManager = {},
            )
        }

        onNodeWithText(getString(R.string.migration_qrcode_transfer_send_title)).assertExists()
    }

    @Test
    fun `when there's no Wi-Fi, clicking the retry button should send RetryClicked`() = runComposeTest {
        val events = mutableListOf<Event>()

        setContentWithTheme {
            TransferSendContent(
                step = Step.NoWifi,
                onEvent = { event -> events.add(event) },
                onUsePasswordManager = {},
            )
        }

        onNodeWithTag("RetryButton").assertExists().performClick()

        assertThat(events).containsExactly(Event.RetryClicked)
    }

    @Test
    fun `when there's no Wi-Fi, the hotspot button should be displayed`() = runComposeTest {
        setContentWithTheme {
            TransferSendContent(step = Step.NoWifi, onEvent = {}, onUsePasswordManager = {})
        }

        onNodeWithText(R.string.migration_qrcode_transfer_send_hotspot_button_text).assertExists()
    }

    @Test
    fun `when there's no Wi-Fi, clicking the hotspot button should send UseHotspotClicked`() = runComposeTest {
        val events = mutableListOf<Event>()

        setContentWithTheme {
            TransferSendContent(
                step = Step.NoWifi,
                onEvent = { event -> events.add(event) },
                onUsePasswordManager = {},
            )
        }

        onNodeWithTag("UseHotspotButton").assertExists().performClick()

        assertThat(events).containsExactly(Event.UseHotspotClicked)
    }

    @Test
    fun `when the hotspot is starting, its text should be displayed`() = runComposeTest {
        setContentWithTheme {
            TransferSendContent(step = Step.StartingHotspot, onEvent = {}, onUsePasswordManager = {})
        }

        onNodeWithText(R.string.migration_qrcode_transfer_send_hotspot_starting).assertExists()
    }

    @Test
    fun `when the transfer failed, the password manager button should be displayed`() = runComposeTest {
        setContentWithTheme {
            TransferSendContent(step = Step.Failed, onEvent = {}, onUsePasswordManager = {})
        }

        onNodeWithText(R.string.migration_qrcode_transfer_send_password_manager_button_text).assertExists()
    }

    @Test
    fun `when the transfer failed, clicking the password manager button should call the callback`() = runComposeTest {
        var passwordManagerUsed = false

        setContentWithTheme {
            TransferSendContent(
                step = Step.Failed,
                onEvent = {},
                onUsePasswordManager = { passwordManagerUsed = true },
            )
        }

        onNodeWithTag("PasswordManagerButton").assertExists().performClick()

        assertThat(passwordManagerUsed).isTrue()
    }
}

private const val QR_TEXT = "TBX1:6v0o6v0o6v0o6v0o6v0o:A-v0o6v0o6v0o6v0o6v0o6v0o6v0o6v0o6v0o6v0o6v0o6v0o6v0o6v0o6v0o0"
