package com.fsck.k9.notification.sound

import android.app.NotificationManager
import android.media.AudioManager
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import net.thunderbird.core.android.account.AccountDefaultsProvider
import org.junit.Test

class NovaSoundChoiceTest {
    @Test
    fun `off serializes to off`() {
        assertThat(NovaSoundChoice.Off.serialize()).isEqualTo("off")
    }

    @Test
    fun `nova sound serializes to nova key`() {
        assertThat(NovaSoundChoice.Nova(NovaSound.NEW_MAIL).serialize()).isEqualTo("nova:newmail")
        assertThat(NovaSoundChoice.Nova(NovaSound.NOTICE).serialize()).isEqualTo("nova:notice")
        assertThat(NovaSoundChoice.Nova(NovaSound.SENT).serialize()).isEqualTo("nova:sent")
    }

    @Test
    fun `system sound serializes to system uri`() {
        assertThat(NovaSoundChoice.System(SYSTEM_URI).serialize()).isEqualTo("system:$SYSTEM_URI")
    }

    @Test
    fun `file sound serializes to file uri`() {
        assertThat(NovaSoundChoice.File(FILE_URI).serialize()).isEqualTo("file:$FILE_URI")
    }

    @Test
    fun `parse off`() {
        assertThat(NovaSoundChoice.parse("off")).isEqualTo(NovaSoundChoice.Off)
    }

    @Test
    fun `parse nova sound`() {
        assertThat(NovaSoundChoice.parse("nova:newmail")).isEqualTo(NovaSoundChoice.Nova(NovaSound.NEW_MAIL))
        assertThat(NovaSoundChoice.parse("nova:notice")).isEqualTo(NovaSoundChoice.Nova(NovaSound.NOTICE))
        assertThat(NovaSoundChoice.parse("nova:sent")).isEqualTo(NovaSoundChoice.Nova(NovaSound.SENT))
    }

    @Test
    fun `parse system sound`() {
        assertThat(NovaSoundChoice.parse("system:$SYSTEM_URI")).isEqualTo(NovaSoundChoice.System(SYSTEM_URI))
    }

    @Test
    fun `parse file sound`() {
        assertThat(NovaSoundChoice.parse("file:$FILE_URI")).isEqualTo(NovaSoundChoice.File(FILE_URI))
    }

    @Test
    fun `parse null returns off`() {
        assertThat(NovaSoundChoice.parse(null)).isEqualTo(NovaSoundChoice.Off)
    }

    @Test
    fun `parse empty string returns off`() {
        assertThat(NovaSoundChoice.parse("")).isEqualTo(NovaSoundChoice.Off)
    }

    @Test
    fun `parse unknown prefix returns off`() {
        assertThat(NovaSoundChoice.parse("bla")).isEqualTo(NovaSoundChoice.Off)
    }

    @Test
    fun `parse nova sound without key returns off`() {
        assertThat(NovaSoundChoice.parse("nova:")).isEqualTo(NovaSoundChoice.Off)
    }

    @Test
    fun `parse unknown nova sound returns off`() {
        assertThat(NovaSoundChoice.parse("nova:xyz")).isEqualTo(NovaSoundChoice.Off)
    }

    @Test
    fun `parse system sound without uri returns off`() {
        assertThat(NovaSoundChoice.parse("system:")).isEqualTo(NovaSoundChoice.Off)
    }

    @Test
    fun `parse file sound without uri returns off`() {
        assertThat(NovaSoundChoice.parse("file:")).isEqualTo(NovaSoundChoice.Off)
    }

    @Test
    fun `parse and serialize round trip`() {
        val choices = listOf(
            NovaSoundChoice.Off,
            NovaSoundChoice.Nova(NovaSound.NEW_MAIL),
            NovaSoundChoice.Nova(NovaSound.NOTICE),
            NovaSoundChoice.Nova(NovaSound.SENT),
            NovaSoundChoice.System(SYSTEM_URI),
            NovaSoundChoice.File(FILE_URI),
        )

        choices.forEach { choice ->
            assertThat(NovaSoundChoice.parse(choice.serialize())).isEqualTo(choice)
        }
    }

