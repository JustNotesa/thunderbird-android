package com.fsck.k9.job

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import net.thunderbird.components.core.logging.testing.TestLogger
import net.thunderbird.core.common.appConfig.PlatformConfigProvider
import net.thunderbird.core.preference.GeneralSettings
import net.thunderbird.core.preference.GeneralSettingsManager
import net.thunderbird.core.preference.network.NetworkSettings
import net.thunderbird.legacy.logging.Log
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class OfflineDownloadWorkerManagerTest {
    private val context = RuntimeEnvironment.getApplication()
    private val workManager = mock<WorkManager>()
    private val generalSettingsManager = mock<GeneralSettingsManager>()
    private val statusStore = OfflineDownloadStatusStore(context)

    @Before
    fun setUp() {
        Log.logger = TestLogger()
        whenever(
            workManager.enqueueUniquePeriodicWork(
                any<String>(),
                any<ExistingPeriodicWorkPolicy>(),
                any<PeriodicWorkRequest>(),
            )
        ).thenReturn(mock<Operation>())
        whenever(
            workManager.enqueueUniqueWork(
                any<String>(),
                any<ExistingWorkPolicy>(),
                any<OneTimeWorkRequest>(),
            )
        ).thenReturn(mock<Operation>())
        whenever(workManager.cancelUniqueWork(any<String>())).thenReturn(mock<Operation>())
    }

    @Test
    fun `no work is scheduled when the offline download is disabled`() {
        givenOfflineDownload(enabled = false)
        val testSubject = createTestSubject()

        testSubject.schedule()

        verify(workManager).cancelUniqueWork(OfflineDownloadWorkerManager.PERIODIC_WORK_NAME)
        verify(workManager).cancelUniqueWork(OfflineDownloadWorkerManager.ONE_TIME_WORK_NAME)
        verify(workManager, never()).enqueueUniquePeriodicWork(
            any<String>(),
            any<ExistingPeriodicWorkPolicy>(),
            any<PeriodicWorkRequest>(),
        )
        verify(workManager, never()).enqueueUniqueWork(
            any<String>(),
            any<ExistingWorkPolicy>(),
            any<OneTimeWorkRequest>(),
        )
        assertThat(statusStore.status.value.state).isEqualTo(OfflineDownloadState.OFF)
    }

    @Test
    fun `periodic and one time work are scheduled when the offline download is enabled`() {
        givenOfflineDownload(enabled = true)
        val testSubject = createTestSubject()

        testSubject.schedule()

        val periodicRequest = argumentCaptor<PeriodicWorkRequest>()
        verify(workManager).enqueueUniquePeriodicWork(
            eq(OfflineDownloadWorkerManager.PERIODIC_WORK_NAME),
            eq(ExistingPeriodicWorkPolicy.UPDATE),
            periodicRequest.capture(),
        )
        assertThat(periodicRequest.firstValue.workSpec.constraints.requiredNetworkType)
            .isEqualTo(NetworkType.UNMETERED)
        assertThat(periodicRequest.firstValue.tags).contains(OfflineDownloadWorkerManager.WORK_TAG)

        val oneTimeRequest = argumentCaptor<OneTimeWorkRequest>()
        verify(workManager).enqueueUniqueWork(
            eq(OfflineDownloadWorkerManager.ONE_TIME_WORK_NAME),
            eq(ExistingWorkPolicy.KEEP),
            oneTimeRequest.capture(),
        )
        assertThat(oneTimeRequest.firstValue.workSpec.constraints.requiredNetworkType)
            .isEqualTo(NetworkType.UNMETERED)
        assertThat(oneTimeRequest.firstValue.tags).contains(OfflineDownloadWorkerManager.WORK_TAG)
    }

    @Test
    fun `status is set to waiting when the offline download is enabled`() {
        givenOfflineDownload(enabled = true)
        val testSubject = createTestSubject()

        testSubject.schedule()

        assertThat(statusStore.status.value.state).isEqualTo(OfflineDownloadState.WAITING)
    }

    private fun createTestSubject(): OfflineDownloadWorkerManager {
        return OfflineDownloadWorkerManager(
            workManager = workManager,
            generalSettingsManager = generalSettingsManager,
            statusStore = statusStore,
        )
    }

    private fun givenOfflineDownload(enabled: Boolean) {
        val settings = GeneralSettings(
            platformConfigProvider = object : PlatformConfigProvider {
                override val isDebug: Boolean = false
            },
            network = NetworkSettings(offlineDownload = enabled),
        )
        whenever(generalSettingsManager.getConfig()).thenReturn(settings)
    }
}
