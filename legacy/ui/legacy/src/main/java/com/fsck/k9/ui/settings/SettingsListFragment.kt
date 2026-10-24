package com.fsck.k9.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.k9mail.core.ui.legacy.designsystem.atom.icon.Icons
import app.k9mail.feature.launcher.FeatureLauncherActivity
import app.k9mail.feature.launcher.FeatureLauncherTarget
import app.k9mail.feature.migration.launcher.api.MigrationManager
import com.fsck.k9.ui.R
import com.fsck.k9.ui.observe
import com.fsck.k9.ui.settings.account.AccountSettingsActivity
import com.fsck.k9.view.DraggableFrameLayout
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.GenericItem
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.drag.ItemTouchCallback
import com.mikepenz.fastadapter.drag.SimpleDragCallback
import com.mikepenz.fastadapter.utils.DragDropUtil
import net.thunderbird.core.android.account.LegacyAccount
import net.thunderbird.core.common.provider.BrandNameProvider
import net.thunderbird.feature.account.AccountIdFactory
import net.thunderbird.feature.funding.api.FundingManager
import net.thunderbird.feature.funding.api.FundingType
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import app.k9mail.feature.settings.importing.R as SettingsImportR

class SettingsListFragment : Fragment(), ItemTouchCallback {
    private val viewModel: SettingsViewModel by viewModel()
    private val fundingManager: FundingManager by inject()
    private val brandNameProvider: BrandNameProvider by inject()
    private val migrationManager: MigrationManager by inject()

    private lateinit var itemAdapter: ItemAdapter<GenericItem>

    private var dragCallback: SimpleDragCallback? = null
    private var actionMode: ActionMode? = null
    private var accounts: List<LegacyAccount> = emptyList()
    private var isSelectionMode = false

    private val actionModeCallback = AccountSelectionActionModeCallback(
        onSelectAll = ::selectAllAccounts,
        onDelete = ::showDeleteAccountsDialog,
        onDestroy = ::exitSelectionMode,
    )

