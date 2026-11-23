package com.fsck.k9.ui.messagelist

import androidx.lifecycle.LiveData
import app.k9mail.legacy.mailstore.MessageListChangedListener
import app.k9mail.legacy.mailstore.MessageListRepository
import com.fsck.k9.search.getLegacyAccountUuids
import com.fsck.k9.search.observeSearchedFolders
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.thunderbird.core.android.account.LegacyAccountManager
import net.thunderbird.feature.account.AccountIdFactory

class MessageListLiveData(
    private val messageListLoader: MessageListLoader,
    private val accountManager: LegacyAccountManager,
    private val messageListRepository: MessageListRepository,
    private val coroutineScope: CoroutineScope,
    val config: MessageListConfig,
) : LiveData<MessageListInfo>() {

    private val messageListChangedListener = MessageListChangedListener {
        loadMessageListAsync()
    }

    private var searchedFoldersJob: Job? = null

    private fun loadMessageListAsync() {
        coroutineScope.launch(Dispatchers.Main) {
            val messageList = withContext(Dispatchers.IO) {
                messageListLoader.getMessageList(config)
            }
            value = messageList
        }
    }

    override fun onActive() {
        super.onActive()

        registerMessageListChangedListenerAsync()
        loadMessageListAsync()
        reloadWhenSearchedFoldersChange()
    }

    override fun onInactive() {
        super.onInactive()
        searchedFoldersJob?.cancel()
        searchedFoldersJob = null
        messageListRepository.removeListener(messageListChangedListener)
    }

    private fun registerMessageListChangedListenerAsync() {
        coroutineScope.launch(Dispatchers.IO) {
            registerMessageListChangedListener()
        }
    }

    private fun registerMessageListChangedListener() {
        val accountUuids = config.search.getLegacyAccountUuids(accountManager)

        for (accountUuid in accountUuids) {
            messageListRepository.addListener(AccountIdFactory.of(accountUuid), messageListChangedListener)
        }
    }

    /**
     * A unified special folder search resolves the folder of each account while it is loaded. Load it again when an
     * account's special folder changes (import, folder list refresh, settings) or an account is added or removed,
     * because none of these changes a message and so no change listener fires.
     */
    private fun reloadWhenSearchedFoldersChange() {
        searchedFoldersJob?.cancel()
        searchedFoldersJob = coroutineScope.launch(Dispatchers.IO) {
            // The first value describes the state the load above already used
            config.search.observeSearchedFolders(accountManager).drop(1).collect {
                registerMessageListChangedListener()
                loadMessageListAsync()
            }
        }
    }
}
