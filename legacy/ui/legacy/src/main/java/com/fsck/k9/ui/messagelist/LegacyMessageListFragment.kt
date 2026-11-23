package com.fsck.k9.ui.messagelist

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.Discouraged
import androidx.annotation.StringRes
import androidx.appcompat.R as AppCompatR
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SearchView
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.children
import androidx.core.view.isGone
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import app.k9mail.core.android.common.contact.ContactRepository
import app.k9mail.feature.launcher.FeatureLauncherActivity
import app.k9mail.feature.launcher.FeatureLauncherTarget
import app.k9mail.legacy.message.controller.MessageReference
import app.k9mail.legacy.message.controller.MessagingControllerRegistry
import app.k9mail.legacy.message.controller.SimpleMessagingListener
import app.k9mail.legacy.ui.folder.FolderNameFormatter
import app.k9mail.ui.utils.itemtouchhelper.ItemTouchHelper
import app.k9mail.ui.utils.linearlayoutmanager.LinearLayoutManager
import com.fsck.k9.K9
import com.fsck.k9.activity.FolderInfoHolder
import com.fsck.k9.activity.MessageSearchActivity
import com.fsck.k9.activity.misc.ContactPicture
import com.fsck.k9.backend.api.SearchStep
import com.fsck.k9.controller.MessagingController
import com.fsck.k9.controller.MessagingControllerWrapper
import com.fsck.k9.fragment.ConfirmationDialogFragment
import com.fsck.k9.fragment.ConfirmationDialogFragment.ConfirmationDialogFragmentListener
import com.fsck.k9.helper.Utility
import com.fsck.k9.helper.mapToSet
import com.fsck.k9.mail.AuthType
import com.fsck.k9.mailstore.LocalStoreProvider
import com.fsck.k9.search.getLegacyAccounts
import com.fsck.k9.search.isNewMessages
import com.fsck.k9.search.isUnified
import com.fsck.k9.search.unifiedSpecialFolderType
import com.fsck.k9.ui.BuildConfig
import com.fsck.k9.ui.R
import com.fsck.k9.ui.choosefolder.ChooseFolderActivity
import com.fsck.k9.ui.choosefolder.ChooseFolderResultContract
import com.fsck.k9.ui.helper.RelativeDateTimeFormatter
import com.fsck.k9.ui.messagelist.MessageListFragmentBridgeContract.MessageListFragmentListener
import com.fsck.k9.ui.messagelist.MessageListFragmentBridgeContract.MessageListFragmentListener.Companion.MAX_PROGRESS
import com.fsck.k9.ui.messagelist.debug.AuthDebugActions
import com.fsck.k9.ui.messagelist.item.MessageViewHolder
import com.fsck.k9.ui.messagelist.item.SearchStatusPanel
import com.fsck.k9.ui.messagelist.search.SearchFilterBar
import com.fsck.k9.ui.messagelist.search.SearchFilters
import com.fsck.k9.ui.messagelist.search.matches
import com.fsck.k9.ui.messagelist.search.toServerSearchCriteria
import com.fsck.k9.ui.messageview.LastSelectedFolderStore
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.BaseTransientBottomBar.BaseCallback
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textview.MaterialTextView
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.Future
import kotlin.time.ExperimentalTime
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.jcip.annotations.GuardedBy
import net.thunderbird.core.android.account.Expunge
import net.thunderbird.core.android.account.LegacyAccount
import net.thunderbird.core.android.account.LegacyAccountManager
import net.thunderbird.core.android.account.SortType
import net.thunderbird.core.android.network.ConnectivityManager
import net.thunderbird.core.common.action.SwipeAction
import net.thunderbird.core.common.exception.MessagingException
import net.thunderbird.core.common.mail.Flag
import net.thunderbird.core.common.mail.ServerSearchCriteria
import net.thunderbird.core.featureflag.FeatureFlagProvider
import net.thunderbird.core.featureflag.FeatureFlagResult
import net.thunderbird.core.featureflag.keys.GeneratedFeatureFlagKey
import net.thunderbird.components.core.logging.Logger
import net.thunderbird.components.core.outcome.Outcome
import net.thunderbird.core.preference.GeneralSettingsManager
import net.thunderbird.core.preference.display.visualSettings.message.list.DisplayMessageListSettings
import net.thunderbird.core.preference.interaction.InteractionSettings
import net.thunderbird.core.ui.theme.api.FeatureThemeProvider
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.AccountIdFactory
import net.thunderbird.feature.account.avatar.AvatarMonogramCreator
import net.thunderbird.feature.changelog.internal.RecentChangesViewModel
import net.thunderbird.feature.mail.folder.FolderType
import net.thunderbird.feature.mail.folder.api.OutboxFolderManager
import net.thunderbird.feature.mail.message.list.domain.DomainContract
import net.thunderbird.feature.mail.message.list.ui.dialog.SetupArchiveFolderDialogFragmentFactory
import net.thunderbird.feature.navigation.changelog.api.ChangeLogMode
import net.thunderbird.feature.notification.api.content.InAppNotification
import net.thunderbird.feature.notification.api.content.SentFolderNotFoundNotification
import net.thunderbird.feature.notification.api.ui.InAppNotificationHost
import net.thunderbird.feature.notification.api.ui.action.NotificationAction
import net.thunderbird.feature.notification.api.ui.dialog.ErrorNotificationsDialogFragmentActionListener
import net.thunderbird.feature.notification.api.ui.dialog.ErrorNotificationsDialogFragmentFactory
import net.thunderbird.feature.notification.api.ui.host.DisplayInAppNotificationFlag
import net.thunderbird.feature.notification.api.ui.host.visual.SnackbarVisual
import net.thunderbird.feature.notification.api.ui.style.SnackbarDuration
import net.thunderbird.feature.search.legacy.LocalMessageSearch
import net.thunderbird.feature.search.legacy.serialization.LocalMessageSearchSerializer
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import net.thunderbird.feature.mail.message.list.R as MessageListApiR

const val MAXIMUM_MESSAGE_SORT_OVERRIDES = 3
const val MINIMUM_CLICK_INTERVAL = 200L
private const val NO_FOLDER_ID = -1L
private const val SEARCH_STEP_UPDATE_INTERVAL = 1000L
private const val LOAD_MORE_MESSAGES_DISTANCE = 3
private const val LOADED_MESSAGES_SETTLING_TIME = 1000L
private const val REMOTE_SEARCH_READING_TIME = 8_000L
private const val ADDITIONAL_FIRST_MESSAGES = 2
private const val MILLISECONDS_PER_SECOND = 1000L

/** How long the undo snackbar stays; Material's LENGTH_LONG (about 2.75 s) is too short to react in time. */
private const val UNDO_SNACKBAR_DURATION_MS = 7_000
const val RECENT_CHANGES_SNACKBAR_DURATION = 10 * 1000

private const val TAG = "BaseMessageListFragment"

