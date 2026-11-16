package net.thunderbird.feature.updater.internal.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.thunderbird.feature.updater.api.AppUpdateSettings

/**
 * Keeps the user settings of the updater in a private [SharedPreferences] file.
 */
internal class PreferencesAppUpdateSettings(context: Context) : AppUpdateSettings {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val autoCheckEnabledFlow = MutableStateFlow(
        preferences.getBoolean(KEY_AUTO_CHECK_ENABLED, DEFAULT_AUTO_CHECK_ENABLED),
    )

    override val autoCheckEnabled: StateFlow<Boolean> = autoCheckEnabledFlow.asStateFlow()

    override fun setAutoCheckEnabled(enabled: Boolean) {
        preferences.edit {
            putBoolean(KEY_AUTO_CHECK_ENABLED, enabled)
        }
        autoCheckEnabledFlow.value = enabled
    }

    private val backgroundDownloadEnabledFlow = MutableStateFlow(
        preferences.getBoolean(KEY_BACKGROUND_DOWNLOAD_ENABLED, DEFAULT_BACKGROUND_DOWNLOAD_ENABLED),
    )

    override val backgroundDownloadEnabled: StateFlow<Boolean> = backgroundDownloadEnabledFlow.asStateFlow()

    override fun setBackgroundDownloadEnabled(enabled: Boolean) {
        preferences.edit {
            putBoolean(KEY_BACKGROUND_DOWNLOAD_ENABLED, enabled)
        }
        backgroundDownloadEnabledFlow.value = enabled
    }

    private val downloadOnlyOnWifiFlow = MutableStateFlow(
        preferences.getBoolean(KEY_DOWNLOAD_ONLY_ON_WIFI, DEFAULT_DOWNLOAD_ONLY_ON_WIFI),
    )

    override val downloadOnlyOnWifi: StateFlow<Boolean> = downloadOnlyOnWifiFlow.asStateFlow()

    override fun setDownloadOnlyOnWifi(enabled: Boolean) {
        preferences.edit {
            putBoolean(KEY_DOWNLOAD_ONLY_ON_WIFI, enabled)
        }
        downloadOnlyOnWifiFlow.value = enabled
    }

    private val autoInstallWhenIdleFlow = MutableStateFlow(
        preferences.getBoolean(KEY_AUTO_INSTALL_WHEN_IDLE, DEFAULT_AUTO_INSTALL_WHEN_IDLE),
    )

    override val autoInstallWhenIdle: StateFlow<Boolean> = autoInstallWhenIdleFlow.asStateFlow()

    override fun setAutoInstallWhenIdle(enabled: Boolean) {
        preferences.edit {
            putBoolean(KEY_AUTO_INSTALL_WHEN_IDLE, enabled)
        }
        autoInstallWhenIdleFlow.value = enabled
    }

    /**
     * Tag of the release the user was told about last. `null` when the user wasn't told about any release yet.
     */
    internal var lastNotifiedTag: String?
        get() = preferences.getString(KEY_LAST_NOTIFIED_TAG, null)
        set(value) {
            preferences.edit {
                if (value == null) {
                    remove(KEY_LAST_NOTIFIED_TAG)
                } else {
                    putString(KEY_LAST_NOTIFIED_TAG, value)
                }
            }
        }

    private companion object {
        private const val PREFERENCES_NAME = "updater"
        private const val KEY_AUTO_CHECK_ENABLED = "auto_check_enabled"
        private const val KEY_BACKGROUND_DOWNLOAD_ENABLED = "background_download_enabled"
        private const val KEY_DOWNLOAD_ONLY_ON_WIFI = "download_only_on_wifi"
        private const val KEY_AUTO_INSTALL_WHEN_IDLE = "auto_install_when_idle"
        private const val KEY_LAST_NOTIFIED_TAG = "last_notified_tag"
        private const val DEFAULT_AUTO_CHECK_ENABLED = true
        private const val DEFAULT_BACKGROUND_DOWNLOAD_ENABLED = false
        private const val DEFAULT_DOWNLOAD_ONLY_ON_WIFI = true
        private const val DEFAULT_AUTO_INSTALL_WHEN_IDLE = false
    }
}
