package net.thunderbird.feature.updater.internal.install

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.updater.api.AppUpdate
import net.thunderbird.feature.updater.api.AppUpdateInstaller
import net.thunderbird.feature.updater.api.UpdateInstallState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InstallDownloadedReceiverTest {

    @Test
    fun `the downloaded update is installed`() = runTest {
        // Arrange
        val installer = FakeInstallDownloadedInstaller()
        val testSubject = InstallDownloadedReceiver()

        // Act
        testSubject.handleInstallDownloaded(installer)

        // Assert
        assertThat(installer.installed).isEqualTo(1)
    }
}

private class FakeInstallDownloadedInstaller : AppUpdateInstaller {
    private val _state = MutableStateFlow<UpdateInstallState>(UpdateInstallState.Idle)
    override val state: StateFlow<UpdateInstallState> = _state

    var installed: Int = 0

    override fun canInstallPackages(): Boolean = true

    override fun openInstallPermissionSettings() = Unit

    override suspend fun install(update: AppUpdate) = Unit

    override suspend fun download(update: AppUpdate, onlyOnWifi: Boolean) = Unit

    override suspend fun installDownloaded() {
        installed++
    }

    override suspend fun refresh() = Unit

    override fun resetState() {
        _state.value = UpdateInstallState.Idle
    }
}
