package com.fsck.k9.job

import android.content.Context
import android.os.StatFs
import java.util.concurrent.atomic.AtomicBoolean
import androidx.work.Worker
import androidx.work.WorkerParameters
import app.k9mail.legacy.mailstore.MessageStoreManager
import com.fsck.k9.controller.ContinueCondition
import com.fsck.k9.controller.MessagingController
import com.fsck.k9.mail.FolderType
import net.thunderbird.core.android.account.LegacyAccountManager
import net.thunderbird.core.preference.GeneralSettingsManager
import net.thunderbird.core.preference.network.NetworkSettings
import net.thunderbird.feature.account.AccountId
import net.thunderbird.legacy.logging.Log

private const val TAG = "OfflineDownloadWorker"
private const val BYTES_PER_MEGABYTE = 1024L * 1024L
private const val SIZE_CHECK_INTERVAL = 50
private const val ERROR_DOWNLOAD_FAILED = "Download failed"

/**
 * Downloads all messages of all folders of all accounts, including attachments.
 *
 * The download is interrupted when the device is running out of storage or when the messages would use more storage
 * than allowed. In that case the job is continued the next time it is run.
 */
class OfflineDownloadWorker(
    private val messagingController: MessagingController,
    private val accountManager: LegacyAccountManager,
    private val messageStoreManager: MessageStoreManager,
    private val generalSettingsManager: GeneralSettingsManager,
    private val statusStore: OfflineDownloadStatusStore,
    context: Context,
    parameters: WorkerParameters,
) : Worker(context, parameters) {

    @Suppress("TooGenericExceptionCaught")
    override fun doWork(): Result {
        val settings = generalSettingsManager.getConfig().network
        if (!settings.offlineDownload) {
            statusStore.update { it.copy(state = OfflineDownloadState.OFF) }
        }

        // The periodic and the one-time job must not download the same folders at the same time
        return if (!settings.offlineDownload || !isRunning.compareAndSet(false, true)) {
            Result.success()
        } else {
            downloadFoldersOnce(settings)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun downloadFoldersOnce(settings: NetworkSettings): Result {
        return try {
            downloadFolders(settings)
        } catch (e: Exception) {
            val rootCause = generateSequence<Throwable>(e) { it.cause }.last()
            val message = rootCause.message ?: rootCause.javaClass.simpleName
            Log.e(TAG, "Offline download failed")
            statusStore.update {
                it.copy(state = OfflineDownloadState.FAILED, lastError = message)
            }
            Result.retry()
        } finally {
            isRunning.set(false)
        }
    }

    private fun downloadFolders(settings: NetworkSettings): Result {
        val folders = collectFolders()
        val startTime = System.currentTimeMillis()
        statusStore.update {
            it.copy(
                state = OfflineDownloadState.RUNNING,
                lastRunStart = startTime,
                folderCount = folders.size,
                completeFolderCount = 0,
                lastError = null,
            )
        }

        val storageMonitor = StorageMonitor(applicationContext, settings, messageStoreManager, accountManager)
        var completeFolderCount = 0
        for (folder in folders) {
            if (isStopped || storageMonitor.failureState != null) break

            val condition = ContinueCondition { !isStopped && storageMonitor.allowsMore() }
            val complete = messagingController.downloadFolderForOfflineUseSynchronous(
                folder.accountId,
                folder.folderId,
                condition,
            )
            if (complete) completeFolderCount++

            statusStore.update { it.copy(completeFolderCount = completeFolderCount) }
        }

        return finishRun(folders.size, completeFolderCount, storageMonitor, startTime)
    }

    private fun collectFolders(): List<FolderToDownload> {
        val folders = mutableListOf<FolderToDownload>()
        for (account in accountManager.findAll()) {
            if (!messagingController.isPushCapable(account.id)) continue

            folders.addAll(getFoldersOfAccount(account.id))
        }
        return folders
    }

    private fun getFoldersOfAccount(accountId: AccountId): List<FolderToDownload> {
        val messageStore = messageStoreManager.getMessageStore(accountId)
        val folders = messageStore.getFolders(excludeLocalOnly = true) { folder ->
            if (folder.serverId != null && folder.type != FolderType.TRASH && folder.type != FolderType.SPAM) {
                FolderToDownload(accountId, folder.id, folder.type == FolderType.INBOX)
            } else {
                null
            }
        }

        val (inboxFolders, otherFolders) = folders.filterNotNull().partition { it.isInbox }
        return inboxFolders + otherFolders
    }

    private fun finishRun(
        folderCount: Int,
        completeFolderCount: Int,
        storageMonitor: StorageMonitor,
        startTime: Long,
    ): Result {
        val now = System.currentTimeMillis()
        val duration = now - startTime
        val failureState = storageMonitor.failureState

        return when {
            completeFolderCount == folderCount -> {
                statusStore.update {
                    it.copy(
                        state = OfflineDownloadState.DONE,
                        lastCompleteTime = now,
                        lastRunDurationMillis = duration,
                    )
                }
                Result.success()
            }
            failureState != null -> {
                statusStore.update {
                    it.copy(state = failureState, lastRunDurationMillis = duration)
                }
                Result.success()
            }
            isStopped -> {
                statusStore.update {
                    it.copy(state = OfflineDownloadState.WAITING, lastRunDurationMillis = duration)
                }
                Result.retry()
            }
            else -> {
                statusStore.update {
                    it.copy(
                        state = OfflineDownloadState.FAILED,
                        lastError = ERROR_DOWNLOAD_FAILED,
                        lastRunDurationMillis = duration,
                    )
                }
                Result.retry()
            }
        }
    }
}

private val isRunning = AtomicBoolean(false)

private data class FolderToDownload(
    val accountId: AccountId,
    val folderId: Long,
    val isInbox: Boolean,
)

/**
 * Checks whether the device has enough storage left to download more messages.
 */
private class StorageMonitor(
    private val context: Context,
    private val settings: NetworkSettings,
    private val messageStoreManager: MessageStoreManager,
    private val accountManager: LegacyAccountManager,
) {
    /** The state describing why no more data can be downloaded. */
    var failureState: OfflineDownloadState? = null
        private set

    private var callsSinceSizeCheck = SIZE_CHECK_INTERVAL

    fun allowsMore(): Boolean {
        failureState = when {
            !hasEnoughFreeSpace() -> OfflineDownloadState.PAUSED_LOW_STORAGE
            isSizeCheckDue() && !isBelowSizeLimit() -> OfflineDownloadState.PAUSED_STORAGE_LIMIT
            else -> null
        }

        return failureState == null
    }

    private fun hasEnoughFreeSpace(): Boolean {
        val freeBytes = StatFs(context.filesDir.path).availableBytes
        return freeBytes >= settings.offlineKeepFreeMegabytes * BYTES_PER_MEGABYTE
    }

    private fun isSizeCheckDue(): Boolean {
        callsSinceSizeCheck++
        if (callsSinceSizeCheck < SIZE_CHECK_INTERVAL) return false

        callsSinceSizeCheck = 0
        return true
    }

    private fun isBelowSizeLimit(): Boolean {
        if (settings.offlineStorageLimitMegabytes <= 0) return true

        val limitBytes = settings.offlineStorageLimitMegabytes * BYTES_PER_MEGABYTE
        return getTotalSize() < limitBytes
    }

    private fun getTotalSize(): Long {
        return accountManager.findAll().sumOf { account ->
            messageStoreManager.getMessageStore(account.id).getSize()
        }
    }
}
