package com.fsck.k9.notification.sound

import android.content.Context
import android.content.SharedPreferences

/**
 * The global settings for the Nova notification sounds.
 */
interface NovaSoundSettings {
    /** The serialized sound for new messages. `null` if it was never set. */
    var newMail: String?

    /** The serialized sound for sent messages. `null` if it was never set. */
    var sent: String?

    /** Whether the defaults were applied to the accounts. */
    var defaultsApplied: Boolean

    /** The IDs of the accounts the current settings were applied to. */
    var handledAccountIds: Set<String>
}

/**
 * [NovaSoundSettings] backed by [SharedPreferences].
 */
class SharedPreferencesNovaSoundSettings(context: Context) : NovaSoundSettings {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override var newMail: String?
        get() = preferences.getString(KEY_NEW_MAIL, null)
        set(value) {
            preferences.edit().putString(KEY_NEW_MAIL, value).apply()
        }

    override var sent: String?
        get() = preferences.getString(KEY_SENT, null)
        set(value) {
            preferences.edit().putString(KEY_SENT, value).apply()
        }

    override var defaultsApplied: Boolean
        get() = preferences.getBoolean(KEY_DEFAULTS_APPLIED, false)
        set(value) {
            preferences.edit().putBoolean(KEY_DEFAULTS_APPLIED, value).apply()
        }

    override var handledAccountIds: Set<String>
        get() = preferences.getStringSet(KEY_HANDLED_ACCOUNT_IDS, null)?.toSet() ?: emptySet()
        set(value) {
            preferences.edit().putStringSet(KEY_HANDLED_ACCOUNT_IDS, value.toSet()).apply()
        }

    companion object {
        private const val PREFERENCES_NAME = "nova_sounds"
        private const val KEY_NEW_MAIL = "new_mail"
        private const val KEY_SENT = "sent"
        private const val KEY_DEFAULTS_APPLIED = "defaults_applied"
        private const val KEY_HANDLED_ACCOUNT_IDS = "handled_account_ids"
    }
}
