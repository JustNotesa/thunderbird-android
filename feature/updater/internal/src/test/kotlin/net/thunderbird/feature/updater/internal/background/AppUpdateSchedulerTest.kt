package net.thunderbird.feature.updater.internal.background

import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isNotEmpty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.updater.api.AppUpdateChecker
import net.thunderbird.feature.updater.api.AppUpdateSettings
import net.thunderbird.feature.updater.api.UpdateCheckResult
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
        val testSubject = AppUpdateScheduler(context, settings, checker, coroutineScope)

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
        val testSubject = AppUpdateScheduler(context, settings, checker, coroutineScope)

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
        val testSubject = AppUpdateScheduler(context, settings, checker, coroutineScope)
        testSubject.start()

        // Act
        settings.setAutoCheckEnabled(false)

        // Assert
        val workInfos = workManager.getWorkInfosForUniqueWork(AppUpdateScheduler.UNIQUE_WORK_NAME).get()
        assertThat(workInfos.filter { it.state == WorkInfo.State.ENQUEUED }).isEmpty()

        coroutineScope.cancel()
    }
}

private class FakeAppUpdateChecker(
    override val isEnabled: Boolean,
    override val currentReleaseTag: String = "mein-thunderbird-2026-10-25",
) : AppUpdateChecker {
    override suspend fun check(): UpdateCheckResult = UpdateCheckResult.UpToDate
}

private class FakeAppUpdateSettings(
    autoCheckEnabled: Boolean,
) : AppUpdateSettings {
    private val autoCheckEnabledFlow = MutableStateFlow(autoCheckEnabled)

    override val autoCheckEnabled: StateFlow<Boolean> = autoCheckEnabledFlow.asStateFlow()

    override fun setAutoCheckEnabled(enabled: Boolean) {
        autoCheckEnabledFlow.value = enabled
    }
}