@Suppress(
    "LargeClass",
    "TooManyFunctions",
    "CyclomaticComplexMethod",
    "TooGenericExceptionCaught",
    "TooGenericExceptionThrown",
    "SwallowedException",
    "ReturnCount",
    "ForbiddenComment",
)
@Discouraged(
    message = "This class is in maintenance mode. DO NOT introduce any new features in this class. " +
        "Only bugfixes are allowed. New features must be introduced in the new MessageListFragment, " +
        "following the MVI principle.",
)
class LegacyMessageListFragment :
    Fragment(),
    MessageListFragmentBridgeContract,
    MessageListHighlighter,
    ConfirmationDialogFragmentListener,
    MessageListItemActionListener,
    ErrorNotificationsDialogFragmentActionListener {
    val logTag: String = TAG
    override val fragmentActivity: FragmentActivity? get() = activity

    override val legacyViewModel: MessageListViewModel by viewModel()
    private val recentChangesViewModel: RecentChangesViewModel by viewModel()

    private val generalSettingsManager: GeneralSettingsManager by inject()
    private val searchHistory: SearchHistory by inject()
    private val sortTypeToastProvider: SortTypeToastProvider by inject()
    private val folderNameFormatter: FolderNameFormatter by inject { parametersOf(requireContext()) }
    private val messagingController: MessagingControllerWrapper by inject()
    private val messagingControllerRegistry: MessagingControllerRegistry by inject()
    private val accountManager: LegacyAccountManager by inject()
    private val lastSelectedFolderStore: LastSelectedFolderStore by inject()
    private val connectivityManager: ConnectivityManager by inject()
    private val localStoreProvider: LocalStoreProvider by inject()

    private val setupArchiveFolderDialogFragmentFactory: SetupArchiveFolderDialogFragmentFactory by inject()
    private val buildSwipeActions: DomainContract.UseCase.BuildSwipeActions by inject()
    private val featureFlagProvider: FeatureFlagProvider by inject()
    private val featureThemeProvider: FeatureThemeProvider by inject()
    private val logger: Logger by inject()
    private val outboxFolderManager: OutboxFolderManager by inject()
    private val authDebugActions: AuthDebugActions by inject()
    private val errorNotificationsDialogFragmentFactory: ErrorNotificationsDialogFragmentFactory by inject()

    private val handler = MessageListHandler(this)
    private val activityListener = MessageListActivityListener()
    private val actionModeCallback = ActionModeCallback()
    private val pendingMessageAction = PendingMessageAction()

    private val contactRepository: ContactRepository by inject()
    private val avatarMonogramCreator: AvatarMonogramCreator by inject()
    private val relativeDateTimeFormatter: RelativeDateTimeFormatter by inject()

    private val chooseFolderForMoveLauncher: ActivityResultLauncher<ChooseFolderResultContract.Input> =
        registerForActivityResult(ChooseFolderResultContract(ChooseFolderActivity.Action.MOVE)) { result ->
            handleChooseFolderResult(result) { folderId, messages ->
                moveWithUndo(messages, folderId)
            }
        }
    private val chooseFolderForCopyLauncher: ActivityResultLauncher<ChooseFolderResultContract.Input> =
        registerForActivityResult(ChooseFolderResultContract(ChooseFolderActivity.Action.COPY)) { result ->
            handleChooseFolderResult(result) { folderId, messages ->
                copy(messages, folderId)
            }
        }

    private lateinit var fragmentListener: MessageListFragmentListener

    private lateinit var recentChangesSnackbar: Snackbar
    private var coordinatorLayout: CoordinatorLayout? = null
    private var recyclerView: RecyclerView? = null
    private var itemTouchHelper: ItemTouchHelper? = null
    private var swipeRefreshLayout: SwipeRefreshLayout? = null
    private var floatingActionButton: FloatingActionButton? = null
    private var bottomBarHeight = 0
    private var areBannersAtBottom = false
    private var bottomBannersHeight = 0
    private var bannerTopPadding = 0
    private var reachAreaHeight = 0
    private var reachAreaExtraBottomPadding = 0
    private var isReachAreaRevealedByUser = false
    private var isReachAreaScrolledAwayByUser = false
    private var isUserScrollGesture = false
    private var isSnappingToTop = false

    // The detent that holds the list back when it is pulled down from the first message's normal place
    private var isDetentArmed = false
    private var detentPull = 0f
    private var detentCarry = 0f
    private var brokeThroughDetent = false

    private lateinit var adapter: MessageListAdapter

    private var searchView: SearchView? = null
    private var initialSearchViewQuery: String? = null
    private var initialSearchViewIconified = true

    private lateinit var accountIds: Array<AccountId>
    private var accounts: List<LegacyAccount> = emptyList()

    private var account: LegacyAccount? = null

    private var currentFolder: FolderInfoHolder? = null
    private var remoteSearchFuture: Future<*>? = null
    private var extraSearchResults: List<String>? = null
    private var hasRemoteSearchFailed = false
    private var isAutomaticRemoteSearchScheduled = false
    private val automaticRemoteSearchRunnable = Runnable {
        if (isResumed && isRemoteSearchAllowed) {
            onRemoteSearchRequested()
        }
    }
    private var remoteSearchProblem: String? = null
    private var remoteSearchProblemReason: String? = null
    private var remoteSearchFoundCount = 0
    private var remoteSearchNotLoadedCount = 0
    private var isSearchingMessageContents = false
    private var hasSearchedMessageContents = false
    private var isAttachmentSearch = false
    private var isRemoteSearchStopped = false
    private var remoteSearchListener: RemoteSearchListener? = null
    private var remoteSearchFolderIndex = 0
    private val remoteSearchSearchedFolders = mutableSetOf<String>()

    /**
     * The accounts the server search is working on at the moment, with the folder each of them is searching.
     * Several accounts are searched at the same time.
     */
    private val runningSearchAccounts = LinkedHashMap<String, String>()
    private var stoppedRemoteSearch: StoppedRemoteSearch? = null
    private var isLoadingMoreMessagesRequested = false
    private var isWaitingForLoadedMessages = false
    private var messageCountBeforeLoading = 0
    private val loadedMessagesDisplayedRunnable = Runnable { onLoadedMessagesDisplayed() }

    @Volatile
    private var hasLoadingMessagesFailed = false

    @Volatile
    private var loadingMessagesFailureReason: String? = null
    private var searchStatus: ServerSearchStatus? = null
    private var searchStatusPanel: SearchStatusPanel? = null
    private var searchScope: SearchScope? = null

    /**
     * The filters the user has set for the search.
     */
    private var searchFilters = SearchFilters()
    private var searchFilterBar: SearchFilterBar? = null

    private var searchStep: SearchStep? = null
    private var searchStepStartTime = 0L
    private val searchStepRunnable = Runnable { showSearchStep() }
    private var threadTitle: String? = null
    private var allAccounts = false
    private var sortType = SortType.SORT_DATE
    private var sortAscending = true
    private var sortDateAscending = false
    private var actionMode: ActionMode? = null
    private var hasConnectivity: Boolean? = null
    private var isShowFloatingActionButton: Boolean = true

    /**
     * Relevant messages for the current context when we have to remember the chosen messages
     * between user interactions (e.g. selecting a folder for move operation).
     */
    private var activeMessages: List<MessageReference>? = null
    private var showingThreadedList = false
    private var isThreadDisplay = false
    private var activeMessage: MessageReference? = null
    private var rememberedSelected: Set<Long>? = null
    private var lastMessageClick = 0L

    /**
     * Messages whose text was already requested, so scrolling back and forth doesn't request them again.
     */
    private val requestedPreviewDownloads = mutableSetOf<MessageReference>()

    final override lateinit var localSearch: LocalMessageSearch
        private set
    var isSingleAccountMode = false
        private set
    private var isSingleFolderMode = false
    private var isRemoteSearch = false
    private var initialMessageListLoad = true

    /**
     * The messages that were loaded last. They are displayed again when the user changes the filters or when messages
     * are removed from the list for an action that can be undone.
     */
    private var lastMessageListInfo: MessageListInfo? = null

    private val isUnifiedFoldersView: Boolean
        get() = localSearch.isUnified

    private val isNewMessagesView: Boolean
        get() = localSearch.isNewMessages

    /**
     * `true` after [.onCreate] was executed. Used in [.updateTitle] to
     * make sure we don't access member variables before initialization is complete.
     */
    private var isInitialized = false

    private var error: Error? = null

    private var messageListSwipeCallback: MessageListSwipeCallback? = null
    private var undoSnackbar: Snackbar? = null

    /**
     * Messages that were removed from the message list, but that the message list loader hasn't reported as gone yet.
     */
    private var hiddenUntilReload: Set<MessageReference> = emptySet()
    private val interactionSettings: InteractionSettings
        get() = generalSettingsManager.getConfig().interaction
    private val messageListSettings: DisplayMessageListSettings
        get() = generalSettingsManager.getConfig().display.visualSettings.messageListSettings

    /**
     * Set this to `true` when the fragment should be considered active. When active, the fragment adds its actions to
     * the toolbar. When inactive, the fragment won't add its actions to the toolbar, even it is still visible, e.g. as
     * part of an animation.
     */
    override var isActive: Boolean = false
        set(value) {
            field = value
            resetActionMode()
            invalidateMenu()
            maybeHideFloatingActionButton()
            onActiveChanged()
        }

    private val stopRemoteSearchRunnable = Runnable { stopRemoteSearch(canContinue = true) }

    override fun isSearchViewCollapsed(): Boolean {
        val searchView = searchView ?: return true

        // The search results always display the search field. It only counts as open while it is being edited.
        return if (isManualSearch) !searchView.hasFocus() else searchView.isIconified
    }

    override fun expandSearchView() {
        val searchView = searchView ?: return

        if (isManualSearch) {
            searchView.requestFocus()
        } else {
            searchView.isIconified = false
        }
    }

    override val isShowAccountIndicator: Boolean
        get() = isUnifiedFoldersView || !isSingleAccountMode

    override fun onAttach(context: Context) {
        super.onAttach(context)

        fragmentListener = try {
            context as MessageListFragmentListener
        } catch (_: ClassCastException) {
            error("${context.javaClass} must implement MessageListFragmentListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        restoreInstanceState(savedInstanceState)
        val error = decodeArguments()
        if (error != null) {
            this.error = error
            return
        }

        legacyViewModel.getMessageListLiveData().observe(this) { messageListInfo: MessageListInfo ->
            setMessageList(messageListInfo)
        }

        adapter = createMessageListAdapter()

        var observedDateTimeFormat = messageListSettings.dateTimeFormat
        generalSettingsManager.getSettingsFlow()
            /**
             * Skips the first emitted item from the settings flow,
             * since initial display settings are taken from fragment state and current preferences.
             */
            .drop(1)
            .onEach { generalSettings ->
                val isThreadedViewEnabled = generalSettings.display.inboxSettings.isThreadedViewEnabled
                if (showingThreadedList != isThreadedViewEnabled) {
                    showingThreadedList = isThreadedViewEnabled
                    loadMessageList(forceUpdate = true)
                }

                val dateTimeFormat = generalSettings.display.visualSettings.messageListSettings.dateTimeFormat
                if (observedDateTimeFormat != dateTimeFormat) {
                    observedDateTimeFormat = dateTimeFormat
                    adapter.refreshFormattedDates()
                }
            }
            .launchIn(lifecycleScope)

        isInitialized = true
    }

    private fun restoreInstanceState(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) return

        activeMessages = savedInstanceState.getStringArray(STATE_ACTIVE_MESSAGES)
            ?.map { MessageReference.parse(it)!! }
        restoreSelectedMessages(savedInstanceState)
        isRemoteSearch = savedInstanceState.getBoolean(STATE_REMOTE_SEARCH_PERFORMED)
        initialSearchViewQuery = savedInstanceState.getString(STATE_SEARCH_VIEW_QUERY)
        initialSearchViewIconified = savedInstanceState.getBoolean(STATE_SEARCH_VIEW_ICONIFIED, true)
        searchFilters = BundleCompat.getSerializable(
            savedInstanceState,
            STATE_SEARCH_FILTERS,
            SearchFilters::class.java,
        ) ?: SearchFilters()
        val messageReferenceString = savedInstanceState.getString(STATE_ACTIVE_MESSAGE)
        activeMessage = MessageReference.parse(messageReferenceString)
    }

    private fun restoreSelectedMessages(savedInstanceState: Bundle) {
        rememberedSelected = savedInstanceState.getLongArray(STATE_SELECTED_MESSAGES)?.toSet()
    }

    private fun decodeArguments(): Error? {
        val arguments = requireArguments()
        showingThreadedList = arguments.getBoolean(ARG_THREADED_LIST, false)
        isThreadDisplay = arguments.getBoolean(ARG_IS_THREAD_DISPLAY, false)

        localSearch = arguments.getByteArray(ARG_SEARCH)?.let {
            LocalMessageSearchSerializer.deserialize(it)
        }!!

        allAccounts = localSearch.searchAllAccounts()
        val searchAccounts = localSearch.getLegacyAccounts(accountManager).also {
            accounts = it
        }
        if (searchAccounts.size == 1) {
            isSingleAccountMode = true
            val singleAccount = searchAccounts[0]
            account = singleAccount
            accountIds = arrayOf(singleAccount.id)
        } else {
            isSingleAccountMode = false
            account = null
            accountIds = searchAccounts.map { it.id }.toTypedArray()
        }

        isSingleFolderMode = false
        currentFolder = null
        if (isSingleAccountMode && localSearch.folderIds.size == 1) {
            try {
                val account = checkNotNull(account)
                val folderId = localSearch.folderIds[0]
                currentFolder = getFolderInfoHolder(account, folderId)
                isSingleFolderMode = true
            } catch (e: MessagingException) {
                return Error.FolderNotFound
            }
        }

        return null
    }

    private fun createMessageListAdapter(): MessageListAdapter {
        @OptIn(ExperimentalTime::class)
        return MessageListAdapter(
            theme = requireActivity().theme,
            res = resources,
            layoutInflater = layoutInflater,
            contactsPictureLoader = ContactPicture.getContactPictureLoader(),
            listItemListener = this,
            appearance = ::messageListAppearance,
            themeProvider = featureThemeProvider,
            featureFlagProvider = featureFlagProvider,
            contactRepository = contactRepository,
            avatarMonogramCreator = avatarMonogramCreator,
            formatDate = { timestamp ->
                relativeDateTimeFormatter.formatDate(timestamp, messageListSettings.dateTimeFormat)
            },
        ).apply {
            activeMessage = this@LegacyMessageListFragment.activeMessage
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return if (error == null) {
            inflater.inflate(R.layout.message_list_fragment, container, false).also { view ->
                setFragmentResultListener(
                    SetupArchiveFolderDialogFragmentFactory.RESULT_CODE_DISMISS_REQUEST_KEY,
                ) { key, bundle ->
                    logger.debug(logTag) {
                        "SetupArchiveFolderDialogFragment fragment listener triggered with " +
                            "key: $key and bundle: $bundle"
                    }
                    loadMessageList(forceUpdate = true)
                }
            }
        } else {
            inflater.inflate(R.layout.message_list_error, container, false)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val menuHost: MenuHost = requireActivity()

        menuHost.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    if (!isActive) return
                    menuInflater.inflate(R.menu.message_list_option_menu, menu)
                }

                override fun onPrepareMenu(menu: Menu) {
                    if (!isActive) return
                    prepareSearchMenu(menu)
                    prepareMenu(menu)
                    prepareDebugMenu(menu)
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    if (!isActive) return false
                    return selectMenuItem(menuItem)
                }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED,
        )

        if (error == null) {
            initializeMessageListLayout(view)
        } else {
            initializeErrorLayout(view)
        }
    }

    private fun initializeErrorLayout(view: View) {
        val errorMessageView = view.findViewById<MaterialTextView>(R.id.message_list_error_message)
        errorMessageView.text = getString(error!!.errorText)
    }

    private fun initializeMessageListLayout(view: View) {
        initializeSwipeRefreshLayout(view)
        initializeBackToSearchResults(view)
        initializeSearchStatus()
        initializeSearchFilterBar(view)
        initializeFloatingActionButton(view)
        initializeRecyclerView(view)
        initializeBottomBanners(view)
        applyBottomBarHeight(view)
        initializeRecentChangesSnackbar()

        // This needs to be done before loading the message list below
        initializeSortSettings()

        loadMessageList()
    }

    /**
     * The filters are only useful when the search can be run on the server.
     */
    private fun initializeSearchFilterBar(view: View) {
        val filterBarView = view.findViewById<View>(R.id.search_filter_bar)
        if (!isRemoteSearchSupported) {
            filterBarView.isGone = true
            return
        }

        filterBarView.isVisible = true
        searchFilterBar = SearchFilterBar(filterBarView) { filters -> onSearchFiltersChanged(filters) }
        searchFilterBar?.bind(searchFilters)
    }

    private fun initializeBackToSearchResults(view: View) {
        val folderOrigin = fragmentListener.folderOrigin.takeUnless { isThreadDisplay }
        view.findViewById<TextView>(R.id.back_to_search_results).apply {
            isVisible = folderOrigin != null
            setText(
                when (folderOrigin) {
                    FolderOrigin.MESSAGE -> R.string.back_to_message
                    else -> R.string.back_to_search_results
                },
            )
            setOnClickListener { fragmentListener.returnToFolderOrigin() }
        }
    }

    private fun initializeSearchStatus() {
        if (!isRemoteSearchSupported) return

        // A search that was started from a folder can be limited to that folder.
        // The scope always comes from the search this list displays, never from an earlier one.
        searchScope = searchScope.takeIf { it != null && isSearchStartedFromSingleFolder() }
            ?: initialSearchScope(isSearchStartedFromSingleFolder(), isUnifiedFoldersView)

        if (isRemoteSearch && remoteSearchFuture == null) {
            // The server search has ended while the message list wasn't displayed, e.g. while reading a message.
            showSearchOutcome()
        } else if (searchStatus == null) {
            showSearchStatus(ServerSearchStatus.Running(getString(R.string.local_search_status)))
        }
    }

    /**
     * Display the state of the server search, either as the first row of the search results or, with one-handed mode,
     * in the bottom area above the search filters.
     *
     * The row is there right from the start and always has the same height. And because it is part of the list, it
     * only moves when the user scrolls the list. So the messages never move on their own.
     */
    private fun showSearchStatus(status: ServerSearchStatus) {
        searchStatus = status
        updateSearchStatusPlacement()
    }

    private fun isSearchStartedFromSingleFolder() = isSingleFolderMode && account != null && currentFolder != null

    private fun searchStatusItem(status: ServerSearchStatus) = MessageListViewItem.SearchStatus(status, searchScope)

    private fun isSearchStatusInBottomPanel(): Boolean {
        return searchStatusPanel != null &&
            SearchStatusPlacement.of(areBannersAtBottom) == SearchStatusPlacement.BOTTOM_PANEL
    }

    /**
     * Put the state of the search where it belongs, and display its current state there.
     */
    private fun updateSearchStatusPlacement() {
        val status = searchStatus
        val isInPanel = isSearchStatusInBottomPanel()

        if (isInPanel && status != null) {
            searchStatusPanel?.bind(status, searchScope)
        } else {
            searchStatusPanel?.hide()
        }

        val viewItems = adapter.viewItems.filterNot { it is MessageListViewItem.SearchStatus }.toMutableList()
        if (status != null && !isInPanel) {
            val index = viewItems.indexOfFirst { it !is MessageListViewItem.InAppNotificationBannerList }
            viewItems.add(if (index == -1) viewItems.size else index, searchStatusItem(status))
        }
        adapter.viewItems = viewItems
    }

    private fun initializeSearchStatusPanel(view: View) {
        val container = view.findViewById<ViewGroup>(R.id.bottom_banners)
        val panel = SearchStatusPanel.create(
            layoutInflater = layoutInflater,
            parent = container,
            onSearchMessageContentsClickListener = { onSearchMessageContentsClicked() },
            onSearchAttachmentsClickListener = { onSearchAttachmentsClicked() },
            onScopeSelected = { scope -> onSearchScopeSelected(scope) },
        )
        container.addView(panel.view, 0)
        searchStatusPanel = panel
    }

    /**
     * Display which folder the remote search is working on and how many folders there are in total.
     */
    private fun showSearchProgress(folderIndex: Int, folderCount: Int, folderLabel: String) {
        val statusText = when {
            isAttachmentSearch -> R.string.remote_search_attachments_status_progress
            isSearchingMessageContents -> R.string.remote_search_contents_status_progress
            else -> R.string.remote_search_status_progress
        }
        endSearchStep()
        remoteSearchFolderIndex = folderIndex
        showSearchStatus(
            ServerSearchStatus.Running(
                title = getString(statusText, folderIndex, folderCount),
                progress = ServerSearchStatus.Progress(folderIndex, folderCount, folderLabel),
                detail = (searchStatus as? ServerSearchStatus.Running)?.detail,
                isMessageContentsSearchOffered = !isSearchingMessageContents && isMessageContentsSearchAllowed,
            ),
        )
    }

    private fun showSearchDetail(text: String) {
        endSearchStep()
        val status = searchStatus as? ServerSearchStatus.Running ?: return
        showSearchStatus(status.copy(detail = text, activity = null))
    }

    /**
     * Remember what the remote search is doing at the moment.
     *
     * It is only displayed when it takes a while, e.g. because the server can't be reached. Otherwise the text would
     * change all the time while one folder after the other is searched.
     */
    private fun startSearchStep(step: SearchStep) {
        handler.removeCallbacks(searchStepRunnable)
        searchStep = step
        searchStepStartTime = SystemClock.elapsedRealtime()
        handler.postDelayed(searchStepRunnable, SEARCH_STEP_UPDATE_INTERVAL)
    }

    private fun endSearchStep() {
        handler.removeCallbacks(searchStepRunnable)
        searchStep = null
    }

    /**
     * Display what the remote search has been doing for how long, so it doesn't look like it is stuck.
     */
    private fun showSearchStep() {
        val step = searchStep ?: return
        val status = searchStatus as? ServerSearchStatus.Running ?: return
        val context = context ?: return

        val text = when (step) {
            SearchStep.CONNECTING -> R.string.remote_search_step_connecting
            SearchStep.WAITING_FOR_RESPONSE -> R.string.remote_search_step_waiting
        }
        val seconds = (SystemClock.elapsedRealtime() - searchStepStartTime) / MILLISECONDS_PER_SECOND
        showSearchStatus(status.copy(activity = context.getString(text, seconds)))

        handler.postDelayed(searchStepRunnable, SEARCH_STEP_UPDATE_INTERVAL)
    }

    /**
     * Display how the remote search has ended. A problem must never go unnoticed, so this stays visible until the
     * next remote search is started.
     */
    private fun showSearchOutcome() {
        endSearchStep()
        val context = context ?: return
        val problem = remoteSearchProblem
        // A search that has failed altogether needs its space to say why. It can be started again by pulling down.
        val isMessageContentsSearchOffered = isRemoteSearchRestartAllowed &&
            !hasSearchedMessageContents &&
            !hasRemoteSearchFailed &&
            isMessageContentsSearchAllowed
        // The attachments of the messages can be searched on the device, after they have been downloaded.
        val isAttachmentSearchOffered = isRemoteSearchSupported && !isAttachmentSearch

        val status = if (problem != null) {
            ServerSearchStatus.Ended(
                title = problem,
                result = ServerSearchStatus.Result.PROBLEM,
                message = remoteSearchProblemReason,
                isMessageContentsSearchOffered = isMessageContentsSearchOffered,
                isAttachmentSearchOffered = isAttachmentSearchOffered,
            )
        } else if (isRemoteSearchStopped) {
            ServerSearchStatus.Ended(
                title = context.getString(R.string.remote_search_stopped),
                result = ServerSearchStatus.Result.STOPPED,
                message = context.getString(R.string.remote_search_stopped_details),
                isMessageContentsSearchOffered = isMessageContentsSearchOffered,
                isAttachmentSearchOffered = isAttachmentSearchOffered,
            )
        } else {
            val title = when {
                isAttachmentSearch -> R.string.remote_search_attachments_finished
                hasSearchedMessageContents -> R.string.remote_search_contents_finished
                else -> R.string.remote_search_finished
            }
            ServerSearchStatus.Ended(
                title = context.getString(title),
                result = ServerSearchStatus.Result.COMPLETE,
                isMessageContentsSearchOffered = isMessageContentsSearchOffered,
                isAttachmentSearchOffered = isAttachmentSearchOffered,
            )
        }
        showSearchStatus(status)
    }

    override fun onSearchScopeSelected(scope: SearchScope) {
        if (scope == searchScope) return

        searchScope = scope
        // Results from other folders are no longer wanted, or the search has to find them first.
        legacyViewModel.remoteSearchResults.clear()
        hasSearchedMessageContents = false
        onRemoteSearchRequested()
    }

    /**
     * The user has changed the filters of the search. The messages that are already loaded are displayed again, now
     * with the filters applied, and the search on the server is started with the new filters.
     */
    private fun onSearchFiltersChanged(filters: SearchFilters) {
        searchFilters = filters
        searchFilterBar?.bind(filters)

        lastMessageListInfo?.let { setMessageList(it, fromLoader = false) }

        // Results from other folders are no longer wanted, or the search has to find them first.
        legacyViewModel.remoteSearchResults.clear()
        hasSearchedMessageContents = false

        onRemoteSearchRequested(searchMessageContents = filters.textScope != SearchFilters.TextScope.HEADERS)
    }

    override fun onSearchMessageContentsClicked() {
        if (connectivityManager.isNetworkAvailable()) {
            onRemoteSearchRequested(searchMessageContents = true)
        } else {
            Toast.makeText(activity, getText(R.string.remote_search_unavailable_no_network), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSearchAttachmentsClicked() {
        if (connectivityManager.isNetworkAvailable()) {
            onAttachmentSearchRequested()
        } else {
            Toast.makeText(activity, getText(R.string.remote_search_unavailable_no_network), Toast.LENGTH_SHORT).show()
        }
    }

    private fun initializeSwipeRefreshLayout(view: View) {
        val swipeRefreshLayout = view.findViewById<SwipeRefreshLayout>(R.id.swiperefresh)

        if (isRemoteSearchSupported) {
            swipeRefreshLayout.setOnRefreshListener { onRemoteSearchRequested() }
        } else if (isCheckMailSupported) {
            swipeRefreshLayout.setOnRefreshListener { checkMail() }
        }

        // Disable pull-to-refresh until the message list has been loaded
        swipeRefreshLayout.isEnabled = false

        this.swipeRefreshLayout = swipeRefreshLayout
    }

    private fun initializeFloatingActionButton(view: View) {
        isShowFloatingActionButton = generalSettingsManager.getConfig()
            .display
            .inboxSettings
            .isShowComposeButtonOnMessageList
        if (isShowFloatingActionButton) {
            enableFloatingActionButton(view)
        } else {
            disableFloatingActionButton(view)
        }

        initializeFloatingActionButtonInsets(view)
    }

    private fun initializeFloatingActionButtonInsets(view: View) {
        val floatingActionButton = view.findViewById<FloatingActionButton>(R.id.floating_action_button)

        ViewCompat.setOnApplyWindowInsetsListener(floatingActionButton) { v, windowInsets ->
            val insets = windowInsets.getInsets(systemBars())

            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                val fabMargin = view.resources.getDimensionPixelSize(R.dimen.floatingActionButtonMargin)

                bottomMargin = fabMargin
                rightMargin = fabMargin + insets.right
                leftMargin = fabMargin + insets.left
            }

            windowInsets
        }
    }

    private fun enableFloatingActionButton(view: View) {
        val floatingActionButton = view.findViewById<FloatingActionButton>(R.id.floating_action_button)

        ViewCompat.setOnApplyWindowInsetsListener(floatingActionButton) { view, windowInsets ->
            val insets = windowInsets.getInsets(systemBars())
            val margin = resources.getDimensionPixelSize(R.dimen.floatingActionButtonMargin)

            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = margin + insets.left
                bottomMargin = margin + insets.bottom
                rightMargin = margin + insets.right
            }

            WindowInsetsCompat.CONSUMED
        }

        floatingActionButton.setOnClickListener {
            onCompose()
        }

        this.floatingActionButton = floatingActionButton
    }

    private fun disableFloatingActionButton(view: View) {
        val floatingActionButton = view.findViewById<FloatingActionButton>(R.id.floating_action_button)
        floatingActionButton.isGone = true
    }

    private fun initializeRecyclerView(view: View) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.message_list)

        if (!isShowFloatingActionButton) {
            recyclerView.setPadding(0)
        }

        recyclerView.layoutManager = ReachAreaLayoutManager().apply { scrollAdjuster = ::adjustScrollForDetent }
        recyclerView.itemAnimator = MessageListItemAnimator()
        recyclerView.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy > 0) {
                        loadMoreMessagesIfEndIsNear()
                    }

                    if (dy != 0) {
                        // The banners above the toolbar stay put, so the toolbar stays with them.
                        if (!isSnappingToTop && !hasBottomBanners()) fragmentListener.onMessageListScrolled(dy)
                        if (recyclerView.scrollState != RecyclerView.SCROLL_STATE_IDLE) {
                            updateReachAreaRevealedByUser()
                        }
                    }
                }

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        isUserScrollGesture = true
                        isSnappingToTop = false
                        armDetentIfAtTop()
                    }
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        if (isUserScrollGesture) {
                            isUserScrollGesture = false
                            snapToTopIfCloseToIt()
                            disarmDetent()
                        } else {
                            isSnappingToTop = false
                        }
                        downloadMissingPreviewsOfVisibleMessages()
                    }
                }
            },
        )
        recyclerView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateReachArea() }

        val itemTouchHelper = ItemTouchHelper(
            MessageListSwipeCallback(
                context = requireContext(),
                scope = lifecycleScope,
                resourceProvider = SwipeResourceProvider(requireContext()),
                swipeActionSupportProvider = swipeActionSupportProvider,
                swipeActions = buildSwipeActions(),
                adapter = adapter,
                listener = swipeListener,
                accounts = accounts,
            ).also { messageListSwipeCallback = it },
        )
        itemTouchHelper.attachToRecyclerView(recyclerView)

        recyclerView.adapter = adapter
        adapter.registerAdapterDataObserver(
            object : RecyclerView.AdapterDataObserver() {
                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                    keepFirstMessageOnTopWhenMessagesAreInserted(positionStart)
                }
            },
        )

        val isDisplayInAppNotificationEnabled = featureFlagProvider
            .provide(GeneratedFeatureFlagKey.DISPLAY_IN_APP_NOTIFICATIONS) == FeatureFlagResult.Enabled
        if (isDisplayInAppNotificationEnabled) {
            view.findViewById<ComposeView>(R.id.banner_global_compose_view).apply {
                setContent {
                    featureThemeProvider.WithTheme {
                        InAppNotificationHost(
                            onActionClick = { },
                            enabled = persistentSetOf(
                                DisplayInAppNotificationFlag.BannerGlobalNotifications,
                                DisplayInAppNotificationFlag.SnackbarNotifications,
                            ),
                            onSnackbarNotificationEvent = ::onSnackbarInAppNotificationEvent,
                            eventFilter = ::filterInAppNotificationEvents,
                            modifier = Modifier
                                .animateContentSize()
                                .onSizeChanged { size ->
                                    bannerTopPadding = size.height
                                    applyListTopPadding()
                                    updateReachArea()
                                },
                        )
                    }
                }
            }
        }

        this.recyclerView = recyclerView
        this.itemTouchHelper = itemTouchHelper
    }

    private fun applyBottomBarHeight(view: View) {
        val coordinator = view.findViewById<CoordinatorLayout?>(R.id.message_list_coordinator) ?: return

        // The FAB and Snackbars are laid out above the bar, the list is allowed to scroll underneath it.
        coordinator.clipToPadding = false
        coordinator.updatePadding(bottom = bottomBarHeight)

        view.findViewById<SwipeRefreshLayout>(R.id.swiperefresh).updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = -bottomBarHeight
        }

        view.findViewById<RecyclerView>(R.id.message_list)
            .updatePadding(bottom = listBottomPadding() + reachAreaExtraBottomPadding)

        floatingActionButton?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = resources.getDimensionPixelSize(R.dimen.floatingActionButtonMargin) + bottomBannersHeight
        }
    }

    private fun initializeBottomBanners(view: View) {
        val container = view.findViewById<ViewGroup>(R.id.bottom_banners)
        container.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top == oldBottom - oldTop) return@addOnLayoutChangeListener

            bottomBannersHeight = bottom - top
            view.findViewById<RecyclerView>(R.id.message_list).let { list ->
                list.updatePadding(bottom = listBottomPadding() + reachAreaExtraBottomPadding)
            }
            floatingActionButton?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = resources.getDimensionPixelSize(R.dimen.floatingActionButtonMargin) + bottomBannersHeight
            }
            updateReachArea()
        }
        initializeSearchStatusPanel(view)
        applyBannerPlacement(view)
        updateSearchStatusPlacement()
    }

    /**
     * Puts the search filter bar and the back banner above the list, or directly above the toolbar at the bottom.
     */
    private fun applyBannerPlacement(view: View) {
        val container = view.findViewById<ViewGroup>(R.id.bottom_banners) ?: return
        val filterBar = view.findViewById<View>(R.id.search_filter_bar)
        val backBanner = view.findViewById<View>(R.id.back_to_search_results)
        val isAtBottom = container.indexOfChild(backBanner) >= 0

        if (areBannersAtBottom == isAtBottom) return

        listOf(backBanner, filterBar).forEach { (it.parent as? ViewGroup)?.removeView(it) }
        if (areBannersAtBottom) {
            // The panel with the state of the search is already in the container, between these two.
            container.addView(backBanner, 0)
            container.addView(filterBar)
        } else {
            val top = view.findViewById<RecyclerView>(R.id.message_list).parent as ViewGroup
            top.addView(filterBar, 0)
            top.addView(backBanner, 1)
        }
        if (!areBannersAtBottom) {
            bottomBannersHeight = 0
            applyBottomBarHeight(view)
        }
    }

    private fun hasBottomBanners(): Boolean {
        return areBannersAtBottom &&
            view?.findViewById<ViewGroup>(R.id.bottom_banners)?.children?.any { it.isVisible } == true
    }

    private fun applyListTopPadding() {
        recyclerView?.updatePadding(top = bannerTopPadding + reachAreaHeight)
    }

    /**
     * With the toolbar at the bottom the list gets an empty area above the first message. It is part of the list's top
     * padding (the list doesn't clip to its padding), so the positions of the items stay as they are.
     */
    private fun updateReachArea() {
        val recyclerView = recyclerView ?: return
        val hasMessages = adapter.viewItems.any { it is MessageListViewItem.Message }
        val newHeight = MessageListReachArea.height(recyclerView.height, bottomBarHeight, hasMessages)
        if (newHeight != reachAreaHeight) {
            reachAreaHeight = newHeight
            applyListTopPadding()
        }

        // How much the messages fill the list is only known once it has been laid out.
        recyclerView.doOnPreDraw {
            updateReachAreaExtraBottomPadding()
            anchorListBelowReachAreaIfNeeded()
        }
    }

    /**
     * The first message is shown at the top of the list, unless the user pulled the list down into the reach area.
     * Whenever the list changed under them (messages arrive, padding changes) it is put back there, but only while the
     * list is at its start, so a user who scrolled down to a later message is never moved.
     */
    private fun anchorListBelowReachAreaIfNeeded() {
        val recyclerView = recyclerView ?: return
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val firstItemOffset = firstItemOffset(recyclerView, layoutManager)

        val shouldAnchor = MessageListReachArea.shouldAnchorBelowReachArea(
            isRevealedByUser = isReachAreaRevealedByUser,
            isScrolledAwayByUser = isReachAreaScrolledAwayByUser,
            firstItemOffset = firstItemOffset,
            reachHeight = reachAreaHeight,
        )
        if (shouldAnchor) {
            // Takes effect with the next layout, which doesn't report a scroll, so the bottom bar stays as it is.
            layoutManager.scrollToPositionWithOffset(0, -reachAreaHeight)
        }
    }

    /**
     * Messages that arrive while the first message is shown at the top are shown at the top, too. Without this the
     * list stays at the message that was the first one, and the new messages are above the edge of the list.
     */
    private fun keepFirstMessageOnTopWhenMessagesAreInserted(positionStart: Int) {
        if (positionStart > 1) return

        val recyclerView = recyclerView ?: return
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val shouldKeepOnTop = MessageListReachArea.shouldKeepFirstItemOnTop(
            isRevealedByUser = isReachAreaRevealedByUser,
            firstItemOffset = firstItemOffset(recyclerView, layoutManager),
            reachHeight = reachAreaHeight,
        )
        if (shouldKeepOnTop) {
            layoutManager.scrollToPositionWithOffset(0, -reachAreaHeight)
        }
    }

    private fun firstItemOffset(recyclerView: RecyclerView, layoutManager: LinearLayoutManager): Int? {
        return MessageListReachArea.firstItemOffset(adapter.itemCount, recyclerView.paddingTop) { position ->
            layoutManager.findViewByPosition(position)?.let { ItemBounds(top = it.top, height = it.height) }
        }
    }

    private fun updateReachAreaRevealedByUser() {
        val recyclerView = recyclerView ?: return
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val firstItemOffset = firstItemOffset(recyclerView, layoutManager)

        val wasRevealed = isReachAreaRevealedByUser
        isReachAreaScrolledAwayByUser = MessageListReachArea.isScrolledAway(firstItemOffset, reachAreaHeight)
        isReachAreaRevealedByUser = MessageListReachArea.isRevealed(
            firstItemOffset = firstItemOffset,
            reachHeight = reachAreaHeight,
        )
        if (wasRevealed && !isReachAreaRevealedByUser) {
            recyclerView.performHapticFeedback(dockedHapticFeedbackConstant())
        }
    }

    /**
     * When the user lets go of the list shortly before the first message is back at its normal place, the list
     * snaps there.
     */
    private fun snapToTopIfCloseToIt() {
        val recyclerView = recyclerView ?: return
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val firstItemOffset = firstItemOffset(recyclerView, layoutManager)

        val shouldSnap = MessageListReachArea.shouldSnapToTop(
            firstItemOffset = firstItemOffset,
            reachHeight = reachAreaHeight,
            brokeThroughDetent = brokeThroughDetent,
            detentDistance = MessageListReachArea.detentDistance(detentThreshold(), detentThreshold()).toInt(),
        )
        if (shouldSnap && firstItemOffset != null) {
            isSnappingToTop = true
            recyclerView.smoothScrollBy(0, firstItemOffset + reachAreaHeight)
        }
    }

    private fun detentThreshold(): Float {
        return MessageListReachArea.detentThreshold(reachAreaHeight, resources.displayMetrics.density)
    }

    /**
     * Arms the detent when a drag starts while the first message is at its normal place. A drag that starts in the
     * reach area, or further down the list, isn't held back.
     */
    private fun armDetentIfAtTop() {
        val recyclerView = recyclerView ?: return
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val firstItemOffset = firstItemOffset(recyclerView, layoutManager)

        isDetentArmed = reachAreaHeight > 0 && firstItemOffset != null &&
            !MessageListReachArea.isRevealed(firstItemOffset, reachAreaHeight) &&
            !MessageListReachArea.isScrolledAway(firstItemOffset, reachAreaHeight)
        detentPull = 0f
        detentCarry = 0f
        brokeThroughDetent = false
    }

    private fun disarmDetent() {
        isDetentArmed = false
        detentPull = 0f
        detentCarry = 0f
        brokeThroughDetent = false
    }

    /**
     * Holds back the list while the finger pulls it down from the first message's normal place. It follows only a
     * part of the finger until the pull passes the threshold; then it gives way with a tick and follows the finger.
     */
    private fun adjustScrollForDetent(dy: Int): Int {
        val recyclerView = recyclerView ?: return dy
        if (!isDetentArmed || brokeThroughDetent || recyclerView.scrollState != RecyclerView.SCROLL_STATE_DRAGGING) {
            return dy
        }

        val step = MessageListReachArea.pullThroughDetent(
            pull = detentPull,
            fingerDelta = -dy.toFloat(),
            threshold = detentThreshold(),
        )
        detentPull = step.pull
        if (step.brokeThrough) {
            brokeThroughDetent = true
            recyclerView.performHapticFeedback(dockedHapticFeedbackConstant())
        }

        val wanted = -step.contentDelta + detentCarry
        val whole = wanted.toInt()
        detentCarry = wanted - whole
        return whole
    }

    private fun dockedHapticFeedbackConstant(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
    }

    /**
     * A list with only a few messages can't be scrolled far enough to move the first message to the top. It gets as
     * much space below the messages as is needed for that, a long list gets none.
     */
    private fun updateReachAreaExtraBottomPadding() {
        val recyclerView = recyclerView ?: return
        val newExtra = if (reachAreaHeight > 0) {
            MessageListReachArea.extraBottomPadding(
                listHeight = recyclerView.height,
                topPadding = bannerTopPadding,
                contentHeight = recyclerView.computeVerticalScrollRange(),
                bottomPadding = listBottomPadding(),
            )
        } else {
            0
        }
        if (newExtra == reachAreaExtraBottomPadding) return

        reachAreaExtraBottomPadding = newExtra
        recyclerView.updatePadding(bottom = listBottomPadding() + newExtra)
    }

    private fun listBottomPadding(): Int {
        val basePadding = if (isShowFloatingActionButton) {
            resources.getDimensionPixelSize(R.dimen.floatingActionButtonSpacing)
        } else {
            0
        }
        return basePadding + bottomBarHeight + bottomBannersHeight
    }

    private fun requireCoordinatorLayout(): CoordinatorLayout {
        val coordinatorLayout = coordinatorLayout
            ?: requireView().findViewById<CoordinatorLayout>(R.id.message_list_coordinator)
                .also { coordinatorLayout = it }

        return coordinatorLayout ?: error("Coordinator layout not initialized")
    }

    private suspend fun onSnackbarInAppNotificationEvent(visual: SnackbarVisual) {
        val (message, action, duration) = visual
        Snackbar.make(
            requireCoordinatorLayout(),
            message,
            when (duration) {
                SnackbarDuration.Short -> Snackbar.LENGTH_SHORT
                SnackbarDuration.Long -> Snackbar.LENGTH_LONG
                SnackbarDuration.Indefinite -> Snackbar.LENGTH_INDEFINITE
            },
        ).apply {
            if (action != null) {
                setAction(
                    action.resolveTitle(),
                ) {
                    // TODO.
                }
            }
        }.show()
    }

    private val shouldShowRecentChangesHintObserver = Observer<Boolean> { showRecentChangesHint ->
        val recentChangesSnackbarVisible = recentChangesSnackbar.isShown
        if (showRecentChangesHint && !recentChangesSnackbarVisible) {
            recentChangesSnackbar.show()
        } else if (!showRecentChangesHint && recentChangesSnackbarVisible) {
            recentChangesSnackbar.dismiss()
        }
    }

    private fun initializeRecentChangesSnackbar() {
        val coordinatorLayout = requireCoordinatorLayout()

        recentChangesSnackbar = Snackbar
            .make(coordinatorLayout, R.string.changelog_snackbar_text, RECENT_CHANGES_SNACKBAR_DURATION)
            .setAction(R.string.changelog_snackbar_button_text) { launchRecentChangesActivity() }
            .addCallback(
                object : BaseCallback<Snackbar>() {
                    override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                        if (event == DISMISS_EVENT_SWIPE || event == DISMISS_EVENT_TIMEOUT) {
                            recentChangesViewModel.onRecentChangesHintDismissed()
                        }
                    }
                },
            )

        recentChangesViewModel.shouldShowRecentChangesHint
            .observe(viewLifecycleOwner, shouldShowRecentChangesHintObserver)
    }

    private fun launchRecentChangesActivity() {
        recentChangesViewModel.shouldShowRecentChangesHint.removeObserver(shouldShowRecentChangesHintObserver)

        FeatureLauncherActivity.launch(
            context = requireContext(),
            target = FeatureLauncherTarget.Changelog(changeLogMode = ChangeLogMode.RECENT_CHANGES),
        )
    }

    private fun initializeSortSettings() {
        if (isSingleAccountMode) {
            val account = checkNotNull(this.account)
            sortType = account.sortType
            sortAscending = account.sortAscending[sortType] ?: sortType.isDefaultAscending
            sortDateAscending = account.sortAscending[SortType.SORT_DATE] ?: SortType.SORT_DATE.isDefaultAscending
        } else {
            sortType = K9.sortType
            sortAscending = K9.isSortAscending(sortType)
            sortDateAscending = K9.isSortAscending(SortType.SORT_DATE)
        }
    }

    private fun loadMessageList(forceUpdate: Boolean = false) {
        val config = MessageListConfig(
            localSearch,
            showingThreadedList,
            sortType,
            sortAscending,
            sortDateAscending,
            activeMessage,
            legacyViewModel.messageSortOverrides.toMap(),
            legacyViewModel.remoteSearchResults.toSet(),
        )

        if (forceUpdate) {
            accounts = config.search.getLegacyAccounts(accountManager)
        }

        legacyViewModel.loadMessageList(config, forceUpdate)
    }

    override fun folderLoading(folderId: Long, loading: Boolean) {
        currentFolder?.let {
            if (it.databaseId == folderId) {
                it.loading = loading
                updateFooterText()
            }
        }
    }

    override fun updateTitle() {
        if (error != null) {
            fragmentListener.setMessageListTitle(getString(R.string.message_list_error_title))
            return
        } else if (!isInitialized) {
            return
        }

        setWindowTitle()

        if (!localSearch.isManualSearch) {
            setWindowProgress()
        }
    }

    override fun setBottomBarHeight(height: Int) {
        if (bottomBarHeight == height) return

        bottomBarHeight = height
        view?.let { applyBottomBarHeight(it) }
        updateReachArea()
    }

    override fun setBannersAtBottom(atBottom: Boolean) {
        if (areBannersAtBottom == atBottom) return

        areBannersAtBottom = atBottom
        view?.let { applyBannerPlacement(it) }
        updateSearchStatusPlacement()
    }

    private fun setWindowProgress() {
        var level = 0
        if (currentFolder?.loading == true) {
            val folderTotal = activityListener.getFolderTotal()
            if (folderTotal > 0) {
                level = (MAX_PROGRESS * activityListener.getFolderCompleted() / folderTotal).coerceAtMost(MAX_PROGRESS)
            }
        }

        fragmentListener.setMessageListProgress(level)
    }

    private fun setWindowTitle() {
        val unifiedFolderType = localSearch.unifiedSpecialFolderType

        val title = when {
            unifiedFolderType != null -> getString(unifiedFolderTitle(unifiedFolderType))
            isUnifiedFoldersView -> getString(unifiedFolderTitle(FolderType.INBOX))
            isNewMessagesView -> getString(R.string.new_messages_title)
            // The search results display the search field with their query instead of a title.
            isManualSearch -> ""
            isThreadDisplay -> threadTitle ?: ""
            isSingleFolderMode -> currentFolder!!.displayName
            else -> ""
        }

        val currentAccount = account
        val subtitle = when {
            unifiedFolderType != null -> getString(R.string.unified_folders_subtitle)
            isUnifiedFoldersView -> getString(R.string.unified_folders_subtitle)
            currentAccount == null || isManualSearch || accountManager.findAll().size == 1 -> null
            else -> currentAccount.profile.name
        }

        fragmentListener.setMessageListTitle(title, subtitle)
    }

    override fun progress(progress: Boolean) {
        if (!progress) {
            swipeRefreshLayout?.isRefreshing = false
        }

        fragmentListener.setMessageListProgressEnabled(progress)
    }

    override fun onFooterClicked() {
        val account = this.account
        val currentFolder = this.currentFolder
        if (account == null || currentFolder == null) {
            // A search spanning multiple folders or accounts only offers to continue on the server.
            if (isRemoteSearchAllowed) {
                onRemoteSearch()
            }
            return
        }

        if (currentFolder.moreMessages && !localSearch.isManualSearch && isRemoteFolder) {
            loadMoreMessages()
        } else if (isRemoteSearch) {
            val additionalSearchResults = extraSearchResults ?: return
            if (additionalSearchResults.isEmpty()) return

            val loadSearchResults: List<String>

            val limit = account.remoteSearchNumResults
            if (limit in 1 until additionalSearchResults.size) {
                extraSearchResults = additionalSearchResults.subList(limit, additionalSearchResults.size)
                loadSearchResults = additionalSearchResults.subList(0, limit)
            } else {
                extraSearchResults = null
                loadSearchResults = additionalSearchResults
                updateFooterText(null)
            }

            messagingController.loadSearchResults(
                account.id,
                currentFolder.databaseId,
                loadSearchResults,
                activityListener,
            )
        } else if (isRemoteSearchAllowed) {
            onRemoteSearch()
        }
    }

    override fun onMessageClicked(messageListItem: MessageListItem) {
        if (!isActive) {
            // Ignore click events that are delivered after the Fragment is no longer active. This could happen when
            // the user taps two messages at almost the same time and the first tap opens a new MessageListFragment.
            return
        }

        val clickTime = SystemClock.elapsedRealtime()
        if (clickTime - lastMessageClick < MINIMUM_CLICK_INTERVAL) return

        if (adapter.selectedCount > 0) {
            toggleMessageSelect(messageListItem)
        } else {
            lastMessageClick = clickTime
            if (showingThreadedList && messageListItem.threadCount > 1) {
                fragmentListener.showThread(messageListItem.account.id, messageListItem.threadRoot)
            } else {
                openMessage(messageListItem.messageReference)
            }
        }
    }

    override fun onMessageFolderClicked(messageListItem: MessageListItem) {
        if (!isActive) return

        val clickTime = SystemClock.elapsedRealtime()
        if (clickTime - lastMessageClick < MINIMUM_CLICK_INTERVAL) return

        if (adapter.selectedCount > 0) {
            toggleMessageSelect(messageListItem)
        } else {
            lastMessageClick = clickTime
            fragmentListener.showFolder(messageListItem.account, messageListItem.folderId)
        }
    }

    override fun onDestroyView() {
        commitPendingMessageAction()

        handler.removeCallbacks(stopRemoteSearchRunnable)
        handler.removeCallbacks(loadedMessagesDisplayedRunnable)
        isWaitingForLoadedMessages = false
        coordinatorLayout = null
        recyclerView = null
        searchStatusPanel = null
        reachAreaHeight = 0
        reachAreaExtraBottomPadding = 0
        bannerTopPadding = 0
        isReachAreaRevealedByUser = false
        isReachAreaScrolledAwayByUser = false
        isUserScrollGesture = false
        isSnappingToTop = false
        disarmDetent()
        messageListSwipeCallback = null
        itemTouchHelper = null
        swipeRefreshLayout = null
        floatingActionButton = null

        if (isNewMessagesView && !requireActivity().isChangingConfigurations) {
            account?.id?.let { messagingController.clearNewMessages(it) }
        }

        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        if (error != null) return

        outState.putLongArray(STATE_SELECTED_MESSAGES, adapter.selected.toLongArray())
        outState.putBoolean(STATE_REMOTE_SEARCH_PERFORMED, isRemoteSearch)
        searchView?.let { searchView ->
            outState.putString(STATE_SEARCH_VIEW_QUERY, searchView.query.toString())
            outState.putBoolean(STATE_SEARCH_VIEW_ICONIFIED, searchView.isIconified)
        }
        outState.putStringArray(
            STATE_ACTIVE_MESSAGES,
            activeMessages?.map(MessageReference::toIdentityString)?.toTypedArray(),
        )
        outState.putSerializable(STATE_SEARCH_FILTERS, searchFilters)
        if (activeMessage != null) {
            outState.putString(STATE_ACTIVE_MESSAGE, activeMessage!!.toIdentityString())
        }
    }

    private val messageListAppearance: MessageListAppearance
        get() = MessageListAppearance(
            fontSizes = K9.fontSizes,
            previewLines = messageListSettings.previewLines,
            stars = !isOutbox && generalSettingsManager.getConfig().display.inboxSettings.isShowMessageListStars,
            senderAboveSubject = generalSettingsManager
                .getConfig()
                .display
                .inboxSettings
                .isMessageListSenderAboveSubject,
            showContactPicture = messageListSettings.isShowContactPicture,
            showingThreadedList = showingThreadedList,
            backGroundAsReadIndicator = messageListSettings.isUseBackgroundAsUnreadIndicator,
            showAccountIndicator = isShowAccountIndicator,
            density = messageListSettings.uiDensity,
            dateTimeFormat = messageListSettings.dateTimeFormat,
        )

    private fun getFolderInfoHolder(account: LegacyAccount, folderId: Long): FolderInfoHolder {
        val localStore = localStoreProvider.getInstance(account.id)
        val localFolder = localStore.getFolder(folderId)
        localFolder.open()
        return FolderInfoHolder(folderNameFormatter, outboxFolderManager, localFolder, account)
    }

    override fun onResume() {
        super.onResume()

        if (hasConnectivity == null) {
            hasConnectivity = connectivityManager.isNetworkAvailable()
        }

        messagingControllerRegistry.addListener(activityListener)

        updateTitle()

        if (!initialMessageListLoad) {
            startAutomaticRemoteSearch()
        }
        continueStoppedRemoteSearch()
    }

    /**
     * A search keeps running while the user has a quick look at one of its results. When the user stays with the
     * message, the search is stopped to not use the connection for something nobody is waiting for. It continues
     * when the user returns to the search results.
     */
    private fun onActiveChanged() {
        handler.removeCallbacks(stopRemoteSearchRunnable)
        if (isActive) {
            if (isResumed) {
                continueStoppedRemoteSearch()
            }
        } else if (remoteSearchFuture != null) {
            handler.postDelayed(stopRemoteSearchRunnable, REMOTE_SEARCH_READING_TIME)
        }
    }

    private fun continueStoppedRemoteSearch() {
        val stoppedRemoteSearch = stoppedRemoteSearch ?: return
        if (!isActive || remoteSearchFuture != null || !connectivityManager.isNetworkAvailable()) return

        onRemoteSearchRequested(stoppedRemoteSearch.searchMessageContents, stoppedRemoteSearch.searchedFolders)
    }

    override fun onPause() {
        super.onPause()

        commitPendingMessageAction()

        recyclerView?.removeCallbacks(automaticRemoteSearchRunnable)
        isAutomaticRemoteSearchScheduled = false

        messagingControllerRegistry.removeListener(activityListener)
    }

    private fun goBack() {
        fragmentListener.goBack()
    }

    override fun onCompose() {
        if (!isSingleAccountMode) {
            fragmentListener.onCompose(null)
        } else {
            fragmentListener.onCompose(account?.id)
        }
    }

    private fun changeSort(sortType: SortType) {
        val sortAscending = if (this.sortType == sortType) !sortAscending else null
        changeSort(sortType, sortAscending)
    }

    /**
     * @param searchedFolders The folder keys of the folders a search that was stopped has already searched. The
     *   search continues after them.
     */
    private fun onRemoteSearchRequested(
        searchMessageContents: Boolean = false,
        searchedFolders: Set<String> = emptySet(),
    ) {
        val queryString = manualSearchQuery ?: return

        // A search that is still running is replaced, e.g. when the user doesn't want to wait for it to finish
        // before the message contents are searched.
        remoteSearchListener = null
        remoteSearchFuture?.cancel(true)
        stoppedRemoteSearch = null

        isRemoteSearch = true
        isSearchingMessageContents = searchMessageContents
        isAttachmentSearch = false
        hasRemoteSearchFailed = false
        isRemoteSearchStopped = false
        remoteSearchProblem = null
        remoteSearchProblemReason = null
        runningSearchAccounts.clear()
        remoteSearchSearchedFolders.clear()
        remoteSearchSearchedFolders.addAll(searchedFolders)
        remoteSearchFolderIndex = searchedFolders.size
        if (searchedFolders.isEmpty()) {
            remoteSearchFoundCount = 0
            remoteSearchNotLoadedCount = 0
        }
        swipeRefreshLayout?.isEnabled = false

        // Widen the visible search to all folders and accounts so that results found by the
        // cascading remote search show up in this list. Unless the user only wants to search the folder the search
        // was started from.
        val startAccount = account
        val startFolder = currentFolder
        val isLimitedToFolder = searchScope == SearchScope.FOLDER && startAccount != null && startFolder != null
        val searchCriteria = createSearchCriteria(queryString, searchMessageContents)
        localSearch = createManualQuerySearch(
            queryString,
            searchMessageContents = searchCriteria.textScope != ServerSearchCriteria.TextScope.HEADERS,
        ).apply {
            if (isLimitedToFolder) {
                addAccountId(startAccount!!.id)
                addAllowedFolder(startFolder!!.databaseId)
            }
        }
        loadMessageList(forceUpdate = true)

        // The account and folder the search was started from are searched first. A search that was started from
        // multiple folders or accounts, e.g. the Unified Folders, has neither.
        remoteSearchFuture = messagingController.searchRemoteMessagesCascading(
            account?.id,
            currentFolder?.databaseId,
            searchCriteria,
            isLimitedToFolder,
            remoteSearchSearchedFolders.toSet(),
            RemoteSearchListener().also { remoteSearchListener = it },
        )

        showSearchStatus(
            ServerSearchStatus.Running(
                title = getString(R.string.remote_search_sending_query),
                isMessageContentsSearchOffered = !searchMessageContents && isMessageContentsSearchAllowed,
            ),
        )
        invalidateMenu()
    }

    /**
     * Search the attachments of the messages for the words of the search.
     *
     * The messages that have attachments are downloaded completely, so the words of the search can be found in their
     * attachments on the device.
     */
    private fun onAttachmentSearchRequested() {
        val queryString = manualSearchQuery ?: return

        // A search that is still running is replaced by the search of the attachments.
        remoteSearchListener = null
        remoteSearchFuture?.cancel(true)
        stoppedRemoteSearch = null

        isRemoteSearch = true
        isSearchingMessageContents = false
        isAttachmentSearch = true
        hasRemoteSearchFailed = false
        isRemoteSearchStopped = false
        remoteSearchProblem = null
        remoteSearchProblemReason = null
        runningSearchAccounts.clear()
        remoteSearchSearchedFolders.clear()
        remoteSearchFolderIndex = 0
        remoteSearchFoundCount = 0
        remoteSearchNotLoadedCount = 0
        swipeRefreshLayout?.isEnabled = false

        // Widen the visible search to all folders and accounts, so the messages that are found show up in this list.
        // Unless the user only wants to search the folder the search was started from.
        val startAccount = account
        val startFolder = currentFolder
        val isLimitedToFolder = searchScope == SearchScope.FOLDER && startAccount != null && startFolder != null
        localSearch = createManualQuerySearch(queryString).apply {
            if (isLimitedToFolder) {
                addAccountId(startAccount!!.id)
                addAllowedFolder(startFolder!!.databaseId)
            }
        }
        loadMessageList(forceUpdate = true)

        // The words of the search are looked for on the device, so the server only looks for messages with
        // attachments.
        remoteSearchFuture = messagingController.searchAttachmentsOnServer(
            account?.id,
            currentFolder?.databaseId,
            searchFilters.toServerSearchCriteria(queryString, Calendar.getInstance()),
            isLimitedToFolder,
            RemoteSearchListener().also { remoteSearchListener = it },
        )

        showSearchStatus(
            ServerSearchStatus.Running(
                title = getString(R.string.remote_search_sending_query),
            ),
        )
        invalidateMenu()
    }

    /**
     * The criteria the search on the server uses.
     *
     * @param searchMessageContents Whether the contents of the messages are searched, too.
     */
    private fun createSearchCriteria(queryString: String, searchMessageContents: Boolean): ServerSearchCriteria {
        val criteria = searchFilters.toServerSearchCriteria(queryString, Calendar.getInstance())

        // The message contents are only searched when the text of the search field is looked for in the headers.
        return if (searchMessageContents && criteria.textScope == ServerSearchCriteria.TextScope.HEADERS) {
            criteria.copy(textScope = ServerSearchCriteria.TextScope.EVERYWHERE)
        } else {
            criteria
        }
    }

    /**
     * Change the sort type and sort order used for the message list.
     *
     * @param sortType Specifies which field to use for sorting the message list.
     * @param sortAscending Specifies the sort order. If this argument is `null` the default search order for the
     *   sort type is used.
     */
    // FIXME: Don't save the changes in the UI thread
    private fun changeSort(sortType: SortType, sortAscending: Boolean?) {
        this.sortType = sortType
        val account = this.account
        if (account != null) {
            val resolvedAscending = sortAscending ?: (account.sortAscending[sortType] ?: sortType.isDefaultAscending)
            this.sortAscending = resolvedAscending

            val newSortAscendingMap = account.sortAscending.toMutableMap().apply {
                this[sortType] = resolvedAscending
            }

            this.sortDateAscending = newSortAscendingMap[SortType.SORT_DATE] ?: SortType.SORT_DATE.isDefaultAscending

            lifecycleScope.launch(Dispatchers.IO) {
                val updatedAccount = accountManager.updateSync(account.id) { latestAccount ->
                    latestAccount.copy(
                        sortType = sortType,
                        sortAscending = latestAccount.sortAscending + (sortType to resolvedAscending),
                    )
                }
                if (updatedAccount != null) {
                    this@LegacyMessageListFragment.account = updatedAccount
                }
            }
        } else {
            K9.sortType = this.sortType
            if (sortAscending == null) {
                this.sortAscending = K9.isSortAscending(this.sortType)
            } else {
                this.sortAscending = sortAscending
            }
            K9.setSortAscending(this.sortType, this.sortAscending)
            sortDateAscending = K9.isSortAscending(SortType.SORT_DATE)

            K9.saveSettingsAsync()
        }

        reSort()
    }

    private fun reSort() {
        val toastString = sortTypeToastProvider.getToast(sortType, sortAscending)
        Toast.makeText(activity, toastString, Toast.LENGTH_SHORT).show()
        loadMessageList()
    }

    override fun onCycleSort() {
        val sortTypes = SortType.entries
        val currentIndex = sortTypes.indexOf(sortType)
        val newIndex = if (currentIndex == sortTypes.lastIndex) 0 else currentIndex + 1
        val nextSortType = sortTypes[newIndex]
        changeSort(nextSortType)
    }

    private fun onDelete(messages: List<MessageReference>, count: Int) {
        if (interactionSettings.isConfirmDelete) {
            // remember the message selection for #onCreateDialog(int)
            activeMessages = messages
            showDialog(R.id.dialog_confirm_delete, count)
        } else {
            onDeleteConfirmed(messages)
        }
    }

    private fun onDeleteConfirmed(messages: List<MessageReference>) {
        val threaded = showingThreadedList
        removeMessagesWithUndo(messages, MessageRemoval.DELETE) { deleteMessagesNow(messages, threaded) }
    }

    private fun deleteMessagesNow(messages: List<MessageReference>, threaded: Boolean) {
        if (threaded) {
            messagingController.deleteThreads(messages)
        } else {
            messagingController.deleteMessages(messages)
        }
    }

    /**
     * Remove messages from the message list, but only after the user didn't undo the removal.
     *
     * The messages are hidden right away. They're really removed when the snackbar goes away without the user
     * choosing to undo the removal.
     */
    override fun removeMessagesWithUndo(
        messages: List<MessageReference>,
        removal: MessageRemoval,
        perform: () -> Unit,
    ) {
        if (messages.isEmpty()) return

        // Only one undo snackbar is shown at a time.
        undoSnackbar?.dismiss()

        // The pending action is carried out when the new action is started. Its messages stay hidden until the
        // message list loader delivers a list that doesn't contain them anymore.
        hiddenUntilReload = hiddenUntilReload + pendingMessageAction.pendingMessages

        val id = pendingMessageAction.start(messages, perform)

        // Hide the messages right away. They only disappear for good when the removal is committed.
        lastMessageListInfo?.let { setMessageList(it, fromLoader = false) }

        val snackbar = Snackbar
            .make(
                undoSnackbarView(),
                resources.getQuantityString(removal.undoText, messages.size, messages.size),
                UNDO_SNACKBAR_DURATION_MS,
            )
            .setAction(R.string.message_list_undo_action) { undoMessageRemoval(id) }
            .addCallback(
                object : BaseCallback<Snackbar>() {
                    override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                        if (event != DISMISS_EVENT_ACTION) {
                            pendingMessageAction.commit(id)
                        }
                    }
                },
            )

        snackbar.show()
        undoSnackbar = snackbar
    }

    /**
     * Show a snackbar after messages were marked as read or unread. Undo sets every message back to the state it had
     * before. The new state is applied right away, so there's nothing to commit when the snackbar goes away.
     */
    override fun offerReadStateUndo(changes: List<ReadStateChange>, markedRead: Boolean) {
        val restoreActions = restoreReadStateActions(changes, markedRead)
        if (restoreActions.isEmpty()) return

        // Only one undo snackbar is shown at a time. Dismissing it carries out a pending removal.
        undoSnackbar?.dismiss()

        val textResId = if (markedRead) {
            R.plurals.message_list_undo_marked_read
        } else {
            R.plurals.message_list_undo_marked_unread
        }
        val snackbar = Snackbar
            .make(
                undoSnackbarView(),
                resources.getQuantityString(textResId, changes.size, changes.size),
                UNDO_SNACKBAR_DURATION_MS,
            )
            .setAction(R.string.message_list_undo_action) { restoreReadState(restoreActions) }

        snackbar.show()
        undoSnackbar = snackbar
    }

    private fun restoreReadState(actions: List<ReadStateRestoreAction>) {
        for (action in actions) {
            if (action.isThread) {
                messagingController.setFlagForThreads(action.accountId, action.ids, Flag.SEEN, action.read)
            } else {
                messagingController.setFlag(action.accountId, action.ids, Flag.SEEN, action.read)
            }
        }

        computeBatchDirection()
    }

    private fun readStateChangeOf(item: MessageListItem): ReadStateChange {
        val isThread = showingThreadedList && item.threadCount > 1
        return ReadStateChange(
            accountId = item.account.id,
            id = if (isThread) item.threadRoot else item.databaseId,
            isThread = isThread,
            wasRead = item.isRead,
        )
    }

    private fun markReadWithUndo(item: MessageListItem, read: Boolean) {
        val change = readStateChangeOf(item)
        setFlag(item, Flag.SEEN, read)
        offerReadStateUndo(listOf(change), read)
    }

    private fun markSelectedReadWithUndo(read: Boolean) {
        val changes = adapter.selectedMessages.map(::readStateChangeOf)
        setFlagForSelected(Flag.SEEN, read)
        offerReadStateUndo(changes, read)
    }

    /**
     * The view the undo snackbar is displayed in.
     *
     * While a message is shown full screen the message list is hidden. The snackbar is displayed in the activity's
     * content view in that case.
     */
    private fun undoSnackbarView(): View {
        val coordinatorLayout = requireCoordinatorLayout()
        return if (coordinatorLayout.isShown) {
            coordinatorLayout
        } else {
            requireActivity().findViewById<View>(android.R.id.content)
        }
    }

    private fun undoMessageRemoval(id: Long) {
        if (pendingMessageAction.undo(id)) {
            // Bring the messages back.
            lastMessageListInfo?.let { setMessageList(it, fromLoader = false) }
        }
    }

    /**
     * Carry out the action that is waiting for the user to decide whether to undo it.
     */
    private fun commitPendingMessageAction() {
        pendingMessageAction.commitPending()
        undoSnackbar?.dismiss()
        undoSnackbar = null
    }

    private fun onExpunge() {
        currentFolder?.let { folderInfoHolder ->
            account?.id?.let { messagingController.expunge(it, folderInfoHolder.databaseId) }
        }
    }

    private fun onEmptySpam() {
        if (isShowingSpamFolder) {
            showDialog(R.id.dialog_confirm_empty_spam)
        }
    }

    private val isShowingSpamFolder: Boolean
        get() {
            if (!isSingleFolderMode) return false
            return currentFolder!!.databaseId == account!!.spamFolderId
        }

    private fun onEmptyTrash() {
        if (isShowingTrashFolder) {
            showDialog(R.id.dialog_confirm_empty_trash)
        }
    }

    private val isShowingTrashFolder: Boolean
        get() {
            if (!isSingleFolderMode) return false
            return currentFolder!!.databaseId == account!!.trashFolderId
        }

    private fun showDialog(dialogId: Int, selectionCount: Int = 1) {
        val dialogFragment = when (dialogId) {
            R.id.dialog_confirm_spam -> {
                val title = getString(R.string.dialog_confirm_spam_title)
                val message = resources.getQuantityString(
                    R.plurals.dialog_confirm_spam_message,
                    selectionCount,
                    selectionCount,
                )
                val confirmText = getString(R.string.dialog_confirm_spam_confirm_button)
                val cancelText = getString(R.string.dialog_confirm_spam_cancel_button)
                ConfirmationDialogFragment.newInstance(dialogId, title, message, confirmText, cancelText)
            }

            R.id.dialog_confirm_delete -> {
                val title = getString(R.string.dialog_confirm_delete_title)
                val message = resources.getQuantityString(
                    R.plurals.dialog_confirm_delete_messages,
                    selectionCount,
                    selectionCount,
                )
                val confirmText = getString(R.string.dialog_confirm_delete_confirm_button)
                val cancelText = getString(R.string.dialog_confirm_delete_cancel_button)
                ConfirmationDialogFragment.newInstance(dialogId, title, message, confirmText, cancelText)
            }

            R.id.dialog_confirm_mark_all_as_read -> {
                val title = getString(R.string.dialog_confirm_mark_all_as_read_title)
                val message = getString(R.string.dialog_confirm_mark_all_as_read_message)
                val confirmText = getString(R.string.dialog_confirm_mark_all_as_read_confirm_button)
                val cancelText = getString(R.string.dialog_confirm_mark_all_as_read_cancel_button)
                ConfirmationDialogFragment.newInstance(dialogId, title, message, confirmText, cancelText)
            }

            R.id.dialog_confirm_empty_spam -> {
                val title = getString(R.string.dialog_confirm_empty_spam_title)
                val message = getString(R.string.dialog_confirm_empty_spam_message)
                val confirmText = getString(R.string.dialog_confirm_delete_confirm_button)
                val cancelText = getString(R.string.dialog_confirm_delete_cancel_button)
                ConfirmationDialogFragment.newInstance(dialogId, title, message, confirmText, cancelText)
            }

            R.id.dialog_confirm_empty_trash -> {
                val title = getString(R.string.dialog_confirm_empty_trash_title)
                val message = getString(R.string.dialog_confirm_empty_trash_message)
                val confirmText = getString(R.string.dialog_confirm_delete_confirm_button)
                val cancelText = getString(R.string.dialog_confirm_delete_cancel_button)
                ConfirmationDialogFragment.newInstance(dialogId, title, message, confirmText, cancelText)
            }

            else -> {
                throw RuntimeException("Called showDialog(int, int) with unknown dialog id.")
            }
        }

        dialogFragment.setTargetFragment(this, dialogId)
        dialogFragment.show(parentFragmentManager, getDialogTag(dialogId))
    }

    private fun getDialogTag(dialogId: Int): String {
        return "dialog-$dialogId"
    }

    private fun prepareSearchMenu(menu: Menu) {
        val searchItem = menu.findItem(R.id.search)

        searchView?.let { searchView ->
            searchItem.actionView = searchView
            return
        }

        val searchView = searchItem.actionView as SearchView
        searchView.maxWidth = Int.MAX_VALUE
        searchView.queryHint = resources.getString(R.string.search_action)
        searchView.setOnQueryTextListener(
            object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean {
                    submitSearch(query)
                    return true
                }

                override fun onQueryTextChange(s: String): Boolean {
                    return false
                }
            },
        )

        initializeSearchSuggestions(searchView)

        if (isManualSearch) {
            // The search results display their query so it can be changed. Leave room for the other toolbar actions.
            searchView.maxWidth = resources.displayMetrics.widthPixels -
                resources.getDimensionPixelSize(R.dimen.messageListSearchResultsToolbarReservedWidth)
            searchView.setIconifiedByDefault(false)
            searchView.setQuery(initialSearchViewQuery ?: manualSearchQuery, false)
            searchView.clearFocus()
        } else {
            searchView.setQuery(initialSearchViewQuery, false)
            searchView.isIconified = initialSearchViewIconified
        }

        this.searchView = searchView
    }

    /**
     * Suggest the most recent search queries below the search field.
     */
    private fun initializeSearchSuggestions(searchView: SearchView) {
        val searchHistoryAdapter = SearchHistoryAdapter(
            context = searchView.context,
            queries = { if (isSearchHistoryEnabled) searchHistory.getQueries() else emptyList() },
            onRemoveQuery = { query -> searchHistory.remove(query) },
        )
        searchView.suggestionsAdapter = searchHistoryAdapter

        // Display the suggestions as soon as the search field is opened, not only once the user has typed something.
        searchView.findViewById<AutoCompleteTextView>(AppCompatR.id.search_src_text)?.threshold = 0

        searchView.setOnSuggestionListener(
            object : SearchView.OnSuggestionListener {
                override fun onSuggestionSelect(position: Int): Boolean = false

                override fun onSuggestionClick(position: Int): Boolean {
                    searchHistoryAdapter.getQuery(position)?.let { query -> submitSearch(query) }
                    return true
                }
            },
        )
    }

    private val isSearchHistoryEnabled: Boolean
        get() = generalSettingsManager.getConfig().privacy.isSearchHistoryEnabled

    private fun submitSearch(query: String) {
        if (isSearchHistoryEnabled) {
            searchHistory.add(query)
        }

        if (isManualSearch) {
            replaceSearch(query)
        } else {
            onSearchRequested(query)
            collapseSearchView()
        }
    }

    /**
     * Display the results for [query] instead of the current search results. The new search covers the same
     * account and folder as the current one.
     */
    private fun replaceSearch(query: String) {
        searchView?.clearFocus()

        val currentIntent = requireActivity().intent
        val searchIntent = Intent(requireContext(), MessageSearchActivity::class.java).apply {
            action = Intent.ACTION_SEARCH
            putExtra(SearchManager.QUERY, query)
            putExtra(SearchManager.APP_DATA, currentIntent.getBundleExtra(SearchManager.APP_DATA))

            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        startActivity(searchIntent)
    }

    private fun prepareMenu(menu: Menu) {
        menu.findItem(R.id.compose).isVisible = !isShowFloatingActionButton
        prepareSortMenu(menu)
        menu.findItem(R.id.select_all).isVisible = true
        menu.findItem(R.id.mark_all_as_read).isVisible = isMarkAllAsReadSupported
        menu.findItem(R.id.empty_spam).isVisible = isShowingSpamFolder
        menu.findItem(R.id.empty_trash).isVisible = isShowingTrashFolder

        if (isSingleAccountMode) {
            menu.findItem(R.id.send_messages).isVisible = isOutbox
            menu.findItem(R.id.expunge).isVisible = isRemoteFolder && shouldShowExpungeAction()
        } else {
            menu.findItem(R.id.send_messages).isVisible = false
            menu.findItem(R.id.expunge).isVisible = false
        }

        menu.findItem(R.id.search_remote).isVisible = !isRemoteSearch && isRemoteSearchAllowed
        menu.findItem(R.id.search_everywhere).isVisible = isManualSearch && !localSearch.searchAllAccounts()
    }

    private fun prepareSortMenu(menu: Menu) {
        menu.findItem(R.id.set_sort).isVisible = true

        menu.findItem(R.id.set_sort_date).setTitle(R.string.sort_by_date)
        menu.findItem(R.id.set_sort_arrival).setTitle(R.string.sort_by_arrival)
        menu.findItem(R.id.set_sort_subject).setTitle(R.string.sort_by_subject)
        menu.findItem(R.id.set_sort_sender).setTitle(R.string.sort_by_sender)
        menu.findItem(R.id.set_sort_unread).setTitle(R.string.sort_by_unread)
        menu.findItem(R.id.set_sort_flag).setTitle(R.string.sort_by_flag)
        menu.findItem(R.id.set_sort_attach).setTitle(R.string.sort_by_attach)

        val selectedSortItem = menu.findItem(getSelectedSortId())
        selectedSortItem.title = "${resources.getString(R.string.sort_by_indicator)} ${selectedSortItem.title}"
    }

    private fun getSelectedSortId(): Int {
        return when (this.sortType) {
            SortType.SORT_DATE -> R.id.set_sort_date
            SortType.SORT_ARRIVAL -> R.id.set_sort_arrival
            SortType.SORT_SUBJECT -> R.id.set_sort_subject
            SortType.SORT_SENDER -> R.id.set_sort_sender
            SortType.SORT_UNREAD -> R.id.set_sort_unread
            SortType.SORT_FLAGGED -> R.id.set_sort_flag
            SortType.SORT_ATTACHMENT -> R.id.set_sort_attach
        }
    }

    private fun prepareDebugMenu(menu: Menu) {
        val isOAuthAccount = account?.incomingServerSettings?.authenticationType == AuthType.XOAUTH2
        val isDebug = BuildConfig.DEBUG
        val showOAuthDebug = isDebug && isOAuthAccount

        menu.findItem(R.id.debug_invalidate_access_token_local).isVisible = showOAuthDebug
        menu.findItem(R.id.debug_invalidate_access_token_server).isVisible = showOAuthDebug
        menu.findItem(R.id.debug_force_auth_failure).isVisible = showOAuthDebug
        menu.findItem(R.id.debug_feature_flags).isVisible = isDebug
    }

    override fun collapseSearchView() {
        searchView?.let { searchView ->
            if (isManualSearch) {
                // Discard the changes to the query of the displayed search results
                searchView.setQuery(manualSearchQuery, false)
                searchView.clearFocus()
            } else {
                searchView.setQuery(null, false)
                searchView.isIconified = true
            }
        }
    }

    private fun selectMenuItem(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.search_remote -> onRemoteSearch()
            R.id.compose -> onCompose()
            R.id.set_sort_date -> changeSort(SortType.SORT_DATE)
            R.id.set_sort_arrival -> changeSort(SortType.SORT_ARRIVAL)
            R.id.set_sort_subject -> changeSort(SortType.SORT_SUBJECT)
            R.id.set_sort_sender -> changeSort(SortType.SORT_SENDER)
            R.id.set_sort_flag -> changeSort(SortType.SORT_FLAGGED)
            R.id.set_sort_unread -> changeSort(SortType.SORT_UNREAD)
            R.id.set_sort_attach -> changeSort(SortType.SORT_ATTACHMENT)
            R.id.select_all -> selectAll()
            R.id.mark_all_as_read -> confirmMarkAllAsRead()
            R.id.send_messages -> onSendPendingMessages()
            R.id.empty_spam -> onEmptySpam()
            R.id.empty_trash -> onEmptyTrash()
            R.id.expunge -> onExpunge()
            R.id.search_everywhere -> onSearchEverywhere()
            R.id.debug_invalidate_access_token_local -> onDebugInvalidateAccessTokenLocal()
            R.id.debug_invalidate_access_token_server -> onDebugInvalidateAccessTokenServer()
            R.id.debug_force_auth_failure -> onDebugForceAuthFailure()
            R.id.debug_feature_flags -> FeatureLauncherActivity.launch(
                context = requireContext(),
                target = FeatureLauncherTarget.SecretDebugSettingsFeatureFlag,
            )

            else -> return super.onOptionsItemSelected(item)
        }

        return true
    }

    private fun onSearchEverywhere() {
        val searchQuery = requireActivity().intent.getStringExtra(SearchManager.QUERY)

        val searchIntent = Intent(requireContext(), MessageSearchActivity::class.java).apply {
            action = Intent.ACTION_SEARCH
            putExtra(SearchManager.QUERY, searchQuery)

            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        startActivity(searchIntent)
    }

    private fun onSendPendingMessages() {
        account?.id?.let { messagingController.sendPendingMessages(it, null) }
    }

    private fun onDebugInvalidateAccessTokenServer() {
        val uuid = account?.id
        if (!BuildConfig.DEBUG || uuid == null) {
            Toast.makeText(
                requireContext(),
                R.string.debug_invalidate_access_token_unavailable,
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        when (val outcome = authDebugActions.invalidateAccessTokenServer(uuid)) {
            is Outcome.Success -> {
                Toast.makeText(
                    requireContext(),
                    R.string.debug_invalidate_access_token_server_done,
                    Toast.LENGTH_SHORT,
                ).show()
            }

            is Outcome.Failure -> {
                when (outcome.error) {
                    is AuthDebugActions.Error.AccountNotFound,
                    is AuthDebugActions.Error.NoOAuthState,
                    -> {
                        Toast.makeText(
                            requireContext(),
                            R.string.debug_invalidate_access_token_unavailable,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }

                    is AuthDebugActions.Error.CannotModifyAccessToken -> {
                        Toast.makeText(
                            requireContext(),
                            R.string.debug_invalidate_access_token_cannot_modify,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }

                    is AuthDebugActions.Error.AlreadyModified -> {
                        Toast.makeText(
                            requireContext(),
                            R.string.debug_invalidate_access_token_already_modified,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        }
    }

    private fun onDebugInvalidateAccessTokenLocal() {
        val accountId = account?.id
        if (!BuildConfig.DEBUG || accountId == null) {
            Toast.makeText(
                requireContext(),
                R.string.debug_invalidate_access_token_unavailable,
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        when (val outcome = authDebugActions.invalidateAccessTokenLocal(accountId)) {
            is Outcome.Success -> {
                Toast.makeText(
                    requireContext(),
                    R.string.debug_invalidate_access_token_local_done,
                    Toast.LENGTH_SHORT,
                ).show()
            }

            is Outcome.Failure -> {
                when (outcome.error) {
                    is AuthDebugActions.Error.AccountNotFound,
                    is AuthDebugActions.Error.NoOAuthState,
                    is AuthDebugActions.Error.CannotModifyAccessToken,
                    is AuthDebugActions.Error.AlreadyModified,
                    -> {
                        Toast.makeText(
                            requireContext(),
                            R.string.debug_invalidate_access_token_unavailable,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        }
    }

    private fun onDebugForceAuthFailure() {
        val accountId = account?.id
        if (!BuildConfig.DEBUG || accountId == null) {
            Toast.makeText(requireContext(), R.string.debug_force_auth_failure_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        when (val outcome = authDebugActions.forceAuthFailure(accountId)) {
            is Outcome.Success -> {
                Toast.makeText(requireContext(), R.string.debug_force_auth_failure_done, Toast.LENGTH_SHORT).show()
            }

            is Outcome.Failure -> {
                when (outcome.error) {
                    is AuthDebugActions.Error.AccountNotFound -> Toast.makeText(
                        requireContext(),
                        R.string.debug_force_auth_failure_unavailable,
                        Toast.LENGTH_SHORT,
                    ).show()

                    is AuthDebugActions.Error.NoOAuthState -> {
                        // Clearing is already the desired state; still report done so user knows it's in effect
                        Toast.makeText(
                            requireContext(),
                            R.string.debug_force_auth_failure_done,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }

                    is AuthDebugActions.Error.CannotModifyAccessToken,
                    is AuthDebugActions.Error.AlreadyModified,
                    -> {
                        // Not relevant to this action, but keep exhaustive when; show generic unavailable
                        Toast.makeText(
                            requireContext(),
                            R.string.debug_invalidate_access_token_unavailable,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        }
    }

    private fun updateFooterText() {
        // While a remote search is running, the MessagingListener callbacks supply progress messages
        // for the footer. Don't overwrite them here.
        if (remoteSearchFuture != null) return

        val currentFolder = this.currentFolder
        val account = this.account

        val footerText = if (initialMessageListLoad) {
            null
        } else if (localSearch.isManualSearch) {
            getSearchResultsFooterText()
        } else if (currentFolder == null || account == null) {
            null
        } else if (currentFolder.loading || isLoadingMoreMessagesRequested || isWaitingForLoadedMessages) {
            getString(R.string.status_loading_more)
        } else if (!currentFolder.moreMessages || !isRemoteFolder) {
            null
        } else if (hasLoadingMessagesFailed) {
            val reason = loadingMessagesFailureReason
            if (reason.isNullOrBlank()) {
                getString(R.string.message_list_load_more_messages_failed)
            } else {
                getString(R.string.message_list_load_more_messages_failed_reason, reason)
            }
        } else {
            // More messages are loaded when the user gets here. Tapping is only needed if that wasn't possible.
            getString(R.string.message_list_load_more_messages_action)
        }

        updateFooterText(footerText)
    }

    private fun getSearchResultsFooterText(): String? {
        val extraSearchResults = this.extraSearchResults
        return if (hasRemoteSearchFailed) {
            getString(R.string.remote_search_error)
        } else if (remoteSearchNotLoadedCount > 0) {
            resources.getQuantityString(
                R.plurals.remote_search_results_limited,
                remoteSearchNotLoadedCount,
                remoteSearchNotLoadedCount,
            )
        } else if (!extraSearchResults.isNullOrEmpty()) {
            // A remote search found more results than were downloaded. Offer to load more.
            account?.let { getString(R.string.load_more_messages_fmt, it.remoteSearchNumResults) }
        } else if (adapter.viewItems.any { it is MessageListViewItem.Message }) {
            null
        } else if (isRemoteSearch) {
            // A remote search completed without results; say so instead of showing a generic message.
            getString(R.string.remote_search_no_results)
        } else if (isRemoteSearchAllowed) {
            getString(R.string.search_no_results_search_server_prompt)
        } else {
            getString(R.string.search_no_results)
        }
    }

    override fun updateFooterText(text: String?) {
        val currentItems = adapter
            .viewItems
            .filter { it !is MessageListViewItem.Footer }
            .toMutableList()

        if (!text.isNullOrEmpty()) {
            currentItems.add(MessageListViewItem.Footer(text))
        }

        adapter.viewItems = currentItems
    }

    private fun selectAll() {
        if (adapter.viewItems.isEmpty()) {
            // Nothing to do if there are no messages
            return
        }

        adapter.selectAll()

        if (actionMode == null) {
            startAndPrepareActionMode()
        }

        computeBatchDirection()
        updateActionMode()
    }

    private fun toggleMessageSelect(messageListItem: MessageListItem) {
        adapter.toggleSelection(messageListItem)
        updateAfterSelectionChange()
    }

    private fun selectMessage(messageListItem: MessageListItem) {
        adapter.selectMessage(messageListItem)
        updateAfterSelectionChange()
    }

    private fun deselectMessage(messageListItem: MessageListItem) {
        adapter.deselectMessage(messageListItem)
        updateAfterSelectionChange()
    }

    private fun isMessageSelected(messageListItem: MessageListItem): Boolean {
        return adapter.isSelected(messageListItem)
    }

    private fun updateAfterSelectionChange() {
        if (adapter.selectedCount == 0) {
            actionMode?.finish()
            actionMode = null
            return
        }

        if (actionMode == null) {
            startAndPrepareActionMode()
        }

        computeBatchDirection()
        updateActionMode()
    }

    override fun onToggleMessageSelection(item: MessageListItem) {
        toggleMessageSelect(item)
    }

    override fun onToggleMessageFlag(item: MessageListItem) {
        setFlag(item, Flag.FLAGGED, !item.isStarred)
    }

    private fun updateActionMode() {
        val actionMode = actionMode ?: error("actionMode == null")
        actionMode.title = getString(MessageListApiR.string.actionbar_selected, adapter.selectedCount)
        actionModeCallback.showSelectAll(!adapter.isAllSelected)

        actionMode.invalidate()
    }

    private fun computeBatchDirection() {
        val selectedMessages = adapter.selectedMessages
        val notAllRead = !selectedMessages.all { it.isRead }
        val notAllStarred = !selectedMessages.all { it.isStarred }

        actionModeCallback.showMarkAsRead(notAllRead)
        actionModeCallback.showFlag(notAllStarred)
    }

    private fun setFlag(messageListItem: MessageListItem, flag: Flag, newState: Boolean) {
        val account = messageListItem.account
        if (showingThreadedList && messageListItem.threadCount > 1) {
            val threadRootId = messageListItem.threadRoot
            messagingController.setFlagForThreads(account.id, listOf(threadRootId), flag, newState)
        } else {
            val messageId = messageListItem.databaseId
            messagingController.setFlag(account.id, listOf(messageId), flag, newState)
        }

        computeBatchDirection()
    }

    private fun setFlagForSelected(flag: Flag, newState: Boolean) {
        if (adapter.selected.isEmpty()) return

        val messageMap = mutableMapOf<LegacyAccount, MutableList<Long>>()
        val threadMap = mutableMapOf<LegacyAccount, MutableList<Long>>()
        val accounts = mutableSetOf<LegacyAccount>()

        for (messageListItem in adapter.selectedMessages) {
            val account = messageListItem.account
            accounts.add(account)

            if (showingThreadedList && messageListItem.threadCount > 1) {
                val threadRootIdList = threadMap.getOrPut(account) { mutableListOf() }
                threadRootIdList.add(messageListItem.threadRoot)
            } else {
                val messageIdList = messageMap.getOrPut(account) { mutableListOf() }
                messageIdList.add(messageListItem.databaseId)
            }
        }

        for (account in accounts) {
            messageMap[account]?.let { messageIds ->
                messagingController.setFlag(account.id, messageIds, flag, newState)
            }

            threadMap[account]?.let { threadRootIds ->
                messagingController.setFlagForThreads(account.id, threadRootIds, flag, newState)
            }
        }

        computeBatchDirection()
    }

    private fun onMove(message: MessageReference) {
        onMove(listOf(message))
    }

    private fun onMove(messages: List<MessageReference>) {
        if (!checkCopyOrMovePossible(messages, FolderOperation.MOVE)) return

        val folderId = when {
            isThreadDisplay -> messages.first().folderId
            isSingleFolderMode -> currentFolder!!.databaseId
            else -> null
        }

        displayFolderChoice(
            operation = FolderOperation.MOVE,
            sourceFolderId = folderId,
            accountId = messages.first().accountId,
            messages = messages,
        )
    }

    private fun onCopy(message: MessageReference) {
        onCopy(listOf(message))
    }

    private fun onCopy(messages: List<MessageReference>) {
        if (!checkCopyOrMovePossible(messages, FolderOperation.COPY)) return

        val folderId = when {
            isThreadDisplay -> messages.first().folderId
            isSingleFolderMode -> currentFolder!!.databaseId
            else -> null
        }

        displayFolderChoice(
            operation = FolderOperation.COPY,
            sourceFolderId = folderId,
            accountId = messages.first().accountId,
            messages = messages,
        )
    }

    private fun displayFolderChoice(
        operation: FolderOperation,
        sourceFolderId: Long?,
        accountId: AccountId,
        messages: List<MessageReference>,
    ) {
        // Remember the selected messages so they are available in the registerForActivityResult() callbacks
        activeMessages = messages

        val input = ChooseFolderResultContract.Input(
            accountId = accountId,
            currentFolderId = sourceFolderId,
        )
        when (operation) {
            FolderOperation.COPY -> chooseFolderForCopyLauncher.launch(input)
            FolderOperation.MOVE -> chooseFolderForMoveLauncher.launch(input)
        }
    }

    private fun handleChooseFolderResult(
        result: ChooseFolderResultContract.Result?,
        action: (Long, List<MessageReference>) -> Unit,
    ) {
        if (result == null) return

        val destinationFolderId = result.folderId
        val messages = activeMessages!!

        if (destinationFolderId != -1L) {
            activeMessages = null

            setLastSelectedFolder(messages, destinationFolderId)

            action(destinationFolderId, messages)
        }
    }

    private fun setLastSelectedFolder(messages: List<MessageReference>, folderId: Long) {
        val firstMessage = messages.firstOrNull() ?: return
        lastSelectedFolderStore.set(firstMessage.accountId, folderId)
    }

    private fun onArchive(message: MessageReference) {
        onArchive(listOf(message))
    }

    private fun onArchive(messages: List<MessageReference>) {
        if (!checkCopyOrMovePossible(messages, FolderOperation.MOVE)) return

        val threaded = showingThreadedList
        removeMessagesWithUndo(messages, MessageRemoval.ARCHIVE) {
            if (threaded) {
                messagingController.archiveThreads(messages)
            } else {
                messagingController.archiveMessages(messages)
            }
        }
    }

    private fun groupMessagesByAccount(
        messages: List<MessageReference>,
    ): Map<LegacyAccount, List<MessageReference>> {
        return messages.groupBy { accountManager.findById(it.accountId)!! }
    }

    private fun onSpam(messages: List<MessageReference>, count: Int) {
        if (interactionSettings.isConfirmSpam) {
            // remember the message selection for #onCreateDialog(int)
            activeMessages = messages
            showDialog(R.id.dialog_confirm_spam, count)
        } else {
            onSpamConfirmed(messages)
        }
    }

    private fun onSpamConfirmed(messages: List<MessageReference>) {
        // Only messages that are really moved to the spam folder are removed from the message list.
        val spamMessages = messages.filter { message ->
            accountManager.findById(message.accountId)?.spamFolderId
                .let { spamFolderId -> spamFolderId != null && message.folderId != spamFolderId }
        }

        removeMessagesWithUndo(spamMessages, MessageRemoval.SPAM) {
            for ((account, messagesInAccount) in groupMessagesByAccount(spamMessages)) {
                account.spamFolderId?.let { spamFolderId ->
                    move(messagesInAccount, spamFolderId)
                }
            }
        }
    }

    private fun checkCopyOrMovePossible(messages: List<MessageReference>, operation: FolderOperation): Boolean {
        if (messages.isEmpty()) return false

        val account = accountManager.findById(messages.first().accountId) ?: return false
        if (operation == FolderOperation.MOVE &&
            !messagingController.isMoveCapable(account.id) ||
            operation == FolderOperation.COPY &&
            !messagingController.isCopyCapable(account.id)
        ) {
            return false
        }

        for (message in messages) {
            if (operation == FolderOperation.MOVE &&
                !messagingController.isMoveCapable(message) ||
                operation == FolderOperation.COPY &&
                !messagingController.isCopyCapable(message)
            ) {
                val toast = Toast.makeText(
                    activity,
                    R.string.move_copy_cannot_copy_unsynced_message,
                    Toast.LENGTH_LONG,
                )
                toast.show()
                return false
            }
        }

        return true
    }

    private fun copy(messages: List<MessageReference>, folderId: Long) {
        copyOrMove(messages, folderId, FolderOperation.COPY)
    }

    private fun move(messages: List<MessageReference>, folderId: Long) {
        copyOrMove(messages, folderId, FolderOperation.MOVE)
    }

    /**
     * Move messages to another folder, but only after the user didn't undo the move.
     */
    private fun moveWithUndo(messages: List<MessageReference>, folderId: Long) {
        val messagesToMove = messages.filterNot { it.folderId == folderId }
        if (!checkCopyOrMovePossible(messagesToMove, FolderOperation.MOVE)) return

        removeMessagesWithUndo(messagesToMove, MessageRemoval.MOVE) { move(messagesToMove, folderId) }
    }

    private fun copyOrMove(messages: List<MessageReference>, destinationFolderId: Long, operation: FolderOperation) {
        if (!checkCopyOrMovePossible(messages, operation)) return

        val folderMap = messages.asSequence()
            .filterNot { it.folderId == destinationFolderId }
            .groupBy { it.folderId }

        for ((folderId, messagesInFolder) in folderMap) {
            val account = accountManager.findById(messagesInFolder.first().accountId)
            if (account == null) {
                logger.debug(logTag) {
                    "Account for message ${messagesInFolder.first()} not found, skipping copy/move operation"
                }
                continue
            }

            when (operation) {
                FolderOperation.MOVE if showingThreadedList -> {
                    messagingController.moveMessagesInThread(
                        account.id,
                        folderId,
                        messagesInFolder,
                        destinationFolderId,
                    )
                }

                FolderOperation.MOVE -> {
                    messagingController.moveMessages(
                        account.id,
                        folderId,
                        messagesInFolder,
                        destinationFolderId,
                    )
                }

                FolderOperation.COPY if showingThreadedList -> {
                    messagingController.copyMessagesInThread(
                        account.id,
                        folderId,
                        messagesInFolder,
                        destinationFolderId,
                    )
                }

                FolderOperation.COPY -> {
                    messagingController.copyMessages(
                        account.id,
                        folderId,
                        messagesInFolder,
                        destinationFolderId,
                    )
                }
            }
        }
    }

    private fun onMoveToDraftsFolder(messages: List<MessageReference>) {
        account?.id?.let { messagingController.moveToDraftsFolder(it, currentFolder!!.databaseId, messages) }
        activeMessages = null
    }

    override fun doPositiveClick(dialogId: Int) {
        when (dialogId) {
            R.id.dialog_confirm_spam -> {
                onSpamConfirmed(activeMessages!!)
                activeMessages = null
            }

            R.id.dialog_confirm_delete -> {
                onDeleteConfirmed(activeMessages!!)
                activeMessage = null
                adapter.activeMessage = null
            }

            R.id.dialog_confirm_mark_all_as_read -> {
                markAllAsRead()
            }

            R.id.dialog_confirm_empty_spam -> {
                account?.id?.let { messagingController.emptySpam(it) }
            }

            R.id.dialog_confirm_empty_trash -> {
                account?.id?.let { messagingController.emptyTrash(it) }
            }
        }
    }

    override fun doNegativeClick(dialogId: Int) {
        if (dialogId == R.id.dialog_confirm_spam || dialogId == R.id.dialog_confirm_delete) {
            val activeMessages = this.activeMessages ?: return
            if (activeMessages.size == 1) {
                // List item might have been swiped and is still showing the "swipe action background"
                resetSwipedView(activeMessages.first())
            }

            this.activeMessages = null
        }
    }

    private fun resetSwipedView(messageReference: MessageReference) {
        val recyclerView = this.recyclerView ?: return
        val itemTouchHelper = this.itemTouchHelper ?: return

        adapter.getItem(messageReference)?.let { messageListItem ->
            recyclerView.findViewHolderForItemId(messageListItem.uniqueId)?.let { viewHolder ->
                itemTouchHelper.stopSwipe(viewHolder)
                notifyItemChanged(messageListItem)
            }
        }
    }

    override fun dialogCancelled(dialogId: Int) {
        doNegativeClick(dialogId)
    }

    private fun checkMail() {
        if (isSingleAccountMode && isSingleFolderMode) {
            val folderId = currentFolder!!.databaseId
            account?.id?.let { messagingController.synchronizeMailbox(it, folderId, false, activityListener) }
            account?.id?.let { messagingController.sendPendingMessages(it, activityListener) }
        } else if (allAccounts) {
            messagingController.checkMail(null, true, true, false, activityListener)
        } else {
            for (accountId in accountIds) {
                val account = accountManager.findById(accountId)
                account?.id?.let { messagingController.checkMail(it, true, true, false, activityListener) }
            }
        }
    }

    override fun onStop() {
        // If we represent a remote search, then kill that before going back.
        stopRemoteSearch(canContinue = !requireActivity().isFinishing)

        super.onStop()
    }

    /**
     * Stop a remote search that is running.
     *
     * @param canContinue Whether the search continues where it was stopped when the user returns to the search
     *   results, e.g. from looking at a folder or a message. The folder it was working on is searched again.
     */
    private fun stopRemoteSearch(canContinue: Boolean) {
        if (!isRemoteSearch || remoteSearchFuture == null) return

        if (canContinue && !isAttachmentSearch) {
            // A search of the attachments is not continued automatically.
            stoppedRemoteSearch = StoppedRemoteSearch(
                searchMessageContents = isSearchingMessageContents,
                searchedFolders = remoteSearchSearchedFolders.toSet(),
            )
        }

        // The stopped search may still report something. Only what is said here counts from now on.
        remoteSearchListener = null

        // An aborted search of the message contents can be started again.
        isSearchingMessageContents = false
        isRemoteSearchStopped = true
        try {
            logger.info(logTag) { "Remote search in progress, attempting to abort..." }

            // Canceling the future stops any message fetches in progress.
            val cancelSuccess = remoteSearchFuture!!.cancel(true) // mayInterruptIfRunning = true
            if (!cancelSuccess) {
                logger.error(logTag) { "Could not cancel remote search future." }
            }

            // Send a remoteSearchFinished() message for good measure.
            activityListener.remoteSearchFinished(
                currentFolder?.databaseId ?: NO_FOLDER_ID,
                0,
                account?.remoteSearchNumResults ?: 0,
                null,
            )
        } catch (e: Exception) {
            // Since the user is going back, log and squash any exceptions.
            logger.error(logTag, e) { "Could not abort remote search before going back" }
        }
    }

    fun openMessage(messageReference: MessageReference) {
        commitPendingMessageAction()
        fragmentListener.openMessage(messageReference)
    }

    override fun onReverseSort() {
        changeSort(sortType)
    }

    private val selectedMessage: MessageReference?
        get() = selectedMessageListItem?.messageReference

    private val selectedMessageListItem: MessageListItem?
        get() {
            val recyclerView = recyclerView ?: return null
            val focusedView = recyclerView.focusedChild ?: return null
            val viewHolder = recyclerView.findContainingViewHolder(focusedView) as? MessageViewHolder ?: return null
            return adapter.getItemById(viewHolder.uniqueId)
        }

    private val selectedMessages: List<MessageReference>
        get() = adapter.selectedMessages.map { it.messageReference }

    private val selectedMessagesCount
        get() = adapter.selectedCount

    override fun onDelete() {
        selectedMessage?.let { message ->
            onDelete(listOf(message), selectedMessagesCount)
        }
    }

    override fun toggleMessageSelect() {
        selectedMessageListItem?.let { messageListItem ->
            toggleMessageSelect(messageListItem)
        }
    }

    override fun onToggleFlagged() {
        selectedMessageListItem?.let { messageListItem ->
            setFlag(messageListItem, Flag.FLAGGED, !messageListItem.isStarred)
        }
    }

    override fun onToggleRead() {
        selectedMessageListItem?.let { messageListItem ->
            markReadWithUndo(messageListItem, !messageListItem.isRead)
        }
    }

    override fun onMove() {
        selectedMessage?.let { message ->
            onMove(message)
        }
    }

    override fun onArchive() {
        selectedMessage?.let { message ->
            onArchive(message)
        }
    }

    override fun onCopy() {
        selectedMessage?.let { message ->
            onCopy(message)
        }
    }

    val isOutbox: Boolean
        get() = isSpecialFolder(account?.id?.let(outboxFolderManager::getOutboxFolderIdSync))

    private val isInbox: Boolean
        get() = isSpecialFolder(account?.inboxFolderId)

    private val isArchiveFolder: Boolean
        get() = isSpecialFolder(account?.archiveFolderId)

    private val isSpamFolder: Boolean
        get() = isSpecialFolder(account?.spamFolderId)

    private fun isSpecialFolder(specialFolderId: Long?): Boolean {
        val folderId = specialFolderId ?: return false
        val currentFolder = currentFolder ?: return false
        return currentFolder.databaseId == folderId
    }

    private val isRemoteFolder: Boolean
        get() {
            if (localSearch.isManualSearch || isOutbox) return false

            val accountId = account?.id
            return if (accountId == null || !messagingController.isMoveCapable(accountId)) {
                // For POP3 accounts only the Inbox is a remote folder.
                isInbox
            } else {
                true
            }
        }

    private val isManualSearch: Boolean
        get() = localSearch.isManualSearch

    private fun shouldShowExpungeAction(): Boolean {
        val account = this.account ?: return false
        return account.expungePolicy == Expunge.EXPUNGE_MANUALLY && messagingController.supportsExpunge(account.id)
    }

    private fun onRemoteSearch() {
        // Remote search is useless without the network.
        if (hasConnectivity == true) {
            onRemoteSearchRequested()
        } else {
            Toast.makeText(activity, getText(R.string.remote_search_unavailable_no_network), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * The query the user has entered for a manual search.
     */
    private val manualSearchQuery: String?
        get() = activity?.intent?.getStringExtra(SearchManager.QUERY)?.trim()?.takeIf { it.isNotEmpty() }
            ?: localSearch.remoteSearchArguments

    private val hasAccountSupportingRemoteSearch: Boolean by lazy { messagingController.supportsRemoteSearch() }

    private val isRemoteSearchSupported: Boolean
        get() = isManualSearch && hasAccountSupportingRemoteSearch

    private val isRemoteSearchAllowed: Boolean
        get() = !isRemoteSearch && isRemoteSearchSupported

    /**
     * The message contents can only be searched when the text of the search field is looked for in the headers.
     * Otherwise the contents are searched already.
     */
    private val isMessageContentsSearchAllowed: Boolean
        get() = searchFilters.textScope == SearchFilters.TextScope.HEADERS

    /**
     * A remote search that has finished or was aborted can be started again, e.g. via pull-to-refresh.
     */
    private val isRemoteSearchRestartAllowed: Boolean
        get() = isRemoteSearch && remoteSearchFuture == null && isRemoteSearchSupported

    fun onSearchRequested(query: String): Boolean {
        val folderId = currentFolder?.databaseId
        return fragmentListener.startSearch(query, account?.id, folderId)
    }

    /**
     * Display the messages of the message list.
     *
     * @param fromLoader `true` if the messages come from the message list loader, `false` if the list is only
     *   redrawn, e.g. because messages were removed from it.
     */
    private fun setMessageList(messageListInfo: MessageListInfo, fromLoader: Boolean = true) {
        lastMessageListInfo = messageListInfo

        if (fromLoader) hiddenUntilReload = emptySet()

        val messageListItems = messageListInfo.messageListItems
        if (isThreadDisplay && messageListItems.isEmpty()) {
            goBack()
            return
        }

        swipeRefreshLayout?.let { swipeRefreshLayout ->
            swipeRefreshLayout.isRefreshing = false
            swipeRefreshLayout.isEnabled = isPullToRefreshAllowed
        }

        if (isThreadDisplay) {
            if (messageListItems.isNotEmpty()) {
                val strippedSubject = messageListItems.first().subject?.let { Utility.stripSubject(it) }
                threadTitle = if (strippedSubject.isNullOrEmpty()) {
                    getString(R.string.general_no_subject)
                } else {
                    strippedSubject
                }
                updateTitle()
            } else {
                // TODO: empty thread view -> return to full message list
            }
        }

        adapter.viewItems = buildList {
            if (featureFlagProvider.provide(GeneratedFeatureFlagKey.DISPLAY_IN_APP_NOTIFICATIONS).isEnabled()) {
                add(MessageListViewItem.InAppNotificationBannerList)
            }
            searchStatus?.takeUnless { isSearchStatusInBottomPanel() }?.let { add(searchStatusItem(it)) }
            addAll(visibleMessageListItems(messageListItems).map { MessageListViewItem.Message(it) })
            // Keep the current footer (e.g. remote search progress) across list rebuilds.
            // updateFooterText() below recomputes it when no remote search is running.
            adapter.viewItems.filterIsInstance<MessageListViewItem.Footer>().firstOrNull()?.let { add(it) }
        }

        rememberedSelected?.let {
            rememberedSelected = null
            adapter.restoreSelected(it)
        }

        updateReachArea()

        // The messages that are visible are only known after the list has been laid out.
        recyclerView?.doOnPreDraw {
            downloadMissingPreviewsOfVisibleMessages()
        }

        messageListItems
            .map { it.account }
            .toSet()
            .forEach { account -> messagingController.checkAuthenticationProblem(account.id) }

        resetActionMode()
        computeBatchDirection()

        invalidateMenu()

        initialMessageListLoad = false

        currentFolder?.let { currentFolder ->
            currentFolder.moreMessages = messageListInfo.hasMoreMessages
        }
        updateFooterText()

        startAutomaticRemoteSearch()
        if (isWaitingForLoadedMessages) {
            handler.removeCallbacks(loadedMessagesDisplayedRunnable)
            handler.postDelayed(loadedMessagesDisplayedRunnable, LOADED_MESSAGES_SETTLING_TIME)
        } else {
            loadMoreMessagesIfScreenIsNotFilled()
        }
    }

    /**
     * Only the messages that match the filters the user has set are displayed.
     */
    /**
     * The messages that are shown: those that match the search filters and are not hidden while an action on them can
     * still be undone.
     */
    private fun visibleMessageListItems(messageListItems: List<MessageListItem>): List<MessageListItem> {
        return displayedMessageListItems(messageListItems).filterNot {
            pendingMessageAction.isPending(it.messageReference) || it.messageReference in hiddenUntilReload
        }
    }

    private fun displayedMessageListItems(messageListItems: List<MessageListItem>): List<MessageListItem> {
        if (!isManualSearch || searchFilters.isEmpty) return messageListItems

        val nowMillis = System.currentTimeMillis()
        val timeZone = TimeZone.getDefault()
        return messageListItems.filter { searchFilters.matches(it, nowMillis, timeZone) }
    }

    /**
     * Whether older messages of the displayed folder can be loaded from the server now.
     */
    private val isLoadingMoreMessagesPossible: Boolean
        get() {
            val currentFolder = currentFolder ?: return false
            return isSingleAccountMode &&
                isSingleFolderMode &&
                !isThreadDisplay &&
                isRemoteFolder &&
                currentFolder.moreMessages &&
                !currentFolder.loading &&
                !isLoadingMoreMessagesRequested &&
                !isWaitingForLoadedMessages
        }

    /**
     * The messages of a folder are only loaded as far as the user looks at them: enough to fill the screen when the
     * folder is opened, and more when the user scrolls towards the end of the messages that are on the device.
     *
     * After a failure the user has to ask for it, so a server that can't be reached isn't tried over and over.
     */
    private val isLoadingMoreMessagesWanted: Boolean
        get() = isResumed && !initialMessageListLoad && !hasLoadingMessagesFailed && isLoadingMoreMessagesPossible

    private fun loadMoreMessagesIfEndIsNear() {
        if (!isLoadingMoreMessagesWanted || !isOldestMessageDisplayedLast) return
        val layoutManager = recyclerView?.layoutManager as? LinearLayoutManager ?: return

        val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
        if (lastVisiblePosition >= adapter.itemCount - 1 - LOAD_MORE_MESSAGES_DISTANCE) {
            loadMoreMessages()
        }
    }

    /**
     * Older messages are added to the end of the list only when it is sorted by date, newest first. With any other
     * sort order reaching the end of the list doesn't mean the user is looking for older messages.
     */
    private val isOldestMessageDisplayedLast: Boolean
        get() = (sortType == SortType.SORT_DATE || sortType == SortType.SORT_ARRIVAL) && !sortAscending

    private fun loadMoreMessagesIfScreenIsNotFilled() {
        val recyclerView = recyclerView ?: return

        // Whether the list can be scrolled is only known once it has been laid out with its current messages.
        recyclerView.doOnPreDraw {
            if (isLoadingMoreMessagesWanted && !recyclerView.canScrollVertically(1)) {
                loadMoreMessages()
            }
        }
        recyclerView.invalidate()
    }

    /**
     * Downloads the text of the messages the list displays without a preview, because only their headers are on the
     * device. The list updates itself once the text of a message has been saved.
     */
    private fun downloadMissingPreviewsOfVisibleMessages() {
        val recyclerView = recyclerView ?: return
        if (!connectivityManager.isNetworkAvailable()) return

        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
        val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
        if (firstVisiblePosition == RecyclerView.NO_POSITION || lastVisiblePosition == RecyclerView.NO_POSITION) return

        val viewItems = adapter.viewItems
        val messages = (firstVisiblePosition..lastVisiblePosition)
            .mapNotNull { position -> viewItems.getOrNull(position) as? MessageListViewItem.Message }
            .map { it.item }
            .filter { it.previewText.isEmpty() && !it.isMessageEncrypted }
            .filter { it.messageReference !in requestedPreviewDownloads }

        requestedPreviewDownloads += messages.map { it.messageReference }

        messages.groupBy { it.account.id to it.folderId }.forEach { (accountAndFolder, items) ->
            val (accountId, folderId) = accountAndFolder
            messagingController.downloadMissingPreviews(accountId, folderId, items.map { it.messageUid })
        }
    }

    private fun loadMoreMessages() {
        val account = account ?: return
        val currentFolder = currentFolder ?: return
        if (isLoadingMoreMessagesRequested || isWaitingForLoadedMessages) return

        isLoadingMoreMessagesRequested = true
        hasLoadingMessagesFailed = false
        updateFooterText()

        messageCountBeforeLoading = displayedMessageCount
        val onFinished: () -> Unit = {
            handler.post {
                isLoadingMoreMessagesRequested = false

                // The message list is updated once for every message that was loaded, and it may still be catching
                // up. Its end is not the end of the loaded messages until it has come to rest.
                isWaitingForLoadedMessages = true
                handler.postDelayed(loadedMessagesDisplayedRunnable, LOADED_MESSAGES_SETTLING_TIME)
            }
        }
        // A folder that only has a few messages on the device, e.g. search results, hasn't been looked at yet.
        if (messageCountBeforeLoading >= messageCountFillingScreen) {
            messagingController.loadNextMessages(account.id, currentFolder.databaseId, onFinished)
        } else {
            messagingController.loadFirstMessages(
                account.id,
                currentFolder.databaseId,
                messageCountFillingScreen,
                onFinished,
            )
        }
    }

    private val displayedMessageCount: Int
        get() = adapter.viewItems.count { it is MessageListViewItem.Message }

    private fun onLoadedMessagesDisplayed() {
        isWaitingForLoadedMessages = false
        if (!isAdded) return

        updateFooterText()

        // Continue if this wasn't enough to fill the screen. Only when it has brought new messages though. Otherwise
        // this would go on forever.
        if (displayedMessageCount > messageCountBeforeLoading) {
            loadMoreMessagesIfScreenIsNotFilled()
        }
    }

    /**
     * How many messages it takes to fill the message list, plus a few so the list can be scrolled.
     */
    private val messageCountFillingScreen: Int
        get() {
            val listHeight = recyclerView?.height?.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
            val smallestRowHeight = resources.getDimensionPixelSize(R.dimen.messageListSmallestRowHeight)
            return listHeight / smallestRowHeight + ADDITIONAL_FIRST_MESSAGES
        }

    /**
     * Continue with a server search as soon as the local search results are displayed.
     */
    private fun startAutomaticRemoteSearch() {
        if (!isResumed || isAutomaticRemoteSearchScheduled || !isRemoteSearchAllowed) return
        val recyclerView = recyclerView ?: return

        isAutomaticRemoteSearchScheduled = true
        if (connectivityManager.isNetworkAvailable()) {
            // Don't start the search from within the list update that triggered it.
            recyclerView.post(automaticRemoteSearchRunnable)
        } else {
            remoteSearchProblem = getString(R.string.remote_search_unavailable)
            remoteSearchProblemReason = getString(R.string.remote_search_unavailable_no_network)
            showSearchOutcome()
        }
    }

    private fun resetActionMode() {
        if (!isResumed) return

        if (!isActive || adapter.selected.isEmpty()) {
            actionMode?.finish()
            actionMode = null
            return
        }

        if (actionMode == null) {
            startAndPrepareActionMode()
        }

        updateActionMode()
    }

    private fun startAndPrepareActionMode() {
        actionMode = fragmentListener.startSupportActionMode(actionModeCallback)
        actionMode?.invalidate()
    }

    override fun finishActionMode() {
        actionMode?.finish()
    }

    override fun remoteSearchFinished() {
        remoteSearchFuture = null
        runningSearchAccounts.clear()
    }

    override fun setActiveMessage(messageReference: MessageReference?) {
        activeMessage = messageReference

        rememberSortOverride(messageReference)

        // Reload message list with modified query that always includes the active message
        if (isAdded) {
            loadMessageList()
        }

        // Redraw list immediately
        if (::adapter.isInitialized) {
            adapter.activeMessage = activeMessage

            if (messageReference != null) {
                scrollToMessage(messageReference)
            }
        }
    }

    override fun onFullyActive() {
        maybeShowFloatingActionButton()
    }

    private fun maybeShowFloatingActionButton() {
        floatingActionButton?.isVisible = true
    }

    private fun maybeHideFloatingActionButton() {
        floatingActionButton?.isGone = true
    }

    // For the last N displayed messages we remember the original 'read' and 'starred' state of the messages. We pass
    // this information to MessageListLoader so messages can be sorted according to these remembered values and not the
    // current state. This way messages, that are marked as read/unread or starred/not starred while being displayed,
    // won't immediately change position in the message list if the list is sorted by these fields.
    // The main benefit is that the swipe to next/previous message feature will work in a less surprising way.
    private fun rememberSortOverride(messageReference: MessageReference?) {
        val messageSortOverrides = legacyViewModel.messageSortOverrides

        if (messageReference == null) {
            messageSortOverrides.clear()
            return
        }

        if (sortType != SortType.SORT_UNREAD && sortType != SortType.SORT_FLAGGED) return

        val messageListItem = adapter.getItem(messageReference) ?: return

        val existingEntry = messageSortOverrides.firstOrNull { it.first == messageReference }
        if (existingEntry != null) {
            messageSortOverrides.remove(existingEntry)
            messageSortOverrides.addLast(existingEntry)
        } else {
            messageSortOverrides.addLast(
                messageReference to MessageSortOverride(messageListItem.isRead, messageListItem.isStarred),
            )
            if (messageSortOverrides.size > MAXIMUM_MESSAGE_SORT_OVERRIDES) {
                messageSortOverrides.removeFirst()
            }
        }
    }

    private fun scrollToMessage(messageReference: MessageReference) {
        val recyclerView = recyclerView ?: return
        val messageListItem = adapter.getItem(messageReference) ?: return
        val position = adapter.getPosition(messageListItem) ?: return

        val scroller = object : LinearSmoothScroller(recyclerView.context) {
            override fun calculateDyToMakeVisible(view: View, snapPreference: Int): Int {
                // The reach area is part of the list's padding. The default would treat it as space to scroll the
                // message into, and so pull the list down into the reach area although the message is on screen.
                return dyToMakeVisible(
                    itemTop = view.top,
                    itemBottom = view.bottom,
                    viewportTop = bannerTopPadding,
                    viewportBottom = recyclerView.height - recyclerView.paddingBottom,
                )
            }
        }
        scroller.targetPosition = position
        recyclerView.layoutManager?.startSmoothScroll(scroller)
    }

    /**
     * Bring the message into view and highlight it, so the user can see the message they came from.
     */
    override fun highlightMessage(messageReference: MessageReference) {
        scrollToMessage(messageReference)
        adapter.highlightMessage(messageReference)
    }

    private val isMarkAllAsReadSupported: Boolean
        get() = isSingleAccountMode && isSingleFolderMode && !isOutbox

    private fun confirmMarkAllAsRead() {
        if (interactionSettings.isConfirmMarkAllRead) {
            showDialog(R.id.dialog_confirm_mark_all_as_read)
        } else {
            markAllAsRead()
        }
    }

    private fun markAllAsRead() {
        if (isMarkAllAsReadSupported) {
            account?.id?.let { messagingController.markAllMessagesRead(it, currentFolder!!.databaseId) }
        }
    }

    private fun invalidateMenu() {
        activity?.invalidateMenu()
    }

    private val isCheckMailSupported: Boolean
        get() = allAccounts || !isSingleAccountMode || !isSingleFolderMode || isRemoteFolder

    private val isCheckMailAllowed: Boolean
        get() = !isManualSearch && isCheckMailSupported

    private val isPullToRefreshAllowed: Boolean
        get() = isRemoteSearchAllowed || isRemoteSearchRestartAllowed || isCheckMailAllowed

    private var itemSelectedOnSwipeStart = false

    private val swipeListener = object : MessageListSwipeListener {
        override fun onSwipeStarted(item: MessageListItem, action: SwipeAction) {
            swipeRefreshLayout?.isEnabled = false
            itemSelectedOnSwipeStart = isMessageSelected(item)
            if (itemSelectedOnSwipeStart && action != SwipeAction.ToggleSelection) {
                deselectMessage(item)
            }
        }

        override fun onSwipeActionChanged(item: MessageListItem, action: SwipeAction) {
            if (action == SwipeAction.ToggleSelection) {
                if (itemSelectedOnSwipeStart && !isMessageSelected(item)) {
                    selectMessage(item)
                }
            } else if (isMessageSelected(item)) {
                deselectMessage(item)
            }
        }

        override fun onSwipeAction(item: MessageListItem, action: SwipeAction) {
            if (action.removesItem || action == SwipeAction.ToggleSelection) {
                itemSelectedOnSwipeStart = false
            }

            when (action) {
                SwipeAction.None -> Unit
                SwipeAction.ToggleSelection -> {
                    toggleMessageSelect(item)
                }

                SwipeAction.ToggleRead -> {
                    markReadWithUndo(item, !item.isRead)
                }

                SwipeAction.ToggleStar -> {
                    setFlag(item, Flag.FLAGGED, !item.isStarred)
                }

                SwipeAction.ArchiveDisabled ->
                    Snackbar
                        .make(
                            requireNotNull(view),
                            R.string.archiving_not_available_for_this_account,
                            Snackbar.LENGTH_LONG,
                        )
                        .show()

                SwipeAction.ArchiveSetupArchiveFolder -> setupArchiveFolderDialogFragmentFactory.show(
                    accountUuid = item.account.id.toString(),
                    fragmentManager = parentFragmentManager,
                )

                SwipeAction.Archive -> {
                    onArchive(item.messageReference)
                }

                SwipeAction.Delete -> {
                    onDelete(listOf(item.messageReference), item.threadCount)
                }

                SwipeAction.Spam -> {
                    onSpam(listOf(item.messageReference), item.threadCount)
                }

                SwipeAction.Move -> {
                    val messageReference = item.messageReference
                    resetSwipedView(messageReference)
                    onMove(messageReference)
                }
            }
        }

        override fun onSwipeEnded(item: MessageListItem) {
            swipeRefreshLayout?.isEnabled = true
            if (itemSelectedOnSwipeStart && !isMessageSelected(item)) {
                selectMessage(item)
            }
        }
    }

    private fun notifyItemChanged(item: MessageListItem) {
        val position = adapter.getPosition(item) ?: return
        adapter.notifyItemChanged(position)
    }

    private val swipeActionSupportProvider = SwipeActionSupportProvider { item, action ->
        when (action) {
            SwipeAction.None -> false
            SwipeAction.ToggleSelection -> true
            SwipeAction.ToggleRead -> !isOutbox
            SwipeAction.ToggleStar -> !isOutbox
            SwipeAction.Archive, SwipeAction.ArchiveDisabled, SwipeAction.ArchiveSetupArchiveFolder -> {
                !isOutbox && item.folderId != item.account.archiveFolderId
            }

            SwipeAction.Delete -> true
            SwipeAction.Move -> !isOutbox && messagingController.isMoveCapable(item.account.id)
            SwipeAction.Spam -> !isOutbox && item.account.hasSpamFolder() && item.folderId != item.account.spamFolderId
        }
    }

    override fun filterInAppNotificationEvents(notification: InAppNotification): Boolean {
        val accountId = notification.accountId
        return notification !is SentFolderNotFoundNotification &&
            accountId != null &&
            accountId in accountIds
    }

    override fun onNotificationActionClicked(action: NotificationAction) = onNotificationActionClick(action)

    override fun onNotificationActionClick(action: NotificationAction) {
        when (action) {
            is NotificationAction.UpdateIncomingServerSettings ->
                FeatureLauncherActivity.launch(
                    context = requireContext(),
                    target = FeatureLauncherTarget.AccountEditIncomingSettings(action.accountId),
                )

            is NotificationAction.UpdateOutgoingServerSettings ->
                FeatureLauncherActivity.launch(
                    context = requireContext(),
                    target = FeatureLauncherTarget.AccountEditOutgoingSettings(action.accountId),
                )

            is NotificationAction.OpenNotificationCentre ->
                errorNotificationsDialogFragmentFactory.show(fragmentManager = childFragmentManager)

            else -> Unit
        }
    }

    private data class StoppedRemoteSearch(val searchMessageContents: Boolean, val searchedFolders: Set<String>)

    /**
     * Passes on what a server search reports for as long as it is the current search. A search that was stopped or
     * replaced by another one may still report something until it notices.
     */
    private inner class RemoteSearchListener : SimpleMessagingListener() {
        private val listener: MessageListActivityListener?
            get() = activityListener.takeIf { remoteSearchListener === this }

        override fun remoteSearchStarted(folderId: Long) {
            listener?.remoteSearchStarted(folderId)
        }

        override fun remoteSearchCascadeProgress(
            accountUuid: String,
            accountName: String,
            folderName: String,
            folderIndex: Int,
            folderCount: Int,
        ) {
            listener?.remoteSearchCascadeProgress(accountUuid, accountName, folderName, folderIndex, folderCount)
        }

        override fun remoteSearchStep(step: SearchStep) {
            listener?.remoteSearchStep(step)
        }

        override fun remoteSearchDownloadProgress(folderName: String, completed: Int, total: Int) {
            listener?.remoteSearchDownloadProgress(folderName, completed, total)
        }

        override fun remoteSearchMessageDownloaded(folderName: String, subject: String?) {
            listener?.remoteSearchMessageDownloaded(folderName, subject)
        }

        override fun remoteSearchFolderResults(accountUuid: String, folderId: Long, messageServerIds: List<String>) {
            listener?.remoteSearchFolderResults(accountUuid, folderId, messageServerIds)
        }

        override fun remoteSearchFolderSearched(accountUuid: String, folderId: Long) {
            listener?.remoteSearchFolderSearched(accountUuid, folderId)
        }

        override fun remoteSearchAccountFinished(accountUuid: String) {
            listener?.remoteSearchAccountFinished(accountUuid)
        }

        override fun remoteSearchResultsLimited(notLoadedCount: Int) {
            listener?.remoteSearchResultsLimited(notLoadedCount)
        }

        override fun remoteSearchCascadeIncomplete(failedFolderCount: Int, folderCount: Int, reason: String?) {
            listener?.remoteSearchCascadeIncomplete(failedFolderCount, folderCount, reason)
        }

        override fun remoteSearchFailed(folderServerId: String?, err: String?) {
            listener?.remoteSearchFailed(folderServerId, err)
        }

        override fun remoteSearchFinished(
            folderId: Long,
            numResults: Int,
            maxResults: Int,
            extraResults: List<String>?,
        ) {
            listener?.remoteSearchFinished(folderId, numResults, maxResults, extraResults)
        }
    }

    internal inner class MessageListActivityListener : SimpleMessagingListener() {
        private val lock = Any()

        @GuardedBy("lock")
        private var folderCompleted = 0

        @GuardedBy("lock")
        private var folderTotal = 0

        override fun remoteSearchFailed(folderServerId: String?, err: String?) {
            handler.post {
                hasRemoteSearchFailed = true
                activity?.let { activity ->
                    remoteSearchProblem = activity.getString(R.string.remote_search_error)
                    remoteSearchProblemReason = err
                    Toast.makeText(activity, R.string.remote_search_error, Toast.LENGTH_LONG).show()
                }
            }
        }

        override fun remoteSearchCascadeIncomplete(failedFolderCount: Int, folderCount: Int, reason: String?) {
            handler.post {
                if (!isAdded) return@post

                // Displayed by showSearchOutcome() when the remote search has finished.
                if (failedFolderCount >= folderCount) {
                    hasRemoteSearchFailed = true
                    remoteSearchProblem = getString(R.string.remote_search_error)
                    remoteSearchProblemReason = reason
                } else {
                    val details = getString(R.string.remote_search_incomplete_details, failedFolderCount, folderCount)
                    remoteSearchProblem = getString(R.string.remote_search_incomplete)
                    remoteSearchProblemReason = listOfNotNull(details, reason).joinToString(separator = " ")
                }
            }
        }

        override fun remoteSearchStarted(folderId: Long) {
            handler.progress(true)

            handler.post {
                if (isAdded) {
                    updateFooterText(null)
                }
            }
        }

        override fun remoteSearchCascadeProgress(
            accountUuid: String,
            accountName: String,
            folderName: String,
            folderIndex: Int,
            folderCount: Int,
        ) {
            handler.post {
                if (!isAdded) return@post

                val isCurrentAccount = accountUuid == account?.id?.toString()
                val folderLabel = if (isCurrentAccount) folderName else "$accountName / $folderName"
                runningSearchAccounts[accountUuid] = folderLabel
                val folderLabels = runningSearchAccounts.values.joinToString(separator = " · ")
                showSearchProgress(folderIndex, folderCount, folderLabels)
            }
        }

        override fun remoteSearchStep(step: SearchStep) {
            handler.post {
                // A search that has ended in the meantime doesn't do anything anymore.
                if (remoteSearchFuture != null) {
                    startSearchStep(step)
                }
            }
        }

        override fun remoteSearchDownloadProgress(folderName: String, completed: Int, total: Int) {
            handler.post {
                if (!isAdded) return@post

                showSearchDetail(getString(R.string.remote_search_status_downloading, completed, total))
            }
        }

        override fun remoteSearchFolderResults(accountUuid: String, folderId: Long, messageServerIds: List<String>) {
            handler.post {
                if (!isAdded) return@post

                // The server decides what matches. Display its results even if the local search doesn't find them,
                // e.g. because the message contents haven't been downloaded.
                val accountId = AccountIdFactory.of(accountUuid)
                val messageReferences = messageServerIds.map { MessageReference(accountId, folderId, it) }
                if (legacyViewModel.remoteSearchResults.addAll(messageReferences)) {
                    loadMessageList()
                }
            }
        }

        override fun remoteSearchFolderSearched(accountUuid: String, folderId: Long) {
            handler.post {
                // Remember the folder, so a search that is stopped doesn't search it again.
                remoteSearchSearchedFolders.add(MessagingController.searchedFolderKey(accountUuid, folderId))
            }
        }

        override fun remoteSearchAccountFinished(accountUuid: String) {
            handler.post {
                runningSearchAccounts.remove(accountUuid)
            }
        }

        override fun remoteSearchResultsLimited(notLoadedCount: Int) {
            handler.post {
                remoteSearchNotLoadedCount = notLoadedCount
            }
        }

        override fun remoteSearchMessageDownloaded(folderName: String, subject: String?) {
            handler.post {
                if (!isAdded) return@post

                remoteSearchFoundCount++
                val displaySubject = subject?.takeIf { it.isNotBlank() } ?: getString(R.string.general_no_subject)
                showSearchDetail(
                    resources.getQuantityString(
                        R.plurals.remote_search_status_found,
                        remoteSearchFoundCount,
                        remoteSearchFoundCount,
                        displaySubject,
                    ),
                )
            }
        }

        override fun enableProgressIndicator(enable: Boolean) {
            handler.progress(enable)
        }

        override fun remoteSearchFinished(
            folderId: Long,
            numResults: Int,
            maxResults: Int,
            extraResults: List<String>?,
        ) {
            handler.progress(false)
            handler.remoteSearchFinished()

            handler.post {
                extraSearchResults = extraResults
                if (isSearchingMessageContents) {
                    isSearchingMessageContents = false
                    // Offer the search again if it didn't cover all folders
                    hasSearchedMessageContents = remoteSearchProblem == null
                }
                if (isAttachmentSearch) {
                    // The messages that were downloaded are now part of the local search.
                    loadMessageList(forceUpdate = true)
                }
                showSearchOutcome()
                swipeRefreshLayout?.isEnabled = isPullToRefreshAllowed
                if (isAdded) {
                    updateFooterText()
                }
            }
        }

        private fun informUserOfStatus() {
            handler.refreshTitle()
        }

        override fun synchronizeMailboxStarted(accountId: AccountId, folderId: Long) {
            if (updateForMe(accountId, folderId)) {
                hasLoadingMessagesFailed = false
                handler.progress(true)
                handler.folderLoading(folderId, true)

                synchronized(lock) {
                    folderCompleted = 0
                    folderTotal = 0
                }

                informUserOfStatus()
            }
        }

        override fun synchronizeMailboxHeadersProgress(
            accountId: AccountId,
            folderServerId: String,
            completed: Int,
            total: Int,
        ) {
            synchronized(lock) {
                folderCompleted = completed
                folderTotal = total
            }

            informUserOfStatus()
        }

        override fun synchronizeMailboxHeadersFinished(
            accountId: AccountId,
            folderServerId: String,
            total: Int,
            completed: Int,
        ) {
            synchronized(lock) {
                folderCompleted = 0
                folderTotal = 0
            }

            informUserOfStatus()
        }

        override fun synchronizeMailboxProgress(accountId: AccountId, folderId: Long, completed: Int, total: Int) {
            synchronized(lock) {
                folderCompleted = completed
                folderTotal = total
            }

            informUserOfStatus()
        }

        override fun synchronizeMailboxFinished(accountId: AccountId, folderId: Long) {
            if (updateForMe(accountId, folderId)) {
                handler.progress(false)
                handler.folderLoading(folderId, false)
            }
        }

        override fun synchronizeMailboxFailed(accountId: AccountId, folderId: Long, message: String) {
            if (updateForMe(accountId, folderId)) {
                // Displayed in the footer of the message list, and keeps messages from being loaded again and again
                loadingMessagesFailureReason = message
                hasLoadingMessagesFailed = true
                handler.progress(false)
                handler.folderLoading(folderId, false)
            }
        }

        override fun checkMailFinished(context: Context?, accountId: AccountId?) {
            handler.progress(false)
        }

        private fun updateForMe(accountId: AccountId?, folderId: Long): Boolean {
            if (accountId == null || accountId !in accountIds) return false

            val folderIds = localSearch.folderIds
            return folderIds.isEmpty() || folderId in folderIds
        }

        fun getFolderCompleted(): Int {
            synchronized(lock) {
                return folderCompleted
            }
        }

        fun getFolderTotal(): Int {
            synchronized(lock) {
                return folderTotal
            }
        }
    }

    internal inner class ActionModeCallback : ActionMode.Callback {
        private var selectAll: MenuItem? = null
        private var markAsRead: MenuItem? = null
        private var markAsUnread: MenuItem? = null
        private var flag: MenuItem? = null
        private var unflag: MenuItem? = null
        private var disableMarkAsRead = false
        private var disableFlag = false

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            selectAll = menu.findItem(R.id.select_all)
            markAsRead = menu.findItem(R.id.mark_as_read)
            markAsUnread = menu.findItem(R.id.mark_as_unread)
            flag = menu.findItem(R.id.flag)
            unflag = menu.findItem(R.id.unflag)

            // we don't support cross account actions atm
            if (!isSingleAccountMode) {
                val accounts = accountUuidsForSelected.mapNotNull { accountUuid ->
                    accountManager.findById(AccountIdFactory.of(accountUuid))
                }

                menu.findItem(R.id.move).isVisible = true
                menu.findItem(R.id.copy).isVisible = true

                // Disable archive/spam options here and maybe enable below when checking account capabilities
                menu.findItem(R.id.archive).isVisible = false
                menu.findItem(R.id.spam).isVisible = false

                for (account in accounts) {
                    setContextCapabilities(account, menu)
                }
            }

            return true
        }

        private val accountUuidsForSelected: Set<String>
            get() = adapter.selectedMessages.mapToSet { it.account.id.toString() }

        override fun onDestroyActionMode(mode: ActionMode) {
            actionMode = null
            selectAll = null
            markAsRead = null
            markAsUnread = null
            flag = null
            unflag = null

            adapter.clearSelected()
        }

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.message_list_context_menu, menu)

            setContextCapabilities(account, menu)
            return true
        }

        private fun setContextCapabilities(account: LegacyAccount?, menu: Menu) {
            if (!isSingleAccountMode || account == null) {
                // We don't support cross-account copy/move operations right now
                menu.findItem(R.id.move).isVisible = false
                menu.findItem(R.id.copy).isVisible = false

                if (account?.hasArchiveFolder() == true) {
                    menu.findItem(R.id.archive).isVisible = true
                }

                if (account?.hasSpamFolder() == true) {
                    menu.findItem(R.id.spam).isVisible = true
                }
            } else if (isOutbox) {
                menu.findItem(R.id.mark_as_read).isVisible = false
                menu.findItem(R.id.mark_as_unread).isVisible = false
                menu.findItem(R.id.archive).isVisible = false
                menu.findItem(R.id.copy).isVisible = false
                menu.findItem(R.id.flag).isVisible = false
                menu.findItem(R.id.unflag).isVisible = false
                menu.findItem(R.id.spam).isVisible = false
                menu.findItem(R.id.move).isVisible = false

                disableMarkAsRead = true
                disableFlag = true

                if (account.hasDraftsFolder()) {
                    menu.findItem(R.id.move_to_drafts).isVisible = true
                }
            } else {
                if (!messagingController.isCopyCapable(account.id)) {
                    menu.findItem(R.id.copy).isVisible = false
                }

                if (!messagingController.isMoveCapable(account.id)) {
                    menu.findItem(R.id.move).isVisible = false
                    menu.findItem(R.id.archive).isVisible = false
                    menu.findItem(R.id.spam).isVisible = false
                } else {
                    if (!account.hasArchiveFolder() || isArchiveFolder) {
                        menu.findItem(R.id.archive).isVisible = false
                    }

                    if (!account.hasSpamFolder() || isSpamFolder) {
                        menu.findItem(R.id.spam).isVisible = false
                    }
                }
            }
        }

        fun showSelectAll(show: Boolean) {
            selectAll?.isVisible = show
        }

        fun showMarkAsRead(show: Boolean) {
            if (!disableMarkAsRead) {
                markAsRead?.isVisible = show
                markAsUnread?.isVisible = !show
            }
        }

        fun showFlag(show: Boolean) {
            if (!disableFlag) {
                flag?.isVisible = show
                unflag?.isVisible = !show
            }
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            // In the following we assume that we can't move or copy mails to the same folder. Also that spam isn't
            // available if we are in the spam folder, same for archive.

            val endSelectionMode = when (item.itemId) {
                R.id.delete -> {
                    onDelete(selectedMessages, selectedMessagesCount)
                    true
                }

                R.id.mark_as_read -> {
                    markSelectedReadWithUndo(true)
                    false
                }

                R.id.mark_as_unread -> {
                    markSelectedReadWithUndo(false)
                    false
                }

                R.id.flag -> {
                    setFlagForSelected(Flag.FLAGGED, true)
                    false
                }

                R.id.unflag -> {
                    setFlagForSelected(Flag.FLAGGED, false)
                    false
                }

                R.id.select_all -> {
                    selectAll()
                    false
                }

                R.id.archive -> {
                    onArchive(selectedMessages)
                    // TODO: Only finish action mode if all messages have been moved.
                    true
                }

                R.id.spam -> {
                    onSpam(selectedMessages, selectedMessagesCount)
                    // TODO: Only finish action mode if all messages have been moved.
                    true
                }

                R.id.move -> {
                    onMove(selectedMessages)
                    true
                }

                R.id.move_to_drafts -> {
                    onMoveToDraftsFolder(selectedMessages)
                    true
                }

                R.id.copy -> {
                    onCopy(selectedMessages)
                    true
                }

                else -> return false
            }

            if (endSelectionMode) {
                mode.finish()
            }

            return true
        }
    }

    private enum class FolderOperation {
        COPY,
        MOVE,
    }

    @Suppress("detekt.UnnecessaryAnnotationUseSiteTarget") // https://github.com/detekt/detekt/issues/8212
    private enum class Error(@param:StringRes val errorText: Int) {
        FolderNotFound(R.string.message_list_error_folder_not_found),
    }

    object Factory : MessageListFragmentBridgeContract.Factory {
        override fun newInstance(
            search: LocalMessageSearch,
            isThreadDisplay: Boolean,
            threadedList: Boolean,
        ): LegacyMessageListFragment {
            val searchBytes = LocalMessageSearchSerializer.serialize(search)

            return LegacyMessageListFragment().apply {
                arguments = bundleOf(
                    ARG_SEARCH to searchBytes,
                    ARG_IS_THREAD_DISPLAY to isThreadDisplay,
                    ARG_THREADED_LIST to threadedList,
                )
            }
        }
    }

    companion object {
        protected const val ARG_SEARCH = "searchObject"
        protected const val ARG_THREADED_LIST = "showingThreadedList"
        protected const val ARG_IS_THREAD_DISPLAY = "isThreadedDisplay"

        protected const val STATE_SELECTED_MESSAGES = "selectedMessages"
        protected const val STATE_ACTIVE_MESSAGES = "activeMessages"
        protected const val STATE_ACTIVE_MESSAGE = "activeMessage"
        protected const val STATE_REMOTE_SEARCH_PERFORMED = "remoteSearchPerformed"
        protected const val STATE_SEARCH_VIEW_QUERY = "searchViewQuery"
        protected const val STATE_SEARCH_VIEW_ICONIFIED = "searchViewIconified"
        protected const val STATE_SEARCH_FILTERS = "searchFilters"
    }
}

/**
 * A message list that can bring one of its messages into view and highlight it, so the user can see the message they
 * just came from.
 */
interface MessageListHighlighter {
    fun highlightMessage(messageReference: MessageReference)
}
