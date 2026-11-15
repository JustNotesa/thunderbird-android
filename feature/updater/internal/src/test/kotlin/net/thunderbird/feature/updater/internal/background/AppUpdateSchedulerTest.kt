package net.thunderbird.feature.updater.internal.background

import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.updater.api.AppUpdate
import net.thunderbird.feature.updater.api.AppUpdateChecker
import net.thunderbird.feature.updater.api.AppUpdateInstaller
import net.thunderbird.feature.updater.api.AppUpdateSettings
import net.thunderbird.feature.updater.api.ReleaseNotesResult
import net.thunderbird.feature.updater.api.UpdateCheckResult
import net.thunderbird.feature.updater.api.UpdateInstallState
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AppUpdateSchedulerTest {
    private val context = RuntimeEnvironment.getApplication()

    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        workManager = WorkManager.getInstance(context)
    }

    @Test
    fun `update check is scheduled when the updater is enabled`() = runTest {
        // Arrange
        val settings = FakeAppUpdateSettings(autoCheckEnabled = true)
        val checker = FakeAppUpdateChecker(isEnabled = true)
        val coroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val testSubject = createScheduler(settings, checker, FakeAppUpdateInstaller(), coroutineScope)

        // Act
        testSubject.start()

        // Assert
        val workInfos = workManager.getWorkInfosForUniqueWork(AppUpdateScheduler.UNIQUE_WORK_NAME).get()
        assertThat(workInfos).isNotEmpty()

        coroutineScope.cancel()
    }

    @Test
    fun `no update check is scheduled when the updater is disabled`() = runTest {
        // Arrange
        val settings = FakeAppUpdateSettings(autoCheckEnabled = true)
        val checker = FakeAppUpdateChecker(isEnabled = false)
        val coroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val testSubject = createScheduler(settings, checker, FakeAppUpdateInstaller(), coroutineScope)

        // Act
        testSubject.start()

        // Assert
        val workInfos = workManager.getWorkInfosForUniqueWork(AppUpdateScheduler.UNIQUE_WORK_NAME).get()
        assertThat(workInfos).isEmpty()

        coroutineScope.cancel()
    }

    @Test
    fun `update check is cancelled when the automatic check is switched off`() = runTest {
        // Arrange
        val settings = FakeAppUpdateSettings(autoCheckEnabled = true)
        val checker = FakeAppUpdateChecker(isEnabled = true)
        val coroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val testSubject = createScheduler(settings, checker, FakeAppUpdateInstaller(), coroutineScope)
        testSubject.start()

        // Act
        settings.setAutoCheckEnabled(false)

        // Assert
        val workInfos = workManager.getWorkInfosForUniqueWork(AppUpdateScheduler.UNIQUE_WORK_NAME).get()
        assertThat(workInfos.filter { it.state == WorkInfo.State.ENQUEUED }).isEmpty()

        coroutineScope.cancel()
    }

    @Test
    fun `the update check needs an unmetered network when the download is only allowed over wifi`() = runTest {
        // Arrange
        val settings = FakeAppUpdateSettings(
            autoCheckEnabled = true,
            backgroundDownloadEnabled = true,
            downloadOnlyOnWifi = true,
        )
        val checker = FakeAppUpdateChecker(isEnabled = true)
        val coroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val testSubject = createScheduler(settings, checker, FakeAppUpdateInstaller(), coroutineScope)

        // Act
        testSubject.start()

        // Assert
        val workInfo = workManager.getWorkInfosForUniqueWork(AppUpdateScheduler.UNIQUE_WORK_NAME).get().first()
        assertThat(workInfo.constraints.requiredNetworkType).isEqualTo(NetworkType.UNMETERED)

        coroutineScope.cancel()
    }

    @Test
    fun `the update check needs any network when the download may run over a metered network`() = runTest {
        // Arrange
        val settings = FakeAppUpdateSettings(
            autoCheckEnabled = true,
            backgroundDownloadEnabled = true,
            downloadOnlyOnWifi = false,
        )
        val checker = FakeAppUpdateChecker(isEnabled = true)
        val coroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val testSubject = createScheduler(settings, checker, FakeAppUpdateInstaller(), coroutineScope)

        // Act
        testSubject.start()

        // Assert
        val workInfo = workManager.getWorkInfosForUniqueWork(AppUpdateScheduler.UNIQUE_WORK_NAME).get().first()
        assertThat(workInfo.constraints.requiredNetworkType).isEqualTo(NetworkType.CONNECTED)

        coroutineScope.cancel()
    }

    @Test
    fun `the automatic installation is scheduled when the update is ready`() = runTest {
        // Arrange
        val settings = FakeAppUpdateSettings(
            autoCheckEnabled = true,
            backgroundDownloadEnabled = true,
            autoInstallWhenIdle = true,
        )
        val installer = FakeAppUpdateInstaller()
        installer.setState(UpdateInstallState.ReadyToInstall(RELEASE_TAG))
        val coroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val testSubject = createScheduler(settings, FakeAppUpdateChecker(isEnabled = true), installer, coroutineScope)

        // Act
        testSubject.start()

        // Assert
        val workInfos = workManager.getWorkInfosForUniqueWork(AppUpdateScheduler.AUTO_INSTALL_WORK_NAME).get()
        assertThat(workInfos).isNotEmpty()

        coroutineScope.cancel()
    }

    @Test
    fun `the automatic installation waits for the idle device and the charger`() = runTest {
        // Arrange
        val settings = FakeAppUpdateSettings(
            autoCheckEnabled = true,
            backgroundDownloadEnabled = true,
            autoInstallWhenIdle = true,
        )
        val installer = FakeAppUpdateInstaller()
        installer.setState(UpdateInstallState.ReadyToInstall(RELEASE_TAG))
        val coroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val testSubject = createScheduler(settings, FakeAppUpdateChecker(isEnabled = true), installer, coroutineScope)

        // Act
        testSubject.start()

        // Assert
        val workInfo = workManager.getWorkInfosForUniqueWork(AppUpdateScheduler.AUTO_INSTALL_WORK_NAME).get().first()
        assertThat(workInfo.constraints.requiresCharging()).isEqualTo(true)
        assertThat(workInfo.constraints.requiresDeviceIdle()).isEqualTo(true)

        coroutineScope.cancel()
    }

    @Test
    fun `the automatic installation is cancelled when the update isn't ready anymore`() = runTest {
        // Arrange
        val settings = FakeAppUpdateSettings(
            autoCheckEnabled = true,
            backgroundDownloadEnabled = true,
            autoInstallWhenIdle = true,
        )
        val installer = FakeAppUpdateInstaller()
        installer.setState(UpdateInstallState.ReadyToInstall(RELEASE_TAG))
        val coroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val testSubject = createScheduler(settings, FakeAppUpdateChecker(isEnabled = true), installer, coroutineScope)
        testSubject.start()

        // Act
        installer.setState(UpdateInstallState.Idle)

        // Assert
        val workInfos = workManager.getWorkInfosForUniqueWork(AppUpdateScheduler.AUTO_INSTALL_WORK_NAME).get()
        assertThat(workInfos.filter { it.state == WorkInfo.State.ENQUEUED }).isEmpty()

        coroutineScope.cancel()
    }

    @Test
    fun `no automatic installation is scheduled when the automatic installation is switched off`() = runTest {
        // Arrange
        val settings = FakeAppUpdateSettings(
            autoCheckEnabled = true,
            backgroundDownloadEnabled = true,
            autoInstallWhenIdle = false,
        )
        val installer = FakeAppUpdateInstaller()
        installer.setState(UpdateInstallState.ReadyToInstall(RELEASE_TAG))
        val coroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val testSubject = createScheduler(settings, FakeAppUpdateChecker(isEnabled = true), installer, coroutineScope)

        // Act
        testSubject.start()

        // Assert
        val workInfos = workManager.getWorkInfosForUniqueWork(AppUpdateScheduler.AUTO_INSTALL_WORK_NAME).get()
        assertThat(workInfos).isEmpty()

        coroutineScope.cancel()
    }

    private fun createScheduler(
        settings: FakeAppUpdateSettings,
        checker: FakeAppUpdateChecker,
        installer: FakeAppUpdateInstaller,
        coroutineScope: CoroutineScope,
    ): AppUpdateScheduler {
        return AppUpdateScheduler(context, settings, checker, installer, coroutineScope)
    }
}

