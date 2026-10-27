package com.fsck.k9.job

import android.content.Context
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class OfflineDownloadStatusStoreTest {
    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `status is off when nothing was stored yet`() {
        val store = OfflineDownloadStatusStore(context)

        val status = store.status.value
        assertThat(status.state).isEqualTo(OfflineDownloadState.OFF)
        assertThat(status.lastRunStart).isNull()
        assertThat(status.lastRunDurationMillis).isNull()
        assertThat(status.folderCount).isEqualTo(0)
        assertThat(status.completeFolderCount).isEqualTo(0)
        assertThat(status.lastCompleteTime).isNull()
        assertThat(status.lastError).isNull()
    }

    @Test
    fun `status is written to disk and read back`() {
        val store = OfflineDownloadStatusStore(context)

        store.update {
            it.copy(
                state = OfflineDownloadState.RUNNING,
                lastRunStart = 1_000L,
                lastRunDurationMillis = 2_000L,
                folderCount = 5,
                completeFolderCount = 3,
                lastCompleteTime = 3_000L,
                lastError = "Some error",
            )
        }

        val newStore = OfflineDownloadStatusStore(context)
        assertThat(newStore.status.value).isEqualTo(
            OfflineDownloadStatus(
                state = OfflineDownloadState.RUNNING,
                lastRunStart = 1_000L,
                lastRunDurationMillis = 2_000L,
                folderCount = 5,
                completeFolderCount = 3,
                lastCompleteTime = 3_000L,
                lastError = "Some error",
            )
        )
    }

    @Test
    fun `status flow is updated when the status is changed`() {
        val store = OfflineDownloadStatusStore(context)

        store.update { it.copy(state = OfflineDownloadState.WAITING) }

        assertThat(store.status.value.state).isEqualTo(OfflineDownloadState.WAITING)
    }

    @Test
    fun `values that are not set are removed`() {
        val store = OfflineDownloadStatusStore(context)
        store.update {
            it.copy(
                state = OfflineDownloadState.FAILED,
                lastRunStart = 1_000L,
                lastError = "Some error",
            )
        }

        store.update {
            it.copy(
                state = OfflineDownloadState.DONE,
                lastRunStart = null,
                lastError = null,
            )
        }

        val newStore = OfflineDownloadStatusStore(context)
        assertThat(newStore.status.value.lastRunStart).isNull()
        assertThat(newStore.status.value.lastError).isNull()
    }

    companion object {
        private const val PREFERENCES_NAME = "offline_download_status"
    }
}