    @Test
    fun `default new mail is the nova new mail sound`() {
        assertThat(NovaSoundChoice.DEFAULT_NEW_MAIL).isEqualTo(NovaSoundChoice.Nova(NovaSound.NEW_MAIL))
    }

    @Test
    fun `default sent is the nova sent sound`() {
        assertThat(NovaSoundChoice.DEFAULT_SENT).isEqualTo(NovaSoundChoice.Nova(NovaSound.SENT))
    }

    @Test
    fun `to sound uri of off is empty`() {
        assertThat(NovaSoundChoice.Off.toSoundUri(PACKAGE_NAME)).isEqualTo("")
    }

    @Test
    fun `to sound uri of nova sound is a resource uri`() {
        val uri = NovaSoundChoice.Nova(NovaSound.NEW_MAIL).toSoundUri(PACKAGE_NAME)

        assertThat(uri).isEqualTo("android.resource://$PACKAGE_NAME/raw/nova_newmail")
    }

    @Test
    fun `to sound uri of system sound is the system uri`() {
        assertThat(NovaSoundChoice.System(SYSTEM_URI).toSoundUri(PACKAGE_NAME)).isEqualTo(SYSTEM_URI)
    }

    @Test
    fun `to sound uri of file sound is the file uri`() {
        assertThat(NovaSoundChoice.File(FILE_URI).toSoundUri(PACKAGE_NAME)).isEqualTo(FILE_URI)
    }

    @Test
    fun `account ringtone is default when ringing is enabled and no ringtone is set`() {
        assertThat(isDefaultAccountRingtone(isRingEnabled = true, ringtone = null)).isTrue()
    }

    @Test
    fun `account ringtone is default when ringing is enabled and the default ringtone is set`() {
        val ringtone = AccountDefaultsProvider.DEFAULT_RINGTONE_URI

        assertThat(isDefaultAccountRingtone(isRingEnabled = true, ringtone = ringtone)).isTrue()
    }

    @Test
    fun `account ringtone is not default when ringing is disabled`() {
        assertThat(isDefaultAccountRingtone(isRingEnabled = false, ringtone = null)).isFalse()
    }

    @Test
    fun `account ringtone is not default when a custom ringtone is set`() {
        assertThat(isDefaultAccountRingtone(isRingEnabled = true, ringtone = CUSTOM_RINGTONE_URI)).isFalse()
    }

    @Test
    fun `sent sound is played when do not disturb is off and ringer mode is normal`() {
        assertThat(shouldPlaySentSound(NotificationManager.INTERRUPTION_FILTER_ALL, AudioManager.RINGER_MODE_NORMAL))
            .isTrue()
    }

    @Test
    fun `sent sound is played when interruption filter is unknown and ringer mode is normal`() {
        assertThat(
            shouldPlaySentSound(NotificationManager.INTERRUPTION_FILTER_UNKNOWN, AudioManager.RINGER_MODE_NORMAL),
        ).isTrue()
    }

    @Test
    fun `sent sound is not played when do not disturb is enabled`() {
        assertThat(
            shouldPlaySentSound(NotificationManager.INTERRUPTION_FILTER_PRIORITY, AudioManager.RINGER_MODE_NORMAL),
        ).isFalse()
        assertThat(
            shouldPlaySentSound(NotificationManager.INTERRUPTION_FILTER_NONE, AudioManager.RINGER_MODE_NORMAL),
        ).isFalse()
    }

    @Test
    fun `sent sound is not played when the device is silent or on vibrate`() {
        assertThat(shouldPlaySentSound(NotificationManager.INTERRUPTION_FILTER_ALL, AudioManager.RINGER_MODE_SILENT))
            .isFalse()
        assertThat(shouldPlaySentSound(NotificationManager.INTERRUPTION_FILTER_ALL, AudioManager.RINGER_MODE_VIBRATE))
            .isFalse()
    }

    companion object {
        private const val PACKAGE_NAME = "com.example.app"
        private const val SYSTEM_URI = "content://media/internal/audio/media/42"
        private const val FILE_URI = "content://media/external_primary/audio/media/7"
        private const val CUSTOM_RINGTONE_URI = "android.resource://com.example.app/raw/nova_sent"
    }
}