private const val RELEASE_TAG = "mein-thunderbird-2026-10-25"

private class FakeAppUpdateChecker(
    override val isEnabled: Boolean,
    override val currentReleaseTag: String = "mein-thunderbird-2026-10-25",
) : AppUpdateChecker {
    override suspend fun check(): UpdateCheckResult = UpdateCheckResult.UpToDate

    override suspend fun installedReleaseNotes(): ReleaseNotesResult = ReleaseNotesResult.Disabled
}

private class FakeAppUpdateSettings(
    autoCheckEnabled: Boolean,
    backgroundDownloadEnabled: Boolean = false,
    downloadOnlyOnWifi: Boolean = true,
    autoInstallWhenIdle: Boolean = false,
) : AppUpdateSettings {
    private val autoCheckEnabledFlow = MutableStateFlow(autoCheckEnabled)
    private val backgroundDownloadEnabledFlow = MutableStateFlow(backgroundDownloadEnabled)
    private val downloadOnlyOnWifiFlow = MutableStateFlow(downloadOnlyOnWifi)
    private val autoInstallWhenIdleFlow = MutableStateFlow(autoInstallWhenIdle)

    override val autoCheckEnabled: StateFlow<Boolean> = autoCheckEnabledFlow.asStateFlow()

    override fun setAutoCheckEnabled(enabled: Boolean) {
        autoCheckEnabledFlow.value = enabled
    }

    override val backgroundDownloadEnabled: StateFlow<Boolean> = backgroundDownloadEnabledFlow.asStateFlow()

    override fun setBackgroundDownloadEnabled(enabled: Boolean) {
        backgroundDownloadEnabledFlow.value = enabled
    }

    override val downloadOnlyOnWifi: StateFlow<Boolean> = downloadOnlyOnWifiFlow.asStateFlow()

    override fun setDownloadOnlyOnWifi(enabled: Boolean) {
        downloadOnlyOnWifiFlow.value = enabled
    }

    override val autoInstallWhenIdle: StateFlow<Boolean> = autoInstallWhenIdleFlow.asStateFlow()

    override fun setAutoInstallWhenIdle(enabled: Boolean) {
        autoInstallWhenIdleFlow.value = enabled
    }
}

private class FakeAppUpdateInstaller : AppUpdateInstaller {
    private val _state = MutableStateFlow<UpdateInstallState>(UpdateInstallState.Idle)
    override val state: StateFlow<UpdateInstallState> = _state

    fun setState(state: UpdateInstallState) {
        _state.value = state
    }

    override fun canInstallPackages(): Boolean = true

    override fun openInstallPermissionSettings() = Unit

    override suspend fun install(update: AppUpdate) = Unit

    override suspend fun download(update: AppUpdate, onlyOnWifi: Boolean) = Unit

    override suspend fun installDownloaded() = Unit

    override suspend fun refresh() = Unit

    override fun resetState() {
        _state.value = UpdateInstallState.Idle
    }
}
