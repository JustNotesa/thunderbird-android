package app.k9mail.feature.migration.qrcode.ui.paste

import android.app.Application
import androidx.compose.ui.test.performClick
import app.k9mail.core.ui.compose.testing.ComposeTest
import app.k9mail.core.ui.compose.testing.onNodeWithTag
import app.k9mail.core.ui.compose.testing.onNodeWithText
import app.k9mail.core.ui.compose.testing.setContentWithTheme
import app.k9mail.feature.migration.qrcode.R
import app.k9mail.feature.migration.qrcode.ui.paste.TransferPasteContract.State
import org.junit.Test
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
}
