package app.k9mail.feature.migration.qrcode.ui

import android.app.Application
import app.k9mail.core.ui.compose.testing.mvi.MviContext
import app.k9mail.core.ui.compose.testing.mvi.MviTurbines
import app.k9mail.core.ui.compose.testing.mvi.runMviTest
import app.k9mail.core.ui.compose.testing.mvi.turbinesWithInitialStateCheck
import app.k9mail.feature.migration.qrcode.domain.QrCodeDomainContract.UseCase
import app.k9mail.feature.migration.qrcode.domain.usecase.QrCodePayloadReader
import app.k9mail.feature.migration.qrcode.payload.FakeDeletePolicyProvider
import app.k9mail.feature.migration.qrcode.payload.QrCodePayloadAdapter
import app.k9mail.feature.migration.qrcode.payload.QrCodePayloadMapper
import app.k9mail.feature.migration.qrcode.payload.QrCodePayloadParser
import app.k9mail.feature.migration.qrcode.payload.QrCodePayloadValidator
import app.k9mail.feature.migration.qrcode.transfer.LanTransferClientResult
import app.k9mail.feature.migration.qrcode.transfer.TransferContainer
import app.k9mail.feature.migration.qrcode.transfer.TransferContainerFetcher
import app.k9mail.feature.migration.qrcode.transfer.TransferPayload
import app.k9mail.feature.migration.qrcode.transfer.TransferQrCode
import app.k9mail.feature.migration.qrcode.transfer.TransferReceiver
import app.k9mail.feature.migration.qrcode.transfer.TransferRoute
import app.k9mail.feature.migration.qrcode.transfer.TransferSecrets
import app.k9mail.feature.migration.qrcode.ui.QrCodeScannerContract.DisplayText
import app.k9mail.feature.migration.qrcode.ui.QrCodeScannerContract.Effect
import app.k9mail.feature.migration.qrcode.ui.QrCodeScannerContract.Event
import app.k9mail.feature.migration.qrcode.ui.QrCodeScannerContract.State
import app.k9mail.feature.migration.qrcode.ui.QrCodeScannerContract.TransferFailureReason
import app.k9mail.feature.migration.qrcode.ui.QrCodeScannerContract.UiPermissionState
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
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

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class QrCodeScannerViewModelTest {

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
    fun `user grants camera permission`() = runMviTest {
        with(QrCodeScannerScreenRobot(mviContext = this)) {
            startScreen()
            userGrantsCameraPermission()

            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `user denies camera permission`() = runMviTest {
        with(QrCodeScannerScreenRobot(mviContext = this)) {
            startScreen()
            userDeniesCameraPermission()

            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `user grants camera permission via android app settings`() = runMviTest {
        with(QrCodeScannerScreenRobot(mviContext = this)) {
            startScreen()
            systemDeniesCameraPermission()

            userClicksGoToSettings()
            userReturnsFromAppInfoScreen()
            systemGrantsCameraPermission()

            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `user successfully scans one QR code`() = runMviTest {
        with(QrCodeScannerScreenRobot(mviContext = this)) {
            startScreen()
            systemGrantsCameraPermission()

            userScansQrCode(sequenceNumber = 1, sequenceEnd = 1)
            assertScannedStatus(expectedScannedCount = 1, expectedScannedTotal = 1)

            assertScanResult(expectedNumberOfAccounts = 1)

            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `user successfully scans two QR codes`() = runMviTest {
        with(QrCodeScannerScreenRobot(mviContext = this)) {
            startScreen()
            systemGrantsCameraPermission()

            userScansQrCode(sequenceNumber = 1, sequenceEnd = 2)
            assertScannedStatus(expectedScannedCount = 1, expectedScannedTotal = 2)

            userScansQrCode(sequenceNumber = 2, sequenceEnd = 2)
            assertScannedStatus(expectedScannedCount = 2, expectedScannedTotal = 2)

            assertScanResult(expectedNumberOfAccounts = 2)

            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `user clicks Done button after one of two QR codes was scanned`() = runMviTest {
        with(QrCodeScannerScreenRobot(mviContext = this)) {
            startScreen()
            systemGrantsCameraPermission()

            userScansQrCode(sequenceNumber = 1, sequenceEnd = 2)
            assertScannedStatus(expectedScannedCount = 1, expectedScannedTotal = 2)

            userClicksDoneButton()
            assertScanResult(expectedNumberOfAccounts = 1)

            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `user clicks Done button without any QR codes having been successfully scanned`() = runMviTest {
        with(QrCodeScannerScreenRobot(mviContext = this)) {
            startScreen()
            systemGrantsCameraPermission()

            userClicksDoneButton()
            assertScanCancel()

            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `user scans unsupported QR code`() = runMviTest {
        with(QrCodeScannerScreenRobot(mviContext = this)) {
            startScreen()
            systemGrantsCameraPermission()

            userScansUnsupportedQrCode()
            assertUnsupportedQrCodeStatus()

            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `user scans supported QR code after unsupported QR code`() = runMviTest {
        with(QrCodeScannerScreenRobot(mviContext = this)) {
            startScreen()
            systemGrantsCameraPermission()

            userScansUnsupportedQrCode()
            assertUnsupportedQrCodeStatus()

            userScansQrCode(sequenceNumber = 1, sequenceEnd = 2)
            assertScannedStatus(expectedScannedCount = 1, expectedScannedTotal = 2)

            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `user scans same unsupported QR code more than once`() = runMviTest {
        with(QrCodeScannerScreenRobot(mviContext = this)) {
            startScreen()
            systemGrantsCameraPermission()

            userScansUnsupportedQrCode()
            assertUnsupportedQrCodeStatus()

            userScansUnsupportedQrCode()
            assertCurrentState(
                State(
                    cameraPermissionState = UiPermissionState.Granted,
                    displayText = DisplayText.UnsupportedQrCode,
                ),
            )

            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `user scans the QR code of an account transfer`() = runMviTest {
        val robot = QrCodeScannerScreenRobot(
            mviContext = this,
            transferResultOfContainer = { LanTransferClientResult.Success(it) },
        )
        with(robot) {
            startScreen()
            systemGrantsCameraPermission()

            userScansTransferQrCode()
            assertReceivingTransferStatus()
            assertScannedStatus(expectedScannedCount = 1, expectedScannedTotal = 1)

            assertScanResult(expectedNumberOfAccounts = 1)
            assertNumberOfReceivedTransfers(expectedCount = 1)

            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `user scans the QR code of an account transfer of an unreachable device`() = runMviTest {
        with(QrCodeScannerScreenRobot(mviContext = this)) {
            startScreen()
            systemGrantsCameraPermission()

            userScansTransferQrCode()
            assertReceivingTransferStatus()
            assertTransferFailedStatus(expectedReason = TransferFailureReason.Unreachable)

            // Scanning the same QR code again while the previous failure is still fresh must not start another
            // transfer, otherwise the failure message is immediately replaced again.
            userScansTransferQrCode()
            assertCurrentState(transferFailedState(TransferFailureReason.Unreachable))
            assertNumberOfReceivedTransfers(expectedCount = 1)

            timePassesBy(TRANSFER_RETRY_DELAY)

            userScansTransferQrCode()
            assertReceivingTransferStatus()
            assertTransferFailedStatus(expectedReason = TransferFailureReason.Unreachable)

            assertNumberOfReceivedTransfers(expectedCount = 2)

            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `user scans same transfer QR code repeatedly after a failed transfer`() = runMviTest {
        with(QrCodeScannerScreenRobot(mviContext = this)) {
            startScreen()
            systemGrantsCameraPermission()

            userScansTransferQrCode()
            assertReceivingTransferStatus()
            assertTransferFailedStatus(expectedReason = TransferFailureReason.Unreachable)

            repeat(NUMBER_OF_ADDITIONAL_SCANS) {
                userScansTransferQrCode()
            }

            assertCurrentState(transferFailedState(TransferFailureReason.Unreachable))
            assertNumberOfReceivedTransfers(expectedCount = 1)

            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `user scans an invalid QR code of an account transfer`() = runMviTest {
        with(QrCodeScannerScreenRobot(mviContext = this)) {
            startScreen()
            systemGrantsCameraPermission()

            userScansInvalidTransferQrCode()
            assertUnsupportedQrCodeStatus()

            assertNumberOfReceivedTransfers(expectedCount = 0)

            ensureThatAllEventsAreConsumed()
        }
    }

    private fun transferFailedState(reason: TransferFailureReason): State {
        return State(
            cameraPermissionState = UiPermissionState.Granted,
            displayText = DisplayText.TransferFailed(reason = reason),
        )
    }
}

private class QrCodeScannerScreenRobot(
    private val mviContext: MviContext,
    private val transferResultOfContainer: (ByteArray) -> LanTransferClientResult = {
        LanTransferClientResult.Unreachable
    },
    private val clock: FakeClock = FakeClock(),
) {
    private val qrCodeSettingsWriter = FakeQrCodeSettingsWriter()
    private var transferReceiveCount = 0
    private val viewModel = QrCodeScannerViewModel(
        qrCodePayloadReader = QrCodePayloadReader(
            parser = QrCodePayloadParser(QrCodePayloadAdapter()),
            mapper = QrCodePayloadMapper(
                qrCodePayloadValidator = QrCodePayloadValidator(),
                deletePolicyProvider = FakeDeletePolicyProvider(),
            ),
        ),
        qrCodeSettingsWriter = qrCodeSettingsWriter,
        transferReceiver = TransferReceiver(
            fetcher = TransferContainerFetcher { _, _ ->
                transferReceiveCount++
                transferResultOfContainer(TRANSFER_CONTAINER)
            },
        ),
        createCameraUseCaseProvider = { listener ->
            qrCodeListener = listener
            UseCase.CameraUseCasesProvider { emptyList() }
        },
        backgroundDispatcher = Dispatchers.Unconfined,
        clock = clock::currentTimeMillis,
    )
    private lateinit var qrCodeListener: (String) -> Unit
    private lateinit var turbines: MviTurbines<State, Effect>

    private val initialState = State()

    suspend fun startScreen() {
        turbines = mviContext.turbinesWithInitialStateCheck(viewModel, initialState)

        viewModel.event(Event.StartScreen)

        assertThat(turbines.awaitEffectItem()).isEqualTo(Effect.RequestCameraPermission)
    }

    suspend fun userGrantsCameraPermission() {
        grantCameraPermission()
    }

    suspend fun systemGrantsCameraPermission() {
        grantCameraPermission()
    }

    private suspend fun grantCameraPermission() {
        viewModel.event(Event.CameraPermissionResult(success = true))

        assertThat(turbines.awaitStateItem()).isEqualTo(State(cameraPermissionState = UiPermissionState.Granted))
    }

    suspend fun userDeniesCameraPermission() {
        denyCameraPermission()
    }

    suspend fun systemDeniesCameraPermission() {
        denyCameraPermission()
    }

    private suspend fun denyCameraPermission() {
        viewModel.event(Event.CameraPermissionResult(success = false))

        assertThat(turbines.awaitStateItem()).isEqualTo(State(cameraPermissionState = UiPermissionState.Denied))
    }

    suspend fun userClicksGoToSettings() {
        viewModel.event(Event.GoToSettingsClicked)

        assertThat(turbines.awaitEffectItem()).isEqualTo(Effect.GoToAppInfoScreen)
        assertThat(turbines.awaitStateItem()).isEqualTo(State(cameraPermissionState = UiPermissionState.Waiting))
    }

    suspend fun userReturnsFromAppInfoScreen() {
        viewModel.event(Event.ReturnedFromAppInfoScreen)

        assertThat(turbines.awaitStateItem()).isEqualTo(State(cameraPermissionState = UiPermissionState.Unknown))
        assertThat(turbines.awaitEffectItem()).isEqualTo(Effect.RequestCameraPermission)
    }

    fun userScansQrCode(sequenceNumber: Int, sequenceEnd: Int) {
        val payload = """[1,[$sequenceNumber,$sequenceEnd],""" +
            """[0,"imap.domain.example",993,3,2,"username","My Account"],""" +
            """[[[0,"smtp.domain.example",587,2,1,"username"],""" +
            """["user@domain.example","Firstname Lastname"]]]]"""

        qrCodeListener.invoke(payload)
    }

    fun userScansUnsupportedQrCode() {
        qrCodeListener.invoke("Unsupported QR code payload")
    }

    fun userScansTransferQrCode() {
        qrCodeListener.invoke(TRANSFER_QR_CODE_TEXT)
    }

    fun userScansInvalidTransferQrCode() {
        qrCodeListener.invoke("TBX1:invalid-transfer")
    }

    fun timePassesBy(duration: Long) {
        clock.advanceBy(duration)
    }

    fun userClicksDoneButton() {
        viewModel.event(Event.DoneClicked)
    }

    suspend fun assertScannedStatus(expectedScannedCount: Int, expectedScannedTotal: Int) {
        assertThat(turbines.awaitStateItem()).isEqualTo(
            State(
                cameraPermissionState = UiPermissionState.Granted,
                displayText = DisplayText.ProgressText(
                    scannedCount = expectedScannedCount,
                    totalCount = expectedScannedTotal,
                ),
            ),
        )
    }

    suspend fun assertUnsupportedQrCodeStatus() {
        assertThat(turbines.awaitStateItem()).isEqualTo(
            State(
                cameraPermissionState = UiPermissionState.Granted,
                displayText = DisplayText.UnsupportedQrCode,
            ),
        )
    }

    suspend fun assertReceivingTransferStatus() {
        assertThat(turbines.awaitStateItem()).isEqualTo(
            State(
                cameraPermissionState = UiPermissionState.Granted,
                displayText = DisplayText.ReceivingTransfer,
            ),
        )
    }

    suspend fun assertTransferFailedStatus(expectedReason: TransferFailureReason) {
        assertThat(turbines.awaitStateItem()).isEqualTo(
            State(
                cameraPermissionState = UiPermissionState.Granted,
                displayText = DisplayText.TransferFailed(reason = expectedReason),
            ),
        )
    }

    fun assertCurrentState(expectedState: State) {
        assertThat(viewModel.state.value).isEqualTo(expectedState)
    }

    suspend fun assertScanResult(expectedNumberOfAccounts: Int) {
        assertThat(turbines.awaitEffectItem()).isInstanceOf<Effect.ReturnResult>()
        assertThat(qrCodeSettingsWriter.arguments).isNotNull().hasSize(expectedNumberOfAccounts)
    }

    suspend fun assertScanCancel() {
        assertThat(turbines.awaitEffectItem()).isInstanceOf<Effect.Cancel>()
        assertThat(qrCodeSettingsWriter.arguments).isNull()
    }

    fun assertNumberOfReceivedTransfers(expectedCount: Int) {
        assertThat(transferReceiveCount).isEqualTo(expectedCount)
    }

    fun ensureThatAllEventsAreConsumed() {
        turbines.effectTurbine.ensureAllEventsConsumed()
        turbines.stateTurbine.ensureAllEventsConsumed()
    }

    private companion object {
        private const val CREATED_AT = 1792300000L
        private const val ACCOUNTS_JSON = """[1,[1,1],""" +
            """[0,"imap.domain.example",993,3,2,"username","My Account"],""" +
            """[[[0,"smtp.domain.example",587,2,1,"username"],""" +
            """["user@domain.example","Firstname Lastname"]]]]"""
        private const val HOST = "192.168.0.2"
        private const val PORT = 49152
        private const val TRANSFER_ID_LENGTH = 16
        private const val KEY_LENGTH = 32

        private val TRANSFER_SECRETS = TransferSecrets(
            transferId = ByteArray(TRANSFER_ID_LENGTH) { it.toByte() },
            key = ByteArray(KEY_LENGTH) { it.toByte() },
        )

        private val TRANSFER_QR_CODE_TEXT = TransferQrCode(
            secrets = TRANSFER_SECRETS,
            route = TransferRoute.Lan(host = HOST, port = PORT),
        ).format()

        private val TRANSFER_CONTAINER = TransferContainer.encrypt(
            plaintext = TransferPayload.write(
                accountsJson = ACCOUNTS_JSON,
                createdAt = CREATED_AT,
            ).encodeToByteArray(),
            secrets = TRANSFER_SECRETS,
        )
    }
}

private class FakeClock(private var currentTime: Long = 0L) {
    fun currentTimeMillis(): Long = currentTime

    fun advanceBy(duration: Long) {
        currentTime += duration
    }
}

private const val TRANSFER_RETRY_DELAY = 10_000L
private const val NUMBER_OF_ADDITIONAL_SCANS = 5
