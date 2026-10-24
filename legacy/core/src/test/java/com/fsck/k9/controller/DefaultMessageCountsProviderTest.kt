package com.fsck.k9.controller

import app.cash.turbine.test
import app.k9mail.legacy.mailstore.ListenableMessageStore
import app.k9mail.legacy.mailstore.MessageStoreManager
import app.k9mail.legacy.message.controller.MessageCounts
import app.k9mail.legacy.message.controller.MessagingControllerRegistry
import app.k9mail.legacy.message.controller.MessagingListener
import app.k9mail.legacy.message.controller.SimpleMessagingListener
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.fsck.k9.FakeLegacyAccount
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.thunderbird.account.fake.FakeAccountData.ACCOUNT_ID
import net.thunderbird.components.core.logging.testing.TestLogger
import net.thunderbird.core.android.account.LegacyAccountManager
import net.thunderbird.core.android.account.fake.FakeLegacyAccountManager
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.AccountIdFactory
import net.thunderbird.feature.mail.folder.FolderType
import net.thunderbird.feature.search.legacy.LocalMessageSearch
import net.thunderbird.feature.search.legacy.LocalMessageSearchType
import net.thunderbird.feature.search.legacy.SearchConditionTreeNode
import net.thunderbird.feature.search.legacy.UnifiedFolderSelection
import net.thunderbird.feature.search.legacy.sql.SqlWhereClause
import net.thunderbird.legacy.core.mailstore.folder.FakeOutboxFolderManager
import org.junit.Test
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

