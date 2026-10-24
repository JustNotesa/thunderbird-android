package app.k9mail.feature.migration.qrcode.ui.auth

import app.k9mail.core.ui.compose.testing.mvi.runMviTest
import app.k9mail.feature.migration.qrcode.transfer.auth.passwordAccount
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import net.thunderbird.components.ui.testing.coroutines.MainDispatcherHelper
import net.thunderbird.components.core.logging.testing.TestLogger
import net.thunderbird.legacy.logging.Log

internal const val NOW = 1_792_300_000_000L
internal const val LOCK_OUT_MILLIS = 30_000L
internal const val ACCOUNT_ID = "account-1"
internal const val PASSWORD = "imap-password"
internal const val WRONG_PASSWORD = "wrong-password"

class TransferAuthorizationViewModelTest {

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
    fun `a device with a screen lock asks for the device authentication`() = runMviTest {
        with(TransferAuthorizationRobot(mviContext = this, isDeviceSecure = true)) {
            startScreen()

            assertAuthenticationIsRequested()
            userAuthenticatesSuccessfully()

            assertTransferIsAuthorized()
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `a failed device authentication leaves the accounts on this device`() = runMviTest {
        with(TransferAuthorizationRobot(mviContext = this, isDeviceSecure = true)) {
            startScreen()
            assertAuthenticationIsRequested()

            userFailsAuthentication()

            assertTransferIsDenied()
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `a device without a screen lock asks for the password of an account`() = runMviTest {
        with(TransferAuthorizationRobot(mviContext = this)) {
            startScreen()

            assertPasswordFormIsShown()

            userEntersPassword(WRONG_PASSWORD)
            userClicksContinue()

            assertWrongPassword(remainingAttempts = 4)
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `the correct password authorizes the transfer`() = runMviTest {
        with(TransferAuthorizationRobot(mviContext = this)) {
            startScreen()
            assertPasswordFormIsShown()

            userEntersPassword(PASSWORD)
            userClicksContinue()

            assertTransferIsAuthorized()
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `five wrong passwords lock the account out`() = runMviTest {
        with(TransferAuthorizationRobot(mviContext = this)) {
            startScreen()
            assertPasswordFormIsShown()

            repeat(4) { attempt ->
                userEntersPassword(WRONG_PASSWORD)
                userClicksContinue()

                assertWrongPassword(remainingAttempts = 4 - attempt)
            }
            userEntersPassword(WRONG_PASSWORD)
            userClicksContinue()

            assertAccountIsLockedOut()
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `a device without accounts that have a password is authorized without asking`() = runMviTest {
        with(TransferAuthorizationRobot(mviContext = this, accounts = listOf(passwordAccount() to null))) {
            startScreen()

            assertTransferIsAuthorized()
            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `the screen is closed when the user cancels the password form`() = runMviTest {
        with(TransferAuthorizationRobot(mviContext = this)) {
            startScreen()
            assertPasswordFormIsShown()

            userCancels()

            assertScreenWasClosed()
            ensureThatAllEventsAreConsumed()
        }
    }
}