    private val backPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            actionMode?.finish()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        initializeSettingsList(recyclerView = view.findViewById(R.id.settings_list))
        observeAccounts()
        observeAccountSelection()
        addOptionsMenu()
        listenForDeleteConfirmation()
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)
    }

    private fun initializeSettingsList(recyclerView: RecyclerView) {
        itemAdapter = ItemAdapter()

        val touchCallBack = SimpleDragCallback(this).apply {
            setIsDragEnabled(true)
        }
        dragCallback = touchCallBack
        val touchHelper = ItemTouchHelper(touchCallBack)

        val settingsListAdapter = FastAdapter.with(itemAdapter).apply {
            setHasStableIds(true)
            onClickListener = { _, _, item, _ ->
                handleItemClick(item)
                true
            }
            addEventHook(
                DragHandleTouchEvent { position ->
                    recyclerView.findViewHolderForAdapterPosition(position)?.let { viewHolder ->
                        touchHelper.startDrag(viewHolder)
                    }
                },
            )
        }

        recyclerView.adapter = settingsListAdapter
        recyclerView.layoutManager = LinearLayoutManager(context)
        touchHelper.attachToRecyclerView(recyclerView)
    }

    private fun observeAccounts() {
        viewModel.accounts.observe(viewLifecycleOwner) { accountList ->
            accountList ?: return@observe

            accounts = accountList
            if (accountList.isEmpty()) {
                launchOnboarding()
            } else {
                populateSettingsList(accountList)
            }

            invalidateOptionsMenu()
        }
    }

    private fun observeAccountSelection() {
        viewModel.selection.observe(viewLifecycleOwner) { selectedIds ->
            if (accounts.isNotEmpty()) {
                populateSettingsList(accounts)
            }

            updateActionMode(selectedIds.size)
        }
    }

    private fun populateSettingsList(accountList: List<LegacyAccount>) {
        val listItems = buildSettingsList {
            addAction(
                text = getString(R.string.general_settings_title),
                navigationAction = R.id.action_settingsListScreen_to_generalSettingsScreen,
                icon = Icons.Outlined.Settings,
            )

            addAccountsSection(accountList)

            addSection(title = getString(R.string.settings_list_transfer_category)) {
                migrationManager.createTransferChoiceIntent(requireContext())?.let { intent ->
                    addIntent(
                        text = getString(R.string.settings_transfer_send_title),
                        icon = Icons.Outlined.Upload,
                        intent = intent,
                    )
                }

                addAction(
                    text = getString(R.string.settings_transfer_receive_title),
                    navigationAction = R.id.action_settingsListScreen_to_settingsImportScreen,
                    icon = Icons.Outlined.Download,
                )
            }

            addSection(title = getString(R.string.settings_list_backup_category)) {
                addAction(
                    text = getString(R.string.settings_export_title),
                    navigationAction = R.id.action_settingsListScreen_to_settingsExportScreen,
                    icon = Icons.Outlined.Upload,
                )

                addAction(
                    text = getString(SettingsImportR.string.settings_import_title),
                    navigationAction = R.id.action_settingsListScreen_to_settingsImportScreen,
                    icon = Icons.Outlined.Download,
                )
            }

            addSection(title = getString(R.string.settings_list_miscellaneous_category)) {
                addAction(
                    text = getString(R.string.about_action),
                    navigationAction = R.id.action_settingsListScreen_to_aboutScreen,
                    icon = Icons.Outlined.Info,
                )

                addUrlAction(
                    text = getString(R.string.get_help_title),
                    url = getString(R.string.user_forum_url),
                    icon = Icons.Outlined.Help,
                )

                addFunding()
            }
        }

        itemAdapter.setNewList(listItems)
    }

    private fun SettingsListBuilder.addAccountsSection(accountList: List<LegacyAccount>) {
        addSection(title = getString(R.string.accounts_title)) {
            val isDraggable = accountList.size > 1 && !isSelectionMode
            val selectedAccountIds = viewModel.selection.value
            for (account in accountList) {
                addAccount(
                    account = account,
                    isDraggable = isDraggable,
                    isSelectionMode = isSelectionMode,
                    selected = account.id in selectedAccountIds,
                )
            }

            addAction(
                text = getString(R.string.add_account_action),
                navigationAction = R.id.action_settingsListScreen_to_addAccountScreen,
                icon = Icons.Outlined.Add,
            )
        }
    }

    private fun SettingsListBuilder.addFunding() {
        when (fundingManager.getFundingType()) {
            FundingType.GOOGLE_PLAY -> {
                addIntent(
                    text = getString(R.string.settings_list_action_support, brandNameProvider.brandName),
                    icon = Icons.Outlined.Favorite,
                    intent = FeatureLauncherActivity.getIntent(
                        context = requireActivity(),
                        target = FeatureLauncherTarget.Funding,
                    ),
                )
            }

            FundingType.LINK -> {
                addUrlAction(
                    text = getString(R.string.settings_list_action_support, brandNameProvider.brandName),
                    url = getString(R.string.funding_url, requireContext().getPackageName()),
                    icon = Icons.Outlined.Favorite,
                )
            }

            FundingType.NO_FUNDING -> Unit
        }
    }

    private fun handleItemClick(item: GenericItem) {
        when (item) {
            is AccountItem -> handleAccountClick(item)
            is IntentItem -> if (!isSelectionMode) startActivity(item.intent)
            is SettingsActionItem -> if (!isSelectionMode) findNavController().navigate(item.navigationAction)
            is UrlActionItem -> if (!isSelectionMode) openUrl(item.url)
        }
    }

    private fun handleAccountClick(item: AccountItem) {
        if (isSelectionMode) {
            viewModel.toggleAccountSelection(item.account.id)
        } else {
            launchAccountSettings(item.account)
        }
    }

    private fun openUrl(url: String) {
        try {
            val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(viewIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.error_activity_not_found, Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchAccountSettings(account: LegacyAccount) {
        AccountSettingsActivity.start(requireActivity(), account.id.toString())
    }

    private fun launchOnboarding() {
        FeatureLauncherActivity.launch(requireActivity(), FeatureLauncherTarget.Onboarding)

        requireActivity().finishAffinity()
    }

    private fun addOptionsMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.settings_list_option, menu)
                }

                override fun onPrepareMenu(menu: Menu) {
                    menu.findItem(R.id.select_accounts).isVisible = accounts.isNotEmpty()
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    return when (menuItem.itemId) {
                        R.id.select_accounts -> {
                            startSelectionMode()
                            true
                        }
                        else -> false
                    }
                }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED,
        )
    }

    private fun invalidateOptionsMenu() {
        requireActivity().invalidateOptionsMenu()
    }

    private fun startSelectionMode() {
        val newActionMode = startActionMode() ?: return
        actionMode = newActionMode
        isSelectionMode = true
        backPressedCallback.isEnabled = true
        dragCallback?.setIsDragEnabled(false)
        populateSettingsList(accounts)
        updateActionMode(0)
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        actionMode = null
        backPressedCallback.isEnabled = false
        dragCallback?.setIsDragEnabled(true)
        viewModel.clearAccountSelection()
    }

    private fun startActionMode(): ActionMode? {
        val activity = requireActivity() as? AppCompatActivity ?: return null

        return activity.startSupportActionMode(actionModeCallback)
    }

    private fun selectAllAccounts() {
        viewModel.selectAllAccounts(accounts.map { it.id })
    }

    private fun updateActionMode(selectedCount: Int) {
        val currentActionMode = actionMode ?: return
        currentActionMode.title = resources.getQuantityString(
            R.plurals.settings_selected_accounts_count,
            selectedCount,
            selectedCount,
        )
        actionModeCallback.setDeleteEnabled(selectedCount > 0)
        currentActionMode.invalidate()
    }

    private fun showDeleteAccountsDialog() {
        val selectedAccountIds = viewModel.selection.value
        if (selectedAccountIds.isEmpty()) return

        val selectedAccounts = accounts.filter { it.id in selectedAccountIds }
        DeleteAccountsDialogFragment.newInstance(
            accountIds = selectedAccountIds.map { it.toString() },
            accountNames = selectedAccounts.map { it.name ?: it.email },
            allAccountsSelected = viewModel.isAllSelected(accounts.size),
        ).show(childFragmentManager, TAG_DELETE_ACCOUNTS)
    }

    private fun listenForDeleteConfirmation() {
        childFragmentManager.setFragmentResultListener(
            DeleteAccountsDialogFragment.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, result ->
            val accountIds = result.getStringArrayList(DeleteAccountsDialogFragment.RESULT_ACCOUNT_IDS).orEmpty()
            deleteAccounts(accountIds)
        }
    }

    private fun deleteAccounts(accountIds: List<String>) {
        viewModel.deleteAccounts(accountIds.map { AccountIdFactory.of(it) })
        actionMode?.finish()
    }

    private fun buildSettingsList(block: SettingsListBuilder.() -> Unit): List<GenericItem> {
        return SettingsListBuilder().apply(block).toList()
    }

    internal class SettingsListBuilder {
        private val settingsList = mutableListOf<GenericItem>()
        private var itemId = 0L

        fun addAction(text: String, @IdRes navigationAction: Int, @DrawableRes icon: Int) {
            itemId++
            settingsList.add(SettingsActionItem(itemId, text, navigationAction, icon))
        }

        fun addUrlAction(text: String, url: String, @DrawableRes icon: Int) {
            itemId++
            settingsList.add(UrlActionItem(itemId, text, url, icon))
        }

        fun addAccount(
            account: LegacyAccount,
            isDraggable: Boolean,
            isSelectionMode: Boolean,
            selected: Boolean,
        ) {
            settingsList.add(AccountItem(account, isDraggable, isSelectionMode, selected))
        }

        fun addIntent(text: String, @DrawableRes icon: Int, intent: Intent) {
            itemId++
            settingsList.add(IntentItem(itemId, text, icon, intent))
        }

        fun addSection(title: String, block: SettingsListBuilder.() -> Unit) {
            itemId++
            settingsList.add(SettingsDividerItem(itemId, title))
            block()
        }

        fun toList(): List<GenericItem> = settingsList
    }

    override fun itemTouchStartDrag(viewHolder: RecyclerView.ViewHolder) {
        (viewHolder.itemView as DraggableFrameLayout).isDragged = true
    }

    override fun itemTouchStopDrag(viewHolder: RecyclerView.ViewHolder) {
        (viewHolder.itemView as DraggableFrameLayout).isDragged = false
    }

    override fun itemTouchOnMove(oldPosition: Int, newPosition: Int): Boolean {
        val firstDropPosition = itemAdapter.adapterItems.indexOfFirst { it is AccountItem }
        val lastDropPosition = itemAdapter.adapterItems.indexOfLast { it is AccountItem }

        return if (newPosition in firstDropPosition..lastDropPosition) {
            DragDropUtil.onMove(itemAdapter, oldPosition, newPosition)
            true
        } else {
            false
        }
    }

    override fun itemTouchDropped(oldPosition: Int, newPosition: Int) {
        if (oldPosition == newPosition) return

        val account = (itemAdapter.getAdapterItem(newPosition) as AccountItem).account
        val firstAccountPosition = itemAdapter.adapterItems.indexOfFirst { it is AccountItem }
        val newAccountPosition = newPosition - firstAccountPosition

        viewModel.moveAccount(account.id, newAccountPosition)
    }

    companion object {
        private const val TAG_DELETE_ACCOUNTS = "delete_accounts_confirmation"
    }
}
