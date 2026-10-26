package com.fsck.k9.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import com.fsck.k9.FakeLegacyAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import net.thunderbird.components.core.logging.testing.TestLogger
import net.thunderbird.core.android.account.fake.FakeLegacyAccountManager
import net.thunderbird.core.android.testing.RobolectricTest
import net.thunderbird.legacy.logging.Log
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class NotificationChannelManagerMissingChannelTest : RobolectricTest() {
    private val account = FakeLegacyAccount.create()
    private val accountManager = FakeLegacyAccountManager(listOf(account))
    private val notificationManager = mock<NotificationManager>()

    private val notificationChannelManager = NotificationChannelManager(
        accountManager = accountManager,
        backgroundExecutor = { it.run() },
        notificationManager = notificationManager,
        resourceProvider = TestNotificationResourceProvider(),
        notificationLightDecoder = NotificationLightDecoder(),
        // Cancelled, so the account observer of NotificationChannelManager doesn't create any notification channels.
        coroutineScope = CoroutineScope(Job().apply { cancel() }),
    )

    @Before
    fun setUp() {
        Log.logger = TestLogger()
    }

    @Test
    fun `recreating the messages notification channel does nothing when the channel doesn't exist`() {
        whenever(notificationManager.getNotificationChannel(any())).thenReturn(null)

        notificationChannelManager.recreateMessagesNotificationChannel(account.id)

        verify(notificationManager).getNotificationChannel(messagesChannelId)
        verify(notificationManager, never()).createNotificationChannel(any<NotificationChannel>())
    }

    private val messagesChannelId: String
        get() = "messages_channel_${account.id}"
}
