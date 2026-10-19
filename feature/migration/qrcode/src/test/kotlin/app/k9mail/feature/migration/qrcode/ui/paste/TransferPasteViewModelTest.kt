package app.k9mail.feature.migration.qrcode.ui.paste

import android.app.Application
import app.k9mail.core.ui.compose.testing.mvi.runMviTest
import app.k9mail.feature.migration.qrcode.ui.paste.TransferPasteContract.PasteError
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import net.thunderbird.components.ui.testing.coroutines.MainDispatcherHelper
import net.thunderbird.core.logging.testing.TestLogger
import net.thunderbird.legacy.logging.Log
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TransferPasteViewModelTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val mainDispatcher = MainDispatcherHelper(UnconfinedTestDispatcher())

    @BeforeTest
    fun setUp() {
        Log.logger = TestLogger()
        mainDispatcher.setUp()
    }

    @AfterTest
    fun tearDown() {
        mainDispatcher.tearDown()
    }

    @Test
    fun `user imports the accounts of a note of the password manager`() = runMviTest {
        with(TransferPasteScreenRobot(mviContext = this)) {
            startScreen()

            userPastesText(TRANSFER_TEXT)
            userClicksImport()

            assertClipboardWasCleared()
            assertImportResult(expectedNumberOfAccounts = 1)

            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `user imports the accounts of a note that is surrounded by whitespace`() = runMviTest {
        with(TransferPasteScreenRobot(mviContext = this)) {
            startScreen()

            userPastesText("  $TRANSFER_TEXT\n")
            userClicksImport()

            assertClipboardWasCleared()
            assertImportResult(expectedNumberOfAccounts = 1)

            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `user pastes text that isn't the accounts of a transfer`() = runMviTest {
        with(TransferPasteScreenRobot(mviContext = this)) {
            startScreen()

            userPastesText("The password of my mail account is …")
            userClicksImport()

            assertError(PasteError.NotRecognized)
            assertNothingWasImported()

            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `user pastes accounts this version of the app doesn't understand`() = runMviTest {
        with(TransferPasteScreenRobot(mviContext = this)) {
            startScreen()

            userPastesText(TRANSFER_TEXT_OF_NEWER_ACCOUNTS)
            userClicksImport()

            assertError(PasteError.Unsupported)
            assertNothingWasImported()

            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `user pastes the text of the clipboard into the field`() = runMviTest {
        with(TransferPasteScreenRobot(mviContext = this)) {
            startScreen()

            userClicksPasteFromClipboard(clipboardText = TRANSFER_TEXT)

            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `the pasted accounts stay hidden until the user asks for them`() = runMviTest {
        with(TransferPasteScreenRobot(mviContext = this)) {
            startScreen()
            userPastesText(TRANSFER_TEXT)

            userTogglesTextVisibility()

            assertTextIsRevealed()

            ensureThatAllEventsAreConsumed()
        }
    }
}
