package app.k9mail.legacy.ui.folder

import android.database.sqlite.SQLiteException
import app.k9mail.legacy.mailstore.FolderSettingsChangedListener
import app.k9mail.legacy.mailstore.FolderTypeMapper
import app.k9mail.legacy.mailstore.MessageStoreManager
import app.k9mail.legacy.message.controller.MessagingControllerRegistry
import app.k9mail.legacy.message.controller.SimpleMessagingListener
import java.text.Collator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import net.thunderbird.core.android.account.LegacyAccountDto
import net.thunderbird.core.android.account.LegacyAccountDtoManager
import net.thunderbird.core.logging.Logger
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.AccountIdFactory
import net.thunderbird.feature.mail.folder.api.Folder
import net.thunderbird.feature.mail.folder.FolderType
import net.thunderbird.feature.mail.folder.api.OutboxFolderManager
import com.fsck.k9.mail.FolderType as LegacyFolderType

private const val LOG_ID = "[repository][display-folder]"

class DefaultDisplayFolderRepository(
    private val logger: Logger,
    private val accountManager: LegacyAccountDtoManager,
    private val messagingController: MessagingControllerRegistry,
    private val messageStoreManager: MessageStoreManager,
    private val outboxFolderManager: OutboxFolderManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DisplayFolderRepository {
    private val sortForDisplay =
        compareByDescending<DisplayFolder> { it.folder.type == FolderType.INBOX }
            .thenByDescending { it.folder.type == FolderType.OUTBOX }
            .thenByDescending { it.folder.type != FolderType.REGULAR }
            .thenByDescending { it.isInTopGroup }
            .thenBy(
                // #10718 use locale-sensitive ordering for folders
                Collator.getInstance().apply {
                    decomposition = Collator.CANONICAL_DECOMPOSITION
                },
            ) { it.folder.name }

    private fun getDisplayFolders(
        account: LegacyAccountDto,
        outboxFolderId: Long?,
        includeHiddenFolders: Boolean,
    ): List<DisplayFolder> {
        val messageStore = messageStoreManager.getMessageStore(account.id)
        return messageStore.getDisplayFolders(
            includeHiddenFolders = includeHiddenFolders,
            outboxFolderId = outboxFolderId,
        ) { folder ->
            DisplayFolder(
                folder = Folder(
                    id = folder.id,
                    name = folder.name,
                    type = folder.takeIf { it.id == outboxFolderId }?.type?.toFolderType()
                        ?: FolderTypeMapper.folderTypeOf(account, folder.id),
                    isLocalOnly = folder.isLocalOnly,
                ),
                isInTopGroup = folder.isInTopGroup,
                unreadMessageCount = folder.unreadMessageCount,
                starredMessageCount = folder.starredMessageCount,
                pathDelimiter = account.folderPathDelimiter,
            )
        }.sortedWith(sortForDisplay)
    }

    /**
     * Reads the display folders of an account. When the account is removed, its message store isn't available anymore.
     * In that case an empty list is returned instead of letting the exception escape.
     */
    private fun getDisplayFoldersOrEmpty(
        account: LegacyAccountDto,
        outboxFolderId: Long?,
        includeHiddenFolders: Boolean,
    ): List<DisplayFolder> {
        return try {
            getDisplayFolders(account, outboxFolderId, includeHiddenFolders)
        } catch (_: IllegalStateException) {
            logger.error { "$LOG_ID Failed to read the display folders of an account" }
            emptyList()
        } catch (_: SQLiteException) {
            logger.error { "$LOG_ID Failed to read the display folders of an account" }
            emptyList()
        }
    }

    private suspend fun getOutboxFolderId(accountId: AccountId): Long? {
        return try {
            outboxFolderManager.getOutboxFolderId(accountId)
        } catch (_: IllegalStateException) {
            logger.error { "$LOG_ID Failed to read the outbox folder of an account" }
            null
        }
    }

    override fun getDisplayFoldersFlow(
        account: LegacyAccountDto,
        includeHiddenFolders: Boolean,
    ): Flow<List<DisplayFolder>> {
        val messageStore = try {
            messageStoreManager.getMessageStore(account.id)
        } catch (_: IllegalStateException) {
            logger.error { "$LOG_ID Failed to read the message store of an account" }
            return flowOf(emptyList())
        }

        return callbackFlow {
            val outboxFolderId = getOutboxFolderId(account.id)
            send(getDisplayFoldersOrEmpty(account, outboxFolderId, includeHiddenFolders))

            val folderStatusChangedListener = object : SimpleMessagingListener() {
                override fun folderStatusChanged(statusChangedAccount: LegacyAccountDto, folderId: Long) {
                    if (statusChangedAccount.id == account.id) {
                        trySendBlocking(getDisplayFoldersOrEmpty(account, outboxFolderId, includeHiddenFolders))
                    }
                }
            }
            messagingController.addListener(folderStatusChangedListener)

            val folderSettingsChangedListener = FolderSettingsChangedListener {
                withContext(ioDispatcher) {
                    trySendBlocking(getDisplayFoldersOrEmpty(account, outboxFolderId, includeHiddenFolders))
                }
            }
            messageStore.addFolderSettingsChangedListener(folderSettingsChangedListener)

            awaitClose {
                messagingController.removeListener(folderStatusChangedListener)
                messageStore.removeFolderSettingsChangedListener(folderSettingsChangedListener)
            }
        }.buffer(capacity = Channel.CONFLATED)
            .distinctUntilChanged()
            .flowOn(ioDispatcher)
    }

    override fun getDisplayFoldersFlow(accountId: String): Flow<List<DisplayFolder>> {
        val account = accountManager.getById(AccountIdFactory.of(accountId))
        return if (account == null) {
            logger.debug { "$LOG_ID Account not found" }
            flowOf(emptyList())
        } else {
            getDisplayFoldersFlow(account, includeHiddenFolders = false)
        }
    }

    private fun LegacyFolderType.toFolderType(): FolderType =
        when (this) {
            LegacyFolderType.REGULAR -> FolderType.REGULAR
            LegacyFolderType.INBOX -> FolderType.INBOX
            LegacyFolderType.OUTBOX -> FolderType.OUTBOX
            LegacyFolderType.DRAFTS -> FolderType.DRAFTS
            LegacyFolderType.SENT -> FolderType.SENT
            LegacyFolderType.TRASH -> FolderType.TRASH
            LegacyFolderType.SPAM -> FolderType.SPAM
            LegacyFolderType.ARCHIVE -> FolderType.ARCHIVE
        }
}
