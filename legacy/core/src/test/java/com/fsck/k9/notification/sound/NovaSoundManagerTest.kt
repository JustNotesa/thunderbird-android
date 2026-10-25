package com.fsck.k9.notification.sound

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import com.fsck.k9.FakeLegacyAccount
import com.fsck.k9.notification.NotificationChannelManager
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.thunderbird.core.android.account.AccountDefaultsProvider
import net.thunderbird.core.android.account.LegacyAccount
import net.thunderbird.core.android.account.LegacyAccountManager
import net.thunderbird.core.android.testing.RobolectricTest
import net.thunderbird.feature.account.AccountIdFactory
import net.thunderbird.feature.notification.NotificationSettings
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

private const val CUSTOM_RINGTONE_URI = "content://media/internal/audio/media/42"

class NovaSoundManagerTest : RobolectricTest() {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val settings = FakeNovaSoundSettings()
    private val accountManager = mock<LegacyAccountManager>()
    private val notificationChannelManager = mock<NotificationChannelManager>()
    private val backgroundExecutor = Executor { it.run() }
    private val novaSoundManager = NovaSoundManager(
        context = context,
        settings = settings,
        accountManager = accountManager,
        notificationChannelManager = notificationChannelManager,
        backgroundExecutor = backgroundExecutor,
        coroutineScope = CoroutineScope(Dispatchers.Unconfined),
    )

    @Test
    fun `applyDefaults sets the default sounds`() {
        novaSoundManager.applyDefaults()

        assertThat(settings.newMail).isEqualTo("nova:newmail")
        assertThat(settings.sent).isEqualTo("nova:sent")
        assertThat(settings.defaultsApplied).isTrue()
    }

    @Test
    fun `applyDefaults keeps the sounds that were set before`() {
        settings.newMail = "system:$CUSTOM_RINGTONE_URI"
        settings.sent = "off"

        novaSoundManager.applyDefaults()

        assertThat(settings.newMail).isEqualTo("system:$CUSTOM_RINGTONE_URI")
        assertThat(settings.sent).isEqualTo("off")
        assertThat(settings.defaultsApplied).isTrue()
    }

    @Test
    fun `applyDefaults is only applied once`() {
        novaSoundManager.applyDefaults()
        settings.newMail = "off"

        novaSoundManager.applyDefaults()

        assertThat(settings.newMail).isEqualTo("off")
        assertThat(settings.defaultsApplied).isTrue()
    }

    @Test
    fun `applyToNewAccounts applies the new mail sound to an account with the default ringtone`() {
        val account = createAccountWithDefaultRingtone()
        novaSoundManager.applyDefaults()

        novaSoundManager.applyToNewAccounts(listOf(account))

        val updatedAccount = captureUpdatedAccount(account)
        assertThat(updatedAccount.notificationSettings.isRingEnabled).isTrue()
        assertThat(updatedAccount.notificationSettings.ringtone).isEqualTo(expectedNewMailUri())

        verify(notificationChannelManager).recreateMessagesNotificationChannel(account.id)
    }

    @Test
    fun `applyToNewAccounts keeps the ringtone of an account that has one of its own`() {
        val account = createAccountWithCustomRingtone()

        novaSoundManager.applyToNewAccounts(listOf(account))

        verifyNoInteractions(accountManager)
        verifyNoInteractions(notificationChannelManager)
    }

    @Test
    fun `applyToNewAccounts records the ids of the handled accounts`() {
        val account = createAccountWithDefaultRingtone()

        novaSoundManager.applyToNewAccounts(listOf(account))

        assertThat(settings.handledAccountIds).isEqualTo(setOf(account.id.toString()))
    }

    @Test
    fun `applyToNewAccounts doesn't touch an account that was handled before`() {
        val account = createAccountWithDefaultRingtone()
        settings.handledAccountIds = setOf(account.id.toString())

        novaSoundManager.applyToNewAccounts(listOf(account))

        verifyNoInteractions(accountManager)
        verifyNoInteractions(notificationChannelManager)
    }

    @Test
    fun `setNewMail applies the new sound to all accounts`() {
        val defaultAccount = createAccountWithDefaultRingtone()
        val customAccount = createAccountWithCustomRingtone()
        whenever(accountManager.findAll()).thenReturn(listOf(defaultAccount, customAccount))

        novaSoundManager.setNewMail(NovaSoundChoice.Nova(NovaSound.NEW_MAIL))

        assertThat(settings.newMail).isEqualTo("nova:newmail")
        assertThat(captureUpdatedAccount(defaultAccount).notificationSettings.ringtone).isEqualTo(expectedNewMailUri())
        assertThat(captureUpdatedAccount(customAccount).notificationSettings.ringtone).isEqualTo(expectedNewMailUri())

        verify(notificationChannelManager).recreateMessagesNotificationChannel(defaultAccount.id)
        verify(notificationChannelManager).recreateMessagesNotificationChannel(customAccount.id)
    }

    @Test
    fun `setNewMail doesn't touch an account that already uses the new sound`() {
        val account = createAccount(NotificationSettings(isRingEnabled = true, ringtone = expectedNewMailUri()))
        whenever(accountManager.findAll()).thenReturn(listOf(account))

        novaSoundManager.setNewMail(NovaSoundChoice.Nova(NovaSound.NEW_MAIL))

        verify(accountManager).findAll()
        verifyNoMoreInteractions(accountManager)
        verifyNoInteractions(notificationChannelManager)
    }

    private fun captureUpdatedAccount(account: LegacyAccount): LegacyAccount {
        val transformCaptor = argumentCaptor<(LegacyAccount) -> LegacyAccount>()
        verify(accountManager).updateSync(eq(account.id), transformCaptor.capture())

        return transformCaptor.firstValue(account)
    }

    private fun createAccountWithDefaultRingtone(): LegacyAccount {
        return createAccount(
            NotificationSettings(
                isRingEnabled = true,
                ringtone = AccountDefaultsProvider.DEFAULT_RINGTONE_URI,
            ),
        )
    }

    private fun createAccountWithCustomRingtone(): LegacyAccount {
        return createAccount(NotificationSettings(isRingEnabled = true, ringtone = CUSTOM_RINGTONE_URI))
    }

    private fun createAccount(notificationSettings: NotificationSettings): LegacyAccount {
        return FakeLegacyAccount.create(id = AccountIdFactory.create()).copy(
            notificationSettings = notificationSettings,
        )
    }

    private fun expectedNewMailUri(): String {
        return "android.resource://${context.packageName}/raw/nova_newmail"
    }
}

private class FakeNovaSoundSettings : NovaSoundSettings {
    override var newMail: String? = null
    override var sent: String? = null
    override var defaultsApplied: Boolean = false
    override var handledAccountIds: Set<String> = emptySet()
}
