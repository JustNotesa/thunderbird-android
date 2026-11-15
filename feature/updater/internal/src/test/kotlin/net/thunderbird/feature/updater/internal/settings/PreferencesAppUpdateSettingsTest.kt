package net.thunderbird.feature.updater.internal.settings

import android.content.Context
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PreferencesAppUpdateSettingsTest {
    private val context = RuntimeEnvironment.getApplication()

    private val testSubject by lazy { PreferencesAppUpdateSettings(context) }

    @Before
    fun setUp() {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `auto check is enabled by default`() {
        assertThat(testSubject.autoCheckEnabled.value).isTrue()
    }

    @Test
    fun `auto check can be switched off`() {
        testSubject.setAutoCheckEnabled(false)

        assertThat(testSubject.autoCheckEnabled.value).isFalse()
    }

    @Test
    fun `auto check is stored in the preferences`() {
        testSubject.setAutoCheckEnabled(false)

        val storedValue = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_CHECK_ENABLED, true)
        assertThat(storedValue).isFalse()
    }

    @Test
    fun `switching auto check emits the new value`() = runTest {
        testSubject.autoCheckEnabled.test {
            assertThat(awaitItem()).isTrue()

            testSubject.setAutoCheckEnabled(false)

            assertThat(awaitItem()).isFalse()
        }
    }

    @Test
    fun `the background download is switched off by default`() {
        assertThat(testSubject.backgroundDownloadEnabled.value).isFalse()
    }

    @Test
    fun `the background download can be switched on`() {
        testSubject.setBackgroundDownloadEnabled(true)

        assertThat(testSubject.backgroundDownloadEnabled.value).isTrue()
    }

    @Test
    fun `the background download is stored in the preferences`() {
        testSubject.setBackgroundDownloadEnabled(true)

        val storedValue = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_BACKGROUND_DOWNLOAD_ENABLED, false)
        assertThat(storedValue).isTrue()
    }

    @Test
    fun `the background download survives a restart of the app`() {
        testSubject.setBackgroundDownloadEnabled(true)

        val settingsAfterRestart = PreferencesAppUpdateSettings(context)
        assertThat(settingsAfterRestart.backgroundDownloadEnabled.value).isTrue()
    }

    @Test
    fun `switching the background download emits the new value`() = runTest {
        testSubject.backgroundDownloadEnabled.test {
            assertThat(awaitItem()).isFalse()

            testSubject.setBackgroundDownloadEnabled(true)

            assertThat(awaitItem()).isTrue()
        }
    }

    @Test
    fun `the download only runs over wifi by default`() {
        assertThat(testSubject.downloadOnlyOnWifi.value).isTrue()
    }

    @Test
    fun `the download can run over a metered network`() {
        testSubject.setDownloadOnlyOnWifi(false)

        assertThat(testSubject.downloadOnlyOnWifi.value).isFalse()
    }

    @Test
    fun `the download over a metered network is stored in the preferences`() {
        testSubject.setDownloadOnlyOnWifi(false)

        val storedValue = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DOWNLOAD_ONLY_ON_WIFI, true)
        assertThat(storedValue).isFalse()
    }

    @Test
    fun `the automatic installation is switched off by default`() {
        assertThat(testSubject.autoInstallWhenIdle.value).isFalse()
    }

    @Test
    fun `the automatic installation can be switched on`() {
        testSubject.setAutoInstallWhenIdle(true)

        assertThat(testSubject.autoInstallWhenIdle.value).isTrue()
    }

    @Test
    fun `the automatic installation is stored in the preferences`() {
        testSubject.setAutoInstallWhenIdle(true)

        val storedValue = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_INSTALL_WHEN_IDLE, false)
        assertThat(storedValue).isTrue()
    }

    @Test
    fun `switching the automatic installation emits the new value`() = runTest {
        testSubject.autoInstallWhenIdle.test {
            assertThat(awaitItem()).isFalse()

            testSubject.setAutoInstallWhenIdle(true)

            assertThat(awaitItem()).isTrue()
        }
    }

    @Test
    fun `no release was notified about yet`() {
        assertThat(testSubject.lastNotifiedTag).isNull()
    }

    @Test
    fun `notified release tag is stored and read back`() {
        testSubject.lastNotifiedTag = "mein-thunderbird-2026-10-25"

        assertThat(testSubject.lastNotifiedTag).isEqualTo("mein-thunderbird-2026-10-25")
    }

    @Test
    fun `notified release tag survives a restart of the app`() {
        testSubject.lastNotifiedTag = "mein-thunderbird-2026-10-25"

        val settingsAfterRestart = PreferencesAppUpdateSettings(context)
        assertThat(settingsAfterRestart.lastNotifiedTag).isEqualTo("mein-thunderbird-2026-10-25")
    }

    private companion object {
        private const val PREFERENCES_NAME = "updater"
        private const val KEY_AUTO_CHECK_ENABLED = "auto_check_enabled"
        private const val KEY_BACKGROUND_DOWNLOAD_ENABLED = "background_download_enabled"
        private const val KEY_DOWNLOAD_ONLY_ON_WIFI = "download_only_on_wifi"
        private const val KEY_AUTO_INSTALL_WHEN_IDLE = "auto_install_when_idle"
    }
}
