package app.k9mail.feature.migration.qrcode.ui.text

import android.app.Application
import app.k9mail.core.ui.compose.testing.ComposeTest
import app.k9mail.core.ui.compose.testing.onNodeWithTag
import app.k9mail.core.ui.compose.testing.onNodeWithText
import app.k9mail.core.ui.compose.testing.setContentWithTheme
import app.k9mail.feature.migration.qrcode.R
import app.k9mail.feature.migration.qrcode.ui.text.TransferTextContract.State
import app.k9mail.feature.migration.qrcode.ui.text.TransferTextContract.Step
import org.junit.Test
import org.robolectric.annotation.Config

private const val LONG_TEXT_LENGTH = 7001

@Config(application = Application::class)
class TransferTextContentKtTest : ComposeTest() {

    @Test
    fun `when the screen is shown, the text of the accounts should be displayed`() = runComposeTest {
        setContentWithTheme {
            TransferTextContent(state = readyState(text = TRANSFER_TEXT), onEvent = {})
        }

        onNodeWithTag("AccountsTextField").assertExists()
        onNodeWithText(TRANSFER_TEXT).assertExists()
    }

    @Test
    fun `when the text is short, there is no warning about its length`() = runComposeTest {
        setContentWithTheme {
            TransferTextContent(state = readyState(text = TRANSFER_TEXT), onEvent = {})
        }

        onNodeWithTag("TooLongText").assertDoesNotExist()
    }

    @Test
    fun `when the text is longer than a note, the user is warned`() = runComposeTest {
        setContentWithTheme {
            TransferTextContent(state = readyState(text = "a".repeat(LONG_TEXT_LENGTH)), onEvent = {})
        }

        onNodeWithTag("TooLongText").assertExists()
        onNodeWithText(R.string.migration_qrcode_transfer_text_too_long).assertExists()
    }

    @Test
    fun `the copy button is only shown while the accounts were copied`() = runComposeTest {
        setContentWithTheme {
            TransferTextContent(state = readyState(text = TRANSFER_TEXT, copied = true), onEvent = {})
        }

        onNodeWithTag("CopiedText").assertExists()
        onNodeWithText(R.string.migration_qrcode_transfer_text_copied).assertExists()
    }
}

private const val TRANSFER_TEXT = """{"type":"thunderbird-account-transfer","version":1,"createdAt":1792300000}"""

private fun readyState(text: String, copied: Boolean = false): State {
    return State(step = Step.Ready(text = text), copied = copied)
}
