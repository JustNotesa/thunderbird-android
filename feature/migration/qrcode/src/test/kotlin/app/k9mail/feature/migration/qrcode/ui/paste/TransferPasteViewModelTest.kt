package app.k9mail.feature.migration.qrcode.ui.paste

import android.app.Application
import androidx.core.net.toUri
import app.k9mail.core.ui.compose.testing.mvi.MviContext
import app.k9mail.core.ui.compose.testing.mvi.MviTurbines
import app.k9mail.core.ui.compose.testing.mvi.runMviTest
import app.k9mail.core.ui.compose.testing.mvi.turbinesWithInitialStateCheck
import app.k9mail.feature.migration.qrcode.domain.usecase.QrCodePayloadReader
import app.k9mail.feature.migration.qrcode.payload.FakeDeletePolicyProvider
import app.k9mail.feature.migration.qrcode.payload.QrCodePayloadAdapter
import app.k9mail.feature.migration.qrcode.payload.QrCodePayloadMapper
import app.k9mail.feature.migration.qrcode.payload.QrCodePayloadParser
import app.k9mail.feature.migration.qrcode.payload.QrCodePayloadValidator
import app.k9mail.feature.migration.qrcode.transfer.TransferPayload
import app.k9mail.feature.migration.qrcode.ui.FakeQrCodeSettingsWriter
import app.k9mail.feature.migration.qrcode.ui.paste.TransferPasteContract.Effect
import app.k9mail.feature.migration.qrcode.ui.paste.TransferPasteContract.Event
import app.k9mail.feature.migration.qrcode.ui.paste.TransferPasteContract.PasteError
import app.k9mail.feature.migration.qrcode.ui.paste.TransferPasteContract.State
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import net.thunderbird.components.ui.testing.coroutines.MainDispatcherHelper
import net.thunderbird.core.logging.testing.TestLogger
import net.thunderbird.legacy.logging.Log
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val CREATED_AT = 1792300000L
private const val ACCOUNTS_JSON = """[1,[1,1],""" +
    """[0,"imap.domain.example",993,3,2,"username","My Account"],""" +
    """[[[0,"smtp.domain.example",587,2,1,"username"],""" +
    """["user@domain.example","Firstname Lastname"]]]]"""

private const val NEWER_ACCOUNTS_JSON = """[2,[1,1],""" +
    """[0,"imap.domain.example",993,3,2,"username","My Account"],""" +
    """[[[0,"smtp.domain.example",587,2,1,"username"],""" +
    """["user@domain.example","Firstname Lastname"]]]]"""

private val TRANSFER_TEXT = TransferPayload.write(accountsJson = ACCOUNTS_JSON, createdAt = CREATED_AT)
private val TRANSFER_TEXT_OF_NEWER_ACCOUNTS =
    TransferPayload.write(accountsJson = NEWER_ACCOUNTS_JSON, createdAt = CREATED_AT)
private val WRITTEN_SETTINGS_URI = "content://provider".toUri()

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
}

private class TransferPasteScreenRobot(private val mviContext: MviContext) {
    private val qrCodeSettingsWriter = FakeQrCodeSettingsWriter()
    private val viewModel = TransferPasteViewModel(
        qrCodePayloadReader = QrCodePayloadReader(
            parser = QrCodePayloadParser(QrCodePayloadAdapter()),
            mapper = QrCodePayloadMapper(
                qrCodePayloadValidator = QrCodePayloadValidator(),
                deletePolicyProvider = FakeDeletePolicyProvider(),
            ),
        ),
        qrCodeSettingsWriter = qrCodeSettingsWriter,
        backgroundDispatcher = Dispatchers.Unconfined,
    )
    private lateinit var turbines: MviTurbines<State, Effect>
    private var pastedText = ""

    suspend fun startScreen() {
        turbines = mviContext.turbinesWithInitialStateCheck(viewModel, State())
    }

    suspend fun userPastesText(text: String) {
        pastedText = text

        viewModel.event(Event.TextChanged(text))

        assertThat(turbines.awaitStateItem()).isEqualTo(State(text = text))
    }

    suspend fun userClicksPasteFromClipboard(clipboardText: String?) {
        viewModel.event(Event.PasteFromClipboardClicked(clipboardText))

        if (clipboardText != null) {
            assertThat(turbines.awaitStateItem()).isEqualTo(State(text = clipboardText))
        }
    }

    suspend fun userClicksImport() {
        viewModel.event(Event.ImportClicked)
    }

    suspend fun assertClipboardWasCleared() {
        assertThat(turbines.awaitEffectItem()).isEqualTo(Effect.ClearClipboard)
    }

    suspend fun assertImportResult(expectedNumberOfAccounts: Int) {
        assertThat(turbines.awaitEffectItem()).isEqualTo(Effect.ReturnResult(contentUri = WRITTEN_SETTINGS_URI))
        assertThat(turbines.awaitStateItem()).isEqualTo(State())
        assertThat(qrCodeSettingsWriter.arguments).isNotNull().hasSize(expectedNumberOfAccounts)
    }

    suspend fun assertError(error: PasteError) {
        assertThat(turbines.awaitStateItem()).isEqualTo(State(text = pastedText, error = error))
    }

    fun assertNothingWasImported() {
        assertThat(qrCodeSettingsWriter.arguments).isNull()
    }

    fun ensureThatAllEventsAreConsumed() {
        turbines.effectTurbine.ensureAllEventsConsumed()
        turbines.stateTurbine.ensureAllEventsConsumed()
    }
}