private const val UNREAD_COUNT = 2
private const val STARRED_COUNT = 3
private const val FIRST_SENT_FOLDER_ID = 7L
private const val SECOND_SENT_FOLDER_ID = 9L
private val FIRST_ACCOUNT_ID = AccountIdFactory.create()
private val SECOND_ACCOUNT_ID = AccountIdFactory.create()

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultMessageCountsProviderTest {

    private val account = FakeLegacyAccount.create(id = ACCOUNT_ID)
    private val accountManager = mock<LegacyAccountManager> {
        on { findById(ACCOUNT_ID) } doReturn account
        on { findAll() } doReturn listOf(account)
    }
    private val messageStore = mock<ListenableMessageStore> {
        on {
            getUnreadMessageCount(
                anyOrNull<SearchConditionTreeNode>(),
            )
        } doReturn UNREAD_COUNT
        on { getStarredMessageCount(anyOrNull()) } doReturn STARRED_COUNT
    }
    private val messageStoreManager = mock<MessageStoreManager> {
        on { getMessageStore(ACCOUNT_ID) } doReturn messageStore
    }

    private val messagingControllerRegistry = mock<MessagingControllerRegistry> {}

    private val conditionsByAccountId = mutableMapOf<AccountId, SearchConditionTreeNode>()

    private val messageCountsProvider = DefaultMessageCountsProvider(
        accountManager = accountManager,
        messageStoreManager = messageStoreManager,
        messagingControllerRegistry = messagingControllerRegistry,
        outboxFolderManager = FakeOutboxFolderManager(),
        logger = TestLogger(),
    )

    @Test
    fun `getMessageCounts() should return zero counts for a removed account`() {
        // Arrange
        val messageCountsProvider = DefaultMessageCountsProvider(
            accountManager = FakeLegacyAccountManager(),
            messageStoreManager = messageStoreManager,
            messagingControllerRegistry = messagingControllerRegistry,
            outboxFolderManager = FakeOutboxFolderManager(),
            logger = TestLogger(),
        )

        // Act
        val messageCounts = messageCountsProvider.getMessageCounts(ACCOUNT_ID)

        // Assert
        assertThat(messageCounts).isEqualTo(MessageCounts(unread = 0, starred = 0))
    }

    @Test
    fun `getMessageCounts() without any special folders`() {
        val accountWithoutSpecialFolders = account.copy(
            inboxFolderId = null,
            trashFolderId = null,
            draftsFolderId = null,
            spamFolderId = null,
            sentFolderId = null,
        )
        val accountManager = mock<LegacyAccountManager> {
            on { findById(ACCOUNT_ID) } doReturn accountWithoutSpecialFolders
            on { findAll() } doReturn listOf(accountWithoutSpecialFolders)
        }
        val messageCountsProvider = DefaultMessageCountsProvider(
            accountManager = accountManager,
            messageStoreManager = messageStoreManager,
            messagingControllerRegistry = messagingControllerRegistry,
            outboxFolderManager = FakeOutboxFolderManager(),
            logger = TestLogger(),
        )

        val messageCounts = messageCountsProvider.getMessageCounts(ACCOUNT_ID)

        assertThat(messageCounts.unread).isEqualTo(UNREAD_COUNT)
        assertThat(messageCounts.starred).isEqualTo(STARRED_COUNT)
    }

    @Test
    fun `getMessageCounts() with a unified Sent search should use the sent folder of each account`() {
        // Arrange
        val firstAccount = FakeLegacyAccount.create(id = FIRST_ACCOUNT_ID, sentFolderId = FIRST_SENT_FOLDER_ID)
        val secondAccount = FakeLegacyAccount.create(id = SECOND_ACCOUNT_ID, sentFolderId = SECOND_SENT_FOLDER_ID)
        val accountManager = mock<LegacyAccountManager> {
            on { findAll() } doReturn listOf(firstAccount, secondAccount)
        }
        val firstMessageStore = createMessageStore(FIRST_ACCOUNT_ID)
        val secondMessageStore = createMessageStore(SECOND_ACCOUNT_ID)
        val messageStoreManager = mock<MessageStoreManager> {
            on { getMessageStore(FIRST_ACCOUNT_ID) } doReturn firstMessageStore
            on { getMessageStore(SECOND_ACCOUNT_ID) } doReturn secondMessageStore
        }
        val messageCountsProvider = DefaultMessageCountsProvider(
            accountManager = accountManager,
            messageStoreManager = messageStoreManager,
            messagingControllerRegistry = messagingControllerRegistry,
            outboxFolderManager = FakeOutboxFolderManager(),
            logger = TestLogger(),
        )
        val search = LocalMessageSearch().apply {
            type = LocalMessageSearchType.Unified(UnifiedFolderSelection.Special(FolderType.SENT))
        }

        // Act
        val messageCounts = messageCountsProvider.getMessageCounts(search)

        // Assert
        assertThat(messageCounts).isEqualTo(MessageCounts(unread = 2 * UNREAD_COUNT, starred = 2 * STARRED_COUNT))
        assertThat(search.leafSet).isEmpty()
        assertSentFolderConditions(FIRST_ACCOUNT_ID, FIRST_SENT_FOLDER_ID)
        assertSentFolderConditions(SECOND_ACCOUNT_ID, SECOND_SENT_FOLDER_ID)
    }

    private fun assertSentFolderConditions(accountId: AccountId, sentFolderId: Long) {
        val conditions = conditionsByAccountId.getValue(accountId)
        val whereClause = SqlWhereClause.Builder().withConditions(conditions).build()

        assertThat(whereClause.selection).isEqualTo("folder_id = ?")
        assertThat(whereClause.selectionArgs).containsExactly(sentFolderId.toString())
    }

    private fun createMessageStore(accountId: AccountId): ListenableMessageStore = mock {
        on {
            getUnreadMessageCount(
                anyOrNull<SearchConditionTreeNode>(),
            )
        } doAnswer {
            conditionsByAccountId[accountId] = it.getArgument(0)
            UNREAD_COUNT
        }
        on { getStarredMessageCount(anyOrNull()) } doReturn STARRED_COUNT
    }

    @Test
    fun `getMessageCountsFlow should emit for every change`() = runTest {
        var currentListener: SimpleMessagingListener? = null
        val registry = object : MessagingControllerRegistry {
            override fun addListener(listener: MessagingListener) {
                currentListener = listener as SimpleMessagingListener
            }

            override fun removeListener(listener: MessagingListener) {
                currentListener = null
            }
        }
        var currentCount = 0
        val messageStore = mock<ListenableMessageStore> {
            on {
                getUnreadMessageCount(
                    anyOrNull<SearchConditionTreeNode>(),
                )
            } doAnswer { currentCount }
            on { getStarredMessageCount(anyOrNull()) } doAnswer { currentCount }
        }
        val messageStoreManager = mock<MessageStoreManager> {
            on { getMessageStore(ACCOUNT_ID) } doReturn messageStore
        }
        val testSubject = DefaultMessageCountsProvider(
            accountManager = accountManager,
            messageStoreManager = messageStoreManager,
            messagingControllerRegistry = registry,
            outboxFolderManager = FakeOutboxFolderManager(),
            logger = TestLogger(),
            coroutineContext = UnconfinedTestDispatcher(testScheduler),
        )
        val search = LocalMessageSearch().apply {
            addAccountId(ACCOUNT_ID)
        }

        testSubject.getMessageCountsFlow(search).test {
            assertThat(awaitItem()).isEqualTo(MessageCounts(0, 0))
            currentCount = 1
            currentListener?.folderStatusChanged(ACCOUNT_ID, 0)
            testScheduler.advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(MessageCounts(1, 1))
            currentCount = 2
            currentListener?.folderStatusChanged(ACCOUNT_ID, 0)
            testScheduler.advanceUntilIdle()
            assertThat(awaitItem()).isEqualTo(MessageCounts(2, 2))
        }
    }
}
