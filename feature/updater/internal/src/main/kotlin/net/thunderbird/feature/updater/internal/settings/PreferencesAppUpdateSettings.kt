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
        private const val KEY_LAST_NOTIFIED_TAG = "last_notified_tag"
        private const val DEFAULT_AUTO_CHECK_ENABLED = true
    }
}
