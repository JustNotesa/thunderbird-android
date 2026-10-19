package app.k9mail.feature.migration.qrcode.ui.paste

import android.app.Application
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performClick
import app.k9mail.core.ui.compose.testing.ComposeTest
import app.k9mail.core.ui.compose.testing.onNodeWithTag
import app.k9mail.core.ui.compose.testing.onNodeWithText
import app.k9mail.core.ui.compose.testing.setContentWithTheme
import app.k9mail.feature.migration.qrcode.R
import app.k9mail.feature.migration.qrcode.ui.paste.TransferPasteContract.Event
import app.k9mail.feature.migration.qrcode.ui.paste.TransferPasteContract.State
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
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
    fun `the user can show the pasted text and hide it again`() = runComposeTest {
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

    @Test
    fun `when the pasted text is shown, it should be blurred until it is tapped`() = runComposeTest {
        // Only Android 12 and newer can blur, below that the text is readable as soon as it is shown.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setContentWithTheme {
                TransferPasteContent(state = pastedState(revealed = true), onEvent = {})
            }

            onNodeWithTag("AccountsTextField").assertAccountsAreBlurred()

            onNodeWithTag("AccountsTextField").performClick()

            onNodeWithTag("AccountsTextField").assertAccountsAreReadable()
        }
    }
}

private fun SemanticsNodeInteraction.assertAccountsAreBlurred() {
    assertThat(fetchSemanticsNode().config[AccountsBlurredKey]).isTrue()
}

private fun SemanticsNodeInteraction.assertAccountsAreReadable() {
    assertThat(fetchSemanticsNode().config[AccountsBlurredKey]).isFalse()
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
