package app.k9mail.feature.migration.qrcode.ui.send

import androidx.lifecycle.viewModelScope
import app.k9mail.core.ui.compose.testing.mvi.MviContext
import app.k9mail.core.ui.compose.testing.mvi.MviTurbines
import app.k9mail.core.ui.compose.testing.mvi.runMviTest
import app.k9mail.core.ui.compose.testing.mvi.turbinesWithInitialStateCheck
import app.k9mail.feature.migration.qrcode.transfer.LanTransferClient
import app.k9mail.feature.migration.qrcode.transfer.LanTransferServer
import app.k9mail.feature.migration.qrcode.transfer.LocalNetworkAddressProvider
import app.k9mail.feature.migration.qrcode.transfer.TransferAccount
import app.k9mail.feature.migration.qrcode.transfer.TransferAccountSource
import app.k9mail.feature.migration.qrcode.transfer.TransferIdentity
import app.k9mail.feature.migration.qrcode.transfer.TransferQrCode
import app.k9mail.feature.migration.qrcode.transfer.TransferReceiver
import app.k9mail.feature.migration.qrcode.transfer.TransferSender
import app.k9mail.feature.migration.qrcode.transfer.hotspot.FakeHotspotController
import app.k9mail.feature.migration.qrcode.transfer.hotspot.HotspotConnectResult
import app.k9mail.feature.migration.qrcode.transfer.hotspot.HotspotNetworkConnector
import app.k9mail.feature.migration.qrcode.transfer.hotspot.HotspotStartResult
import app.k9mail.feature.migration.qrcode.transfer.hotspot.UnsupportedHotspotNetworkConnector
import app.k9mail.feature.migration.qrcode.ui.send.TransferSendContract.Effect
import app.k9mail.feature.migration.qrcode.ui.send.TransferSendContract.Event
import app.k9mail.feature.migration.qrcode.ui.send.TransferSendContract.State
import app.k9mail.feature.migration.qrcode.ui.send.TransferSendContract.Step
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import com.fsck.k9.mail.AuthType
import com.fsck.k9.mail.ConnectionSecurity
import com.fsck.k9.mail.ServerSettings
import java.net.Inet4Address
import java.net.InetAddress
import javax.net.SocketFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withContext
import net.thunderbird.components.core.logging.testing.TestLogger
import net.thunderbird.components.ui.testing.coroutines.MainDispatcherHelper
import net.thunderbird.legacy.logging.Log

private const val SERVER_LIFETIME = 10_000L
private const val SHORT_LIFETIME = 300L
private const val ACCOUNT_NAME = "Personal"
private const val USERNAME = "username"
private const val CREATED_AT = 1792300000L
private const val HOTSPOT_SSID = "DIRECT-test"
private const val HOTSPOT_PASSPHRASE = "secret123"

private val LOOPBACK_ADDRESS = InetAddress.getLoopbackAddress() as Inet4Address

private val STARTED_HOTSPOT = HotspotStartResult.Started(
    ssid = HOTSPOT_SSID,
    passphrase = HOTSPOT_PASSPHRASE,
    address = LOOPBACK_ADDRESS,
)

class TransferSendViewModelTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val mainDispatcher = MainDispatcherHelper(UnconfinedTestDispatcher())

    private var robot: TransferSendScreenRobot? = null

    @BeforeTest
    fun setUp() {
        Log.logger = TestLogger()
        mainDispatcher.setUp()
    }

    /**
     * Waits until the transfer of the screen stopped before the dispatchers are torn down: the transfer is served on a
     * real thread and resumes on the main dispatcher as soon as its socket was closed.
     */
    @AfterTest
    fun tearDown() {
        runBlocking { robot?.leaveScreen() }

        mainDispatcher.tearDown()
    }

    @Test
    fun `the other device fetches the accounts of this device`() = runMviTest {
        with(screen(mviContext = this)) {
            startScreen()
            assertShowingQrCode()

            theOtherDeviceFetchesTheTransfer()

            assertStep(Step.Transferred)
            userClicksClose()

            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `nobody fetches the transfer before it expired`() = runMviTest {
        with(screen(mviContext = this, lifetimeMillis = SHORT_LIFETIME)) {
            startScreen()
            assertShowingQrCode()

            assertStep(Step.Expired)
            userClicksClose()

            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `the device isn't on a wifi network`() = runMviTest {
        with(screen(mviContext = this, wifiAddress = null)) {
            startScreen()

            assertStep(Step.NoWifi)
            userClicksClose()

            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `the device has no account to send`() = runMviTest {
        with(screen(mviContext = this, accounts = emptyList())) {
            startScreen()

            assertStep(Step.NoAccounts)
            userClicksClose()

            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `user retries when the wifi network became available`() = runMviTest {
        with(screen(mviContext = this, wifiAddress = null)) {
            startScreen()
            assertStep(Step.NoWifi)

            theWifiNetworkBecomesAvailable()
            userClicksRetry()
            assertShowingQrCode()

            userClicksClose()

            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `user closes the screen while the transfer is running`() = runMviTest {
        with(screen(mviContext = this)) {
            startScreen()
            assertShowingQrCode()

            userClicksClose()

            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `user chooses to send the accounts over the hotspot`() = runMviTest {
        with(screen(mviContext = this, wifiAddress = null)) {
            startScreen()
            assertStep(Step.NoWifi)

            userClicksUseHotspot()

            userClicksClose()

            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `user denies the permission to send the accounts over the hotspot`() = runMviTest {
        with(screen(mviContext = this, wifiAddress = null)) {
            startScreen()
            assertStep(Step.NoWifi)

            userClicksUseHotspot()
            userDeniesTheHotspotPermission()

            assertStep(Step.HotspotPermissionDenied)
            userClicksClose()

            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `the other device fetches the accounts over the hotspot of this device`() = runMviTest {
        with(
            screen(
                mviContext = this,
                wifiAddress = null,
                hotspotStartResult = STARTED_HOTSPOT,
                hotspotConnector = JoinedHotspotNetworkConnector,
            ),
        ) {
            startScreen()
            assertStep(Step.NoWifi)

            userClicksUseHotspot()
            userGrantsTheHotspotPermission()

            assertShowingQrCode(isHotspot = true)
            theOtherDeviceFetchesTheTransfer()

            assertStep(Step.Transferred)
            assertHotspotWasStopped()
            userClicksClose()

            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `the device can't run a hotspot for the transfer`() = runMviTest {
        with(
            screen(
                mviContext = this,
                wifiAddress = null,
                hotspotStartResult = HotspotStartResult.Unsupported,
            ),
        ) {
            startScreen()
            assertStep(Step.NoWifi)

            userClicksUseHotspot()
            userGrantsTheHotspotPermission()

            assertStep(Step.HotspotUnsupported)
            userClicksClose()

            ensureThatAllEventsAreConsumed()
        }
    }

    @Test
    fun `the hotspot of this device couldn't be started`() = runMviTest {
        with(
            screen(
                mviContext = this,
                wifiAddress = null,
                hotspotStartResult = HotspotStartResult.Failed,
            ),
        ) {
            startScreen()
            assertStep(Step.NoWifi)

            userClicksUseHotspot()
            userGrantsTheHotspotPermission()

            assertStep(Step.HotspotFailed)
            userClicksClose()

            ensureThatAllEventsAreConsumed()
        }
    }

    private fun screen(
        mviContext: MviContext,
        accounts: List<TransferAccount> = listOf(account()),
        wifiAddress: Inet4Address? = LOOPBACK_ADDRESS,
        lifetimeMillis: Long = SERVER_LIFETIME,
        hotspotStartResult: HotspotStartResult = HotspotStartResult.Unsupported,
        hotspotConnector: HotspotNetworkConnector = UnsupportedHotspotNetworkConnector,
    ): TransferSendScreenRobot {
        return TransferSendScreenRobot(
            mviContext = mviContext,
            accounts = accounts,
            wifiAddress = wifiAddress,
            lifetimeMillis = lifetimeMillis,
            hotspotStartResult = hotspotStartResult,
            hotspotConnector = hotspotConnector,
        ).also { robot = it }
    }
}

private class TransferSendScreenRobot(
    private val mviContext: MviContext,
    accounts: List<TransferAccount> = listOf(account()),
    wifiAddress: Inet4Address? = LOOPBACK_ADDRESS,
    lifetimeMillis: Long = SERVER_LIFETIME,
    hotspotStartResult: HotspotStartResult = HotspotStartResult.Unsupported,
    private val hotspotConnector: HotspotNetworkConnector = UnsupportedHotspotNetworkConnector,
) {
    private var address: Inet4Address? = wifiAddress
    private val hotspotController = FakeHotspotController(hotspotStartResult)
    private val viewModel = TransferSendViewModel(
        transferSender = TransferSender(
            accountSource = TransferAccountSource { accounts },
            addressProvider = LocalNetworkAddressProvider { address },
            hotspotController = hotspotController,
            clock = { CREATED_AT },
            createServer = { container, transferId, serverAddress ->
                LanTransferServer(
                    container = container,
                    transferId = transferId,
                    bindAddress = serverAddress,
                    lifetimeMillis = lifetimeMillis,
                )
            },
        ),
    )
    private lateinit var turbines: MviTurbines<State, Effect>
    private var qrCode: TransferQrCode? = null

    suspend fun startScreen() {
        turbines = mviContext.turbinesWithInitialStateCheck(viewModel, State())

        // Preparing is the initial state, so no state is emitted until the transfer is prepared.
        viewModel.event(Event.StartScreen)
    }

    suspend fun assertShowingQrCode(isHotspot: Boolean = false) {
        val step = awaitStep()

        assertThat(step).isInstanceOf<Step.ShowingQrCode>()
        val showingQrCode = step as Step.ShowingQrCode
        assertThat(showingQrCode.isHotspot).isEqualTo(isHotspot)
        qrCode = TransferQrCode.parse(showingQrCode.qrText)
        assertThat(qrCode).isNotNull()
    }

    suspend fun assertStep(expectedStep: Step) {
        assertThat(awaitStep()).isEqualTo(expectedStep)
    }

    fun assertHotspotWasStopped() {
        assertThat(hotspotController.stopCount).isEqualTo(1)
    }

    fun theOtherDeviceFetchesTheTransfer() {
        val transfer = checkNotNull(qrCode) { "The transfer was never shown as a QR code" }

        mviContext.testScope.async(Dispatchers.IO) {
            TransferReceiver(
                fetcher = LanTransferClient(),
                hotspotConnector = hotspotConnector,
            ).receive(transfer)
        }
    }

    fun theWifiNetworkBecomesAvailable() {
        address = LOOPBACK_ADDRESS
    }

    suspend fun userClicksRetry() {
        viewModel.event(Event.RetryClicked)

        assertStep(Step.Preparing)
    }

    suspend fun userClicksUseHotspot() {
        viewModel.event(Event.UseHotspotClicked)

        assertThat(awaitEffect()).isEqualTo(Effect.RequestHotspotPermission)
    }

    fun userDeniesTheHotspotPermission() {
        viewModel.event(Event.HotspotPermissionResult(granted = false))
    }

    suspend fun userGrantsTheHotspotPermission() {
        viewModel.event(Event.HotspotPermissionResult(granted = true))

        assertStep(Step.StartingHotspot)
    }

    suspend fun userClicksClose() {
        viewModel.event(Event.CloseClicked)

        assertThat(awaitEffect()).isEqualTo(Effect.Close)
    }

    fun ensureThatAllEventsAreConsumed() {
        turbines.effectTurbine.ensureAllEventsConsumed()
        turbines.stateTurbine.ensureAllEventsConsumed()
    }

    /**
     * Waits until the transfer stopped and cancels the scope of the view model, so no server thread outlives the test.
     */
    suspend fun leaveScreen() {
        viewModel.awaitIdle()
        viewModel.viewModelScope.cancel()
    }

    /**
     * The transfer is served by a real server on real threads, so the state is awaited with real time instead of the
     * virtual time of [runMviTest].
     */
    private suspend fun awaitStep(): Step {
        return withContext(Dispatchers.IO) { turbines.awaitStateItem() }.step
    }

    private suspend fun awaitEffect(): Effect {
        return withContext(Dispatchers.IO) { turbines.awaitEffectItem() }
    }
}

/**
 * Stands in for the receiving device that is already connected to the hotspot of the sending device, so the container
 * is fetched from the host and port of the QR code.
 */
private object JoinedHotspotNetworkConnector : HotspotNetworkConnector {
    override suspend fun connect(ssid: String, passphrase: String): HotspotConnectResult {
        return HotspotConnectResult.Connected(socketFactory = SocketFactory.getDefault(), release = {})
    }
}

private fun account(): TransferAccount {
    return TransferAccount(
        accountName = ACCOUNT_NAME,
        incoming = ServerSettings(
            type = "imap",
            host = "imap.domain.example",
            port = 993,
            connectionSecurity = ConnectionSecurity.SSL_TLS_REQUIRED,
            authenticationType = AuthType.CRAM_MD5,
            username = USERNAME,
            password = "imap-password",
            clientCertificateAlias = null,
        ),
        outgoing = ServerSettings(
            type = "smtp",
            host = "smtp.domain.example",
            port = 587,
            connectionSecurity = ConnectionSecurity.STARTTLS_REQUIRED,
            authenticationType = AuthType.PLAIN,
            username = USERNAME,
            password = "smtp-password",
            clientCertificateAlias = null,
        ),
        identities = listOf(
            TransferIdentity(emailAddress = "user@domain.example", displayName = "Firstname Lastname"),
        ),
    )
}
