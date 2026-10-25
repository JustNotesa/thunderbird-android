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
    }
}
