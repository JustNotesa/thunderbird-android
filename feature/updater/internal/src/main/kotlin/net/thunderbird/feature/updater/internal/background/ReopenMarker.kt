package net.thunderbird.feature.updater.internal.background

import android.content.Context
import androidx.core.content.edit

/**
 * Remembers that the user started the installation of an update, so that the app is opened again afterwards.
 *
 * The marker is stored on disk, because Android closes the app while it installs an update of itself.
 */
internal interface ReopenMarker {
    /** Stores that the user started an installation now. */
    fun mark()

    /** The time the marker was stored at, `null` when there is no marker. */
    fun markedAtMillis(): Long?

    /** Removes the marker. */
    fun clear()
}

internal class SharedPreferencesReopenMarker(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) : ReopenMarker {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun mark() {
        preferences.edit { putLong(KEY_MARKED_AT, clock()) }
    }

    override fun markedAtMillis(): Long? {
        return if (preferences.contains(KEY_MARKED_AT)) preferences.getLong(KEY_MARKED_AT, 0L) else null
    }

    override fun clear() {
        preferences.edit { remove(KEY_MARKED_AT) }
    }

    private companion object {
        const val PREFERENCES_NAME = "updater_reopen"
        const val KEY_MARKED_AT = "marked_at"
    }
}
