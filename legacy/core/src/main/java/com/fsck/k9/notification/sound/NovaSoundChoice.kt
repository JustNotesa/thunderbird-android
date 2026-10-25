package com.fsck.k9.notification.sound

import android.app.NotificationManager
import android.media.AudioManager
import net.thunderbird.core.android.account.AccountDefaultsProvider

/**
 * The sound that is played for a notification.
 *
 * The choice is stored as a string. Use [serialize] and [parse] to convert it to and from that representation.
 */
sealed interface NovaSoundChoice {
    /** No sound is played. */
    data object Off : NovaSoundChoice {
        override fun serialize(): String = "off"
    }

    /** One of the Nova sounds shipped with the app. */
    data class Nova(val sound: NovaSound) : NovaSoundChoice {
        override fun serialize(): String = "nova:${sound.key}"
    }

    /** A notification sound of the device. */
    data class System(val uri: String) : NovaSoundChoice {
        override fun serialize(): String = "system:$uri"
    }

    /** A sound file the user picked. */
    data class File(val uri: String) : NovaSoundChoice {
        override fun serialize(): String = "file:$uri"
    }

    /** Serialize this choice into the representation used to store it. */
    fun serialize(): String

    companion object {
        /** The sound used for new messages as long as the user didn't pick a different one. */
        val DEFAULT_NEW_MAIL: NovaSoundChoice = Nova(NovaSound.NEW_MAIL)

        /** The sound used for sent messages as long as the user didn't pick a different one. */
        val DEFAULT_SENT: NovaSoundChoice = Nova(NovaSound.SENT)

        /**
         * Parse a stored value.
         *
         * Unknown or malformed values are treated as [Off]. This function never throws.
         */
        fun parse(value: String?): NovaSoundChoice {
            val prefix = value?.substringBefore(SEPARATOR).orEmpty()
            val payload = value?.substringAfter(SEPARATOR, missingDelimiterValue = "").orEmpty()

            return when (prefix) {
                NOVA_PREFIX -> NovaSound.entries.find { it.key == payload }?.let { Nova(it) } ?: Off
                SYSTEM_PREFIX -> if (payload.isEmpty()) Off else System(payload)
                FILE_PREFIX -> if (payload.isEmpty()) Off else File(payload)
                else -> Off
            }
        }

        private const val SEPARATOR = ':'
        private const val NOVA_PREFIX = "nova"
        private const val SYSTEM_PREFIX = "system"
        private const val FILE_PREFIX = "file"
    }
}

/** The Nova sounds shipped with the app. */
enum class NovaSound(val key: String, val resourceName: String) {
    NEW_MAIL("newmail", "nova_newmail"),
    NOTICE("notice", "nova_notice"),
    SENT("sent", "nova_sent"),
}

/**
 * The Uri Android uses as channel sound and for playing the sound.
 *
 * [NovaSoundChoice.Off] results in an empty string, i.e. the notification is silent.
 */
fun NovaSoundChoice.toSoundUri(packageName: String): String {
    return when (this) {
        is NovaSoundChoice.Off -> ""
        is NovaSoundChoice.Nova -> "android.resource://$packageName/raw/${sound.resourceName}"
        is NovaSoundChoice.System -> uri
        is NovaSoundChoice.File -> uri
    }
}

/**
 * Whether the account ringtone was never chosen by the user, i.e. it's still the K-9 default.
 */
fun isDefaultAccountRingtone(isRingEnabled: Boolean, ringtone: String?): Boolean {
    return isRingEnabled && (ringtone == null || ringtone == AccountDefaultsProvider.DEFAULT_RINGTONE_URI)
}

/**
 * Whether the sound for sent messages should be played.
 *
 * The sound is only played when "Do not disturb" is disabled and the device is allowed to ring.
 */
fun shouldPlaySentSound(interruptionFilter: Int, ringerMode: Int): Boolean {
    val interruptionFilterAllowsSound =
        interruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL ||
            interruptionFilter == NotificationManager.INTERRUPTION_FILTER_UNKNOWN

    return interruptionFilterAllowsSound && ringerMode == AudioManager.RINGER_MODE_NORMAL
}
