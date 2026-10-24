package app.k9mail.feature.migration.qrcode.ui.paste

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
import app.k9mail.feature.migration.qrcode.ui.paste.TransferPasteContract.Event
import app.k9mail.feature.migration.qrcode.ui.paste.TransferPasteContract.State
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@Config(application = Application::class)
class TransferPasteContentKtTest : ComposeTest() {

    @Test
    fun `when the screen is shown, the title should be displayed`() = runComposeTest {
        setContentWithTheme {
            TransferPasteContent(state = State(), onEvent = {})
        }

        onNodeWithText(R.string.migration_qrcode_transfer_paste_title).assertExists()
    }

    @Test
    fun `when the screen is shown, the illustration should be displayed`() = runComposeTest {
        setContentWithTheme {
            TransferPasteContent(state = State(), onEvent = {})
        }

        onNodeWithTag("TransferGuideIllustration").assertExists()
    }

    @Test
    fun `when the help button is clicked, the help should be shown`() = runComposeTest {
        setContentWithTheme {
            TransferPasteContent(state = State(), onEvent = {})
        }

        onNodeWithTag("HelpButton").performClick()

        onNodeWithText(R.string.migration_qrcode_transfer_paste_help_title).assertExists()
    }

    @Test
    fun `when accounts were pasted, they should be hidden`() = runComposeTest {
        setContentWithTheme {
            TransferPasteContent(state = pastedState(), onEvent = {})
        }

        onNodeWithTag("AccountsTextField").assertExists()
        onNodeWithText(ACCOUNT_TEXT).assertDoesNotExist()
        onNodeWithText(recognizedText()).assertExists()
        onNodeWithTag("TextVisibilityButton").assertExists()
    }

    @Test
    fun `when text that holds no accounts was pasted, it should be masked`() = runComposeTest {
        setContentWithTheme {
            TransferPasteContent(state = State(text = OTHER_TEXT), onEvent = {})
        }

        onNodeWithText(OTHER_TEXT).assertDoesNotExist()
        onNodeWithText(MASKED_TEXT).assertExists()
    }

    @Test
    fun `the user can show the pasted text right away and hide it again`() = runComposeTest {
        var revealed by mutableStateOf(false)
        setContentWithTheme {
            TransferPasteContent(
                state = pastedState(revealed = revealed),
                onEvent = { event ->
                    if (event is Event.TextVisibilityToggled) {
                        revealed = !revealed
                    }
                },
            )
        }

        onNodeWithTag("TextVisibilityButton").performClick()

        onNodeWithText(ACCOUNT_TEXT).assertExists()

        onNodeWithTag("TextVisibilityButton").performClick()

        onNodeWithText(ACCOUNT_TEXT).assertDoesNotExist()
        onNodeWithText(recognizedText()).assertExists()
    }
}

private const val ACCOUNT_TEXT = """{"type":"thunderbird-account-transfer","version":1,"createdAt":1792300000,""" +
    """"accounts":[1,[1,1],[0,"imap.domain.example",993,3,2,"username","My Account"]],"extensions":{}}"""
private const val OTHER_TEXT = "The password of my mail account is …"
private const val ACCOUNT_COUNT = 1

/** What the field names instead of the accounts: how many of them the reader found. */
private fun recognizedText(): String {
    return RuntimeEnvironment.getApplication()
        .getString(R.string.migration_qrcode_transfer_paste_recognized_one, ACCOUNT_COUNT)
}

private fun pastedState(revealed: Boolean = false): State {
    return State(text = ACCOUNT_TEXT, recognizedAccountCount = ACCOUNT_COUNT, revealed = revealed)
}
