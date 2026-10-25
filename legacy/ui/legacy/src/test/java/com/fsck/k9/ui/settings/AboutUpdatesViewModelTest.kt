package com.fsck.k9.ui.settings

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.thunderbird.feature.updater.api.AppUpdate
import net.thunderbird.feature.updater.api.AppUpdateChecker
import net.thunderbird.feature.updater.api.AppUpdateInstaller
import net.thunderbird.feature.updater.api.AppUpdateSettings
import net.thunderbird.feature.updater.api.UpdateCheckFailure
import net.thunderbird.feature.updater.api.UpdateCheckResult
import net.thunderbird.feature.updater.api.UpdateInstallFailure
import net.thunderbird.feature.updater.api.UpdateInstallState
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AboutUpdatesViewModelTest {
    private val checker = FakeAppUpdateChecker()
    private val settings = FakeAppUpdateSettings()
    private val installer = FakeAppUpdateInstaller()
    private val update = AppUpdate(
        tag = "mein-thunderbird-2026-10-26",
        title = "Mein Thunderbird 2026-10-26",
        releaseNotes = "Some release notes",
        apkUrl = "https://example.com/mein-thunderbird-2026-10-26.apk",
        apkSizeBytes = 36_349_629,
        apkSha256 = null,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `updates section is hidden when no update checker is available`() {
        val testSubject = createViewModel(checker = null)

        assertThat(testSubject.state.value.updates).isNull()
    }

    @Test
    fun `updates section is hidden when the update checker is disabled`() {
        val testSubject = createViewModel(checker = FakeAppUpdateChecker(isEnabled = false))

        assertThat(testSubject.state.value.updates).isNull()
    }

    @Test
    fun `updates section is hidden when the update settings are missing`() {
        val testSubject = createViewModel(settings = null)

        assertThat(testSubject.state.value.updates).isNull()
    }

    @Test
    fun `updates section is hidden when the update installer is missing`() {
        val testSubject = createViewModel(installer = null)

        assertThat(testSubject.state.value.updates).isNull()
    }

    @Test
    fun `check on startup finds an available update`() {
        checker.checkResult = UpdateCheckResult.Available(update)

        val testSubject = createViewModel()

        assertThat(testSubject.state.value.updates?.check).isEqualTo(CheckStatus.Available(update))
    }

    @Test
    fun `check on startup reports that the app is up to date`() {
        val testSubject = createViewModel()

        assertThat(testSubject.state.value.updates?.check).isEqualTo(CheckStatus.UpToDate)
    }

    @Test
    fun `check on startup reports the failure reason`() {
        checker.checkResult = UpdateCheckResult.Failed(UpdateCheckFailure.NETWORK)

        val testSubject = createViewModel()

        assertThat(testSubject.state.value.updates?.check).isEqualTo(CheckStatus.Failed(UpdateCheckFailure.NETWORK))
    }

    @Test
    fun `updates section shows the release tag of the installed version`() {
        val testSubject = createViewModel()

        assertThat(testSubject.state.value.updates?.currentVersionTag).isEqualTo("mein-thunderbird-2026-10-25")
    }

    @Test
    fun `checking again after a failure runs a new check`() {
        checker.checkResult = UpdateCheckResult.Failed(UpdateCheckFailure.SERVER)
        val testSubject = createViewModel()

        checker.checkResult = UpdateCheckResult.UpToDate
        testSubject.event(AboutContract.Event.OnCheckForUpdatesClick)

        assertThat(checker.checkCount).isEqualTo(2)
        assertThat(testSubject.state.value.updates?.check).isEqualTo(CheckStatus.UpToDate)
    }

    @Test
    fun `changing the auto check switch updates the settings`() {
        val testSubject = createViewModel()

        testSubject.event(AboutContract.Event.OnAutoCheckChanged(false))

        assertThat(settings.setAutoCheckEnabledCount).isEqualTo(1)
        assertThat(testSubject.state.value.updates?.autoCheckEnabled).isEqualTo(false)
    }

    @Test
    fun `installing an available update is forwarded to the installer`() {
        checker.checkResult = UpdateCheckResult.Available(update)
        val testSubject = createViewModel()

        testSubject.event(AboutContract.Event.OnInstallClick)

        assertThat(installer.installedUpdates).containsExactly(update)
    }

    @Test
    fun `installing is not possible without the install permission`() {
        checker.checkResult = UpdateCheckResult.Available(update)
        installer.canInstallPackages = false
        val testSubject = createViewModel()

        testSubject.event(AboutContract.Event.OnInstallClick)

        assertThat(installer.installedUpdates).isEmpty()
        assertThat(testSubject.state.value.updates?.canInstallPackages).isEqualTo(false)
    }

    @Test
    fun `installing an update again while waiting for the confirmation is forwarded to the installer`() {
        checker.checkResult = UpdateCheckResult.Available(update)
        val testSubject = createViewModel()
        installer.setInstallState(UpdateInstallState.WaitingForConfirmation)

        testSubject.event(AboutContract.Event.OnInstallClick)

        assertThat(installer.installedUpdates).containsExactly(update)
    }

    @Test
    fun `installing an update after a failed installation is forwarded to the installer`() {
        checker.checkResult = UpdateCheckResult.Available(update)
        val testSubject = createViewModel()
        installer.setInstallState(UpdateInstallState.Failed(UpdateInstallFailure.DOWNLOAD))

        testSubject.event(AboutContract.Event.OnInstallClick)

        assertThat(installer.installedUpdates).containsExactly(update)
    }

    @Test
    fun `installing is not possible while the update is being downloaded`() {
        checker.checkResult = UpdateCheckResult.Available(update)
        val testSubject = createViewModel()
        installer.setInstallState(UpdateInstallState.Downloading(downloadedBytes = 50, totalBytes = 100))

        testSubject.event(AboutContract.Event.OnInstallClick)

        assertThat(installer.installedUpdates).isEmpty()
    }

    @Test
    fun `cancelling a pending installation resets the state of the installer`() {
        installer.setInstallState(UpdateInstallState.WaitingForConfirmation)
        val testSubject = createViewModel()

        testSubject.event(AboutContract.Event.OnCancelInstallClick)

        assertThat(installer.resetStateCount).isEqualTo(1)
        assertThat(testSubject.state.value.updates?.install).isEqualTo(UpdateInstallState.Idle)
    }

    @Test
    fun `resuming the screen refreshes the install permission`() {
        val testSubject = createViewModel()
        assertThat(testSubject.state.value.updates?.canInstallPackages).isEqualTo(true)

        installer.canInstallPackages = false
        testSubject.event(AboutContract.Event.OnScreenResumed)

        assertThat(testSubject.state.value.updates?.canInstallPackages).isEqualTo(false)
    }

    @Test
    fun `the state of the installer is shown in the state`() {
        val testSubject = createViewModel()

        installer.setInstallState(UpdateInstallState.Downloading(downloadedBytes = 50, totalBytes = 100))

        assertThat(testSubject.state.value.updates?.install)
            .isEqualTo(UpdateInstallState.Downloading(downloadedBytes = 50, totalBytes = 100))
    }

    @Test
    fun `dismissing a failed installation resets the state of the installer`() {
        installer.setInstallState(UpdateInstallState.Failed(UpdateInstallFailure.DOWNLOAD))
        val testSubject = createViewModel()

        testSubject.event(AboutContract.Event.OnInstallFailureDismiss)

        assertThat(installer.resetStateCount).isEqualTo(1)
        assertThat(testSubject.state.value.updates?.install).isEqualTo(UpdateInstallState.Idle)
    }

    @Test
    fun `opening the install permission settings is forwarded to the installer`() {
        val testSubject = createViewModel()

        testSubject.event(AboutContract.Event.OnOpenInstallPermissionClick)

        assertThat(installer.openInstallPermissionSettingsCount).isEqualTo(1)
    }

    private fun createViewModel(
        checker: AppUpdateChecker? = this.checker,
        settings: AppUpdateSettings? = this.settings,
        installer: AppUpdateInstaller? = this.installer,
    ): AboutViewModel {
        return AboutViewModel(
            appVersionProvider = FakeAppVersionProvider(),
            updateChecker = checker,
            updateSettings = settings,
            updateInstaller = installer,
        )
    }
}
