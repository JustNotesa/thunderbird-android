package net.thunderbird.feature.updater.internal.install

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
class PendingDownloadStoreTest {
    private val context = RuntimeEnvironment.getApplication()

    private lateinit var testSubject: PendingDownloadStore

    @Before
    fun setUp() {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        testSubject = PendingDownloadStore(context)
    }

    @Test
    fun `there is no pending download at the beginning`() {
        assertThat(testSubject.load()).isNull()
    }

    @Test
    fun `stored download is read back`() {
        // Arrange
        val download = createDownload()

        // Act
        testSubject.save(download)

        // Assert
        assertThat(testSubject.load()).isEqualTo(download)
    }

    @Test
    fun `stored download survives a restart of the app`() {
        // Arrange
        testSubject.save(createDownload())

        // Act
        val storeAfterRestart = PendingDownloadStore(context)

        // Assert
        assertThat(storeAfterRestart.load()).isEqualTo(createDownload())
    }

    @Test
    fun `incomplete download is not returned`() {
        // Arrange
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_DOWNLOAD_ID, DOWNLOAD_ID)
            .putString(KEY_TAG, TAG)
            .commit()

        // Act
        val download = testSubject.load()

        // Assert
        assertThat(download).isNull()
    }

    @Test
    fun `clearing removes the pending download`() {
        // Arrange
        testSubject.save(createDownload())

        // Act
        testSubject.clear()

        // Assert
        assertThat(testSubject.load()).isNull()
    }

    private fun createDownload(): PendingDownload {
        return PendingDownload(
            downloadId = DOWNLOAD_ID,
            tag = TAG,
            sha256 = SHA_256,
            sizeBytes = SIZE_BYTES,
            title = TITLE,
        )
    }

    private companion object {
        private const val PREFERENCES_NAME = "updater_download"
        private const val KEY_DOWNLOAD_ID = "download_id"
        private const val KEY_TAG = "tag"
        private const val DOWNLOAD_ID = 42L
        private const val TAG = "mein-thunderbird-2026-10-25"
        private const val SHA_256 = "398d0034e0e8ede314aa130243fd47c87bd94bf019515e3d683dd50c3fb8d942"
        private const val SIZE_BYTES = 36_349_629L
        private const val TITLE = "Mein Thunderbird 2026-10-25"
    }
}
