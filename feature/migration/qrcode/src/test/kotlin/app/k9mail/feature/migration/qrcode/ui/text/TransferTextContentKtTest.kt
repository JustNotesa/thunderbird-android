package app.k9mail.feature.migration.qrcode.ui.text

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.performClick
import app.k9mail.core.ui.compose.testing.ComposeTest
import app.k9mail.core.ui.compose.testing.onNodeWithTag
import app.k9mail.core.ui.compose.testing.onNodeWithText
import app.k9mail.core.ui.compose.testing.setContentWithTheme
import app.k9mail.feature.migration.qrcode.R
import app.k9mail.feature.migration.qrcode.ui.text.TransferTextContract.Event
import app.k9mail.feature.migration.qrcode.ui.text.TransferTextContract.State
import app.k9mail.feature.migration.qrcode.ui.text.TransferTextContract.Step
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private const val LONG_TEXT_LENGTH = 7001
private const val ACCOUNT_COUNT = 2

@Config(application = Application::class)
class TransferTextContentKtTest : ComposeTest() {

    @Test
    fun `when the screen is shown, the accounts should be hidden`() = runComposeTest {
        setContentWithTheme {
            TransferTextContent(state = readyState(text = ACCOUNT_TEXT), onEvent = {})
        }

        onNodeWithTag("AccountsTextField").assertExists()
        onNodeWithText(ACCOUNT_TEXT).assertDoesNotExist()
        onNodeWithText(hiddenText()).assertExists()
        onNodeWithTag("TextVisibilityButton").assertExists()
    }

    @Test
    fun `the user can show the accounts right away and hide them again`() = runComposeTest {
        var revealed by mutableStateOf(false)
        setContentWithTheme {
            TransferTextContent(
                state = readyState(text = ACCOUNT_TEXT, revealed = revealed),
                onEvent = { event ->
                    if (event is Event.TextVisibilityToggled) {
                        revealed = !revealed
                    }
                },
            )
        }

        onNodeWithTag("TextVisibilityButton").performClick()

        onNodeWithText(ACCOUNT_TEXT).assertExists()
        onNodeWithText(hiddenText()).assertDoesNotExist()

        onNodeWithTag("TextVisibilityButton").performClick()

        onNodeWithText(ACCOUNT_TEXT).assertDoesNotExist()
        onNodeWithText(hiddenText()).assertExists()
    }

    @Test
    fun `when the text is short, there is no warning about its length`() = runComposeTest {
        setContentWithTheme {
            TransferTextContent(state = readyState(text = ACCOUNT_TEXT), onEvent = {})
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
            TransferTextContent(state = readyState(text = ACCOUNT_TEXT, copied = true), onEvent = {})
        }

        onNodeWithTag("CopiedText").assertExists()
        onNodeWithText(R.string.migration_qrcode_transfer_text_copied).assertExists()
    }
}

private const val ACCOUNT_TEXT = """{"type":"thunderbird-account-transfer","version":1,"createdAt":1792300000}"""

/** What the field names instead of the text: how much account data it holds and that it is hidden. */
private fun hiddenText(): String {
    return RuntimeEnvironment.getApplication().getString(
        R.string.migration_qrcode_transfer_text_hidden,
        ACCOUNT_COUNT,
        ACCOUNT_TEXT.length,
    )
}

private fun readyState(text: String, revealed: Boolean = false, copied: Boolean = false): State {
    return State(
        step = Step.Ready(text = text, accountCount = ACCOUNT_COUNT),
        copied = copied,
        revealed = revealed,
    )
}
