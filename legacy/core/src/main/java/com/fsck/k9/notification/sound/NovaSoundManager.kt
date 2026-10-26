package com.fsck.k9.notification.sound

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import com.fsck.k9.notification.NotificationChannelManager
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.thunderbird.core.android.account.LegacyAccount
import net.thunderbird.core.android.account.LegacyAccountManager
import net.thunderbird.legacy.logging.Log

private const val TAG = "NovaSoundManager"

/**
 * Manages the global notification sounds.
 *
 * The sound for new messages is applied to all accounts. Accounts with a ringtone of their own keep it, until the user
 * picks a different global sound.
 */
class NovaSoundManager(
    private val context: Context,
    private val settings: NovaSoundSettings,
    private val accountManager: LegacyAccountManager,
    private val notificationChannelManager: NotificationChannelManager,
    private val backgroundExecutor: Executor,
    private val coroutineScope: CoroutineScope,
) : NovaSoundFiles by DefaultNovaSoundFiles(context) {
    private var lastRingtone: Ringtone? = null

    /**
     * Apply the default sounds and watch the accounts for new ones.
     */
    fun start() {
        applyDefaults()

        coroutineScope.launch {
            accountManager.observeAll().collect { accounts ->
                applyToNewAccounts(accounts)
            }
        }
    }

    /**
     * Set the default sounds, unless they were set before.
     */
    internal fun applyDefaults() {
        if (settings.defaultsApplied) return

        if (settings.newMail == null) {
            settings.newMail = NovaSoundChoice.DEFAULT_NEW_MAIL.serialize()
        }
        if (settings.sent == null) {
            settings.sent = NovaSoundChoice.DEFAULT_SENT.serialize()
        }

        settings.defaultsApplied = true
    }

    /**
     * Apply the sound for new messages to the accounts that don't have a ringtone of their own.
     */
    internal fun applyToNewAccounts(accounts: List<LegacyAccount>) {
        backgroundExecutor.execute {
            val handledAccountIds = settings.handledAccountIds
            val newAccounts = accounts.filter { it.id.toString() !in handledAccountIds }
            val choice = currentNewMail()

            for (account in newAccounts) {
                val notificationSettings = account.notificationSettings
                if (isDefaultAccountRingtone(notificationSettings.isRingEnabled, notificationSettings.ringtone)) {
                    applyToAccount(account, choice)
                }
            }

            val newAccountIds = newAccounts.map { it.id.toString() }
            if (newAccountIds.isNotEmpty()) {
                settings.handledAccountIds = handledAccountIds + newAccountIds
            }
        }
    }

    /**
     * The sound that is played for new messages.
     */
    fun currentNewMail(): NovaSoundChoice {
        return NovaSoundChoice.parse(settings.newMail)
    }

    /**
     * The sound that is played after sending a message.
     */
    fun currentSent(): NovaSoundChoice {
        return NovaSoundChoice.parse(settings.sent)
    }

    /**
     * Set the sound for new messages and apply it to all accounts.
     */
    fun setNewMail(choice: NovaSoundChoice) {
        settings.newMail = choice.serialize()

        backgroundExecutor.execute {
            val accounts = accountManager.findAll()

            for (account in accounts) {
                applyToAccount(account, choice)
            }

            settings.handledAccountIds = settings.handledAccountIds + accounts.map { it.id.toString() }
        }
    }

    /**
     * Set the sound that is played after sending a message.
     */
    fun setSent(choice: NovaSoundChoice) {
        settings.sent = choice.serialize()
    }

    /**
     * Play the sound once, so the user can hear what they picked.
     */
    fun preview(choice: NovaSoundChoice) {
        if (choice is NovaSoundChoice.Off) return

        play(choice.toSoundUri(context.packageName))
    }

    /**
     * Play the sound for sent messages.
     */
    fun playSent() {
        val choice = currentSent()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val soundAllowed = shouldPlaySentSound(notificationManager.currentInterruptionFilter, audioManager.ringerMode)

        if (choice !is NovaSoundChoice.Off && soundAllowed) {
            play(choice.toSoundUri(context.packageName))
        }
    }

    private fun applyToAccount(account: LegacyAccount, choice: NovaSoundChoice) {
        val accountId = account.id
        val soundUri = choice.toSoundUri(context.packageName)
        val notificationSettings = account.notificationSettings
        if (notificationSettings.isRingEnabled && notificationSettings.ringtone == soundUri) return

        accountManager.updateSync(accountId) { legacyAccount ->
            legacyAccount.copy(
                notificationSettings = legacyAccount.notificationSettings.copy(
                    isRingEnabled = true,
                    ringtone = soundUri,
                ),
            )
        }

        notificationChannelManager.recreateMessagesNotificationChannel(accountId)
    }

    @Synchronized
    @Suppress("TooGenericExceptionCaught")
    private fun play(uri: String) {
        try {
            val ringtone: Ringtone? = RingtoneManager.getRingtone(context, Uri.parse(uri))
            if (ringtone == null) return

            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            lastRingtone?.stop()
            lastRingtone = ringtone
            ringtone.play()
        } catch (e: Exception) {
            Log.e(TAG, e, "Failed to play the notification sound")
        }
    }
}
