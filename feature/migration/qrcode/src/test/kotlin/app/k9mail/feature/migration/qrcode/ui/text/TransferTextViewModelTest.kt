package app.k9mail.feature.migration.qrcode.ui.text

import app.k9mail.core.ui.compose.testing.mvi.runMviTest
import app.k9mail.feature.migration.qrcode.ui.text.TransferTextContract.Step
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import net.thunderbird.components.ui.testing.coroutines.MainDispatcherHelper
import net.thunderbird.components.core.logging.testing.TestLogger
import net.thunderbird.legacy.logging.Log

class TransferTextViewModelTest {

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
    fun `the screen shows the text of the transfer`() = runMviTest {
        with(TransferTextScreenRobot(mviContext = this)) {
            startScreen()

            assertText(TRANSFER_TEXT)

            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `a device without accounts has no text to show`() = runMviTest {
        with(TransferTextScreenRobot(mviContext = this, accounts = emptyList())) {
            startScreen()

            assertStep(Step.NoAccounts)

            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `the user copies the accounts to the clipboard`() = runMviTest {
        with(TransferTextScreenRobot(mviContext = this)) {
            startScreen()
            assertText(TRANSFER_TEXT)

            userClicksCopy()

            assertCopiedText(TRANSFER_TEXT)

            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `the accounts stay hidden until the user asks for them`() = runMviTest {
        with(TransferTextScreenRobot(mviContext = this)) {
            startScreen()
            assertText(TRANSFER_TEXT)

            userTogglesTextVisibility()
            assertTextIsRevealed()

            userClicksCopy()

            // The accounts are in the clipboard now, so the screen hides them again.
            assertCopiedText(TRANSFER_TEXT)

            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `the accounts are hidden again when another app comes in front`() = runMviTest {
        with(TransferTextScreenRobot(mviContext = this)) {
            startScreen()
            assertText(TRANSFER_TEXT)

            userTogglesTextVisibility()
            assertTextIsRevealed()

            screenGoesToBackground()
            assertTextIsHidden()

            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `the screen is closed when the user is done`() = runMviTest {
        with(TransferTextScreenRobot(mviContext = this)) {
            startScreen()
            assertText(TRANSFER_TEXT)

            userClicksDone()

            assertScreenWasClosed()

            ensureThatAllEventsAreConsumed()
        }
    }
}
