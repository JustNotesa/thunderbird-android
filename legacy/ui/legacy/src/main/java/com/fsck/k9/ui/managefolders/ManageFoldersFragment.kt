package com.fsck.k9.ui.managefolders

import androidx.core.view.ViewCompat
import android.graphics.drawable.ColorDrawable
import android.view.DragEvent
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.fsck.k9.ui.resolveColorAttribute
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import net.thunderbird.feature.mail.folder.FolderType
import net.thunderbird.feature.mail.folder.api.Folder
import com.fsck.k9.ui.base.R as BaseR
import com.google.android.material.R as MaterialR
import android.widget.Toast
import androidx.core.content.ContextCompat
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.core.os.bundleOf
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import app.k9mail.legacy.ui.folder.DisplayFolder
import app.k9mail.legacy.ui.folder.FolderIconProvider
import app.k9mail.legacy.ui.folder.FolderNameFormatter
import com.fsck.k9.Preferences
import com.fsck.k9.controller.MessagingController
import com.fsck.k9.ui.R
import com.fsck.k9.ui.base.livedata.observeNotNull
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import java.util.Locale
import net.thunderbird.core.android.account.LegacyAccountDto
import net.thunderbird.feature.account.AccountIdFactory
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

@Suppress("TooManyFunctions")
class ManageFoldersFragment : Fragment() {
    private val viewModel: ManageFoldersViewModel by viewModel()
    private val folderNameFormatter: FolderNameFormatter by inject()
    private val messagingController: MessagingController by inject()
    private val preferences: Preferences by inject()
    private val folderIconProvider: FolderIconProvider by inject { parametersOf(requireActivity().theme) }

    private lateinit var account: LegacyAccountDto
    private lateinit var itemAdapter: ItemAdapter<FolderListItem>
    private var folders: Map<Long, Folder> = emptyMap()
    private var displayFolders: List<DisplayFolder> = emptyList()
    private val expandedFolderIds = mutableSetOf<Long>()
    private var folderFilter: String = ""
    private var dropTargetView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val arguments = arguments ?: error("Missing arguments")
        val accountUuid = arguments.getString(EXTRA_ACCOUNT) ?: error("Missing argument '$EXTRA_ACCOUNT'")
        account = preferences.getById(AccountIdFactory.of(accountUuid)) ?: error("Missing account: $accountUuid")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_manage_folders, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.folder_list_option, menu)
                    configureFolderSearchView(menu)
                }

                override fun onPrepareMenu(menu: Menu) {
                    val folderMenuItem = menu.findItem(R.id.filter_folders)
                    folderMenuItem.isVisible = !folderMenuItem.isActionViewExpanded
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    return when (menuItem.itemId) {
                        R.id.list_folders -> {
                            refreshFolderList()
                            true
                        }
                        R.id.create_folder -> {
                            createFolder()
                            true
                        }
                        else -> false
                    }
                }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED,
        )

        initializeFolderList()

        viewModel.getFolders(account).observeNotNull(this) { folders ->
            updateFolderList(folders)
        }
    }

    private fun initializeFolderList() {
        itemAdapter = ItemAdapter()
        itemAdapter.itemFilter.filterPredicate = ::folderListFilter

        val folderListAdapter = FastAdapter.with(itemAdapter).apply {
            setHasStableIds(true)
            onClickListener = { _, _, item: FolderListItem, _ ->
                openFolderSettings(item.folderId)
                true
            }
            onLongClickListener = { view, _, item: FolderListItem, _ ->
                startFolderDrag(view, item)
            }
        }

        val recyclerView = requireView().findViewById<RecyclerView>(R.id.folderList)
        recyclerView.adapter = folderListAdapter
        recyclerView.setOnDragListener { _, event -> onDragOverFolderList(recyclerView, folderListAdapter, event) }

        val topLevelTarget = requireView().findViewById<TextView>(R.id.folder_move_target)
        topLevelTarget.setOnDragListener { _, event -> onDragOverTopLevelTarget(topLevelTarget, event) }
    }

    /**
     * A folder is moved by dragging it onto the folder it is to be moved into, or onto the text above the list to
     * move it to the top level.
     */
    private fun startFolderDrag(view: View, item: FolderListItem): Boolean {
        val folder = folders[item.folderId]
        if (folder != null && !folder.isLocalOnly && folder.type == FolderType.REGULAR) {
            ViewCompat.startDragAndDrop(view, null, View.DragShadowBuilder(view), item.folderId, 0)
        } else if (folder != null) {
            Toast.makeText(requireContext(), R.string.folder_move_not_possible, Toast.LENGTH_SHORT).show()
        }

        return folder != null
    }

    private fun onDragOverFolderList(
        recyclerView: RecyclerView,
        adapter: FastAdapter<FolderListItem>,
        event: DragEvent,
    ): Boolean {
        when (event.action) {
            DragEvent.ACTION_DRAG_LOCATION -> {
                highlightDropTarget(recyclerView.findChildViewUnder(event.x, event.y))
                scrollWhileDragging(recyclerView, event.y)
            }

            DragEvent.ACTION_DROP -> {
                val targetView = recyclerView.findChildViewUnder(event.x, event.y)
                val targetFolderId = targetView
                    ?.let { recyclerView.getChildAdapterPosition(it) }
                    ?.takeIf { it != RecyclerView.NO_POSITION }
                    ?.let { adapter.getItem(it)?.folderId }
                highlightDropTarget(null)
                if (targetFolderId != null) {
                    confirmMoveFolder(event.localState as Long, targetFolderId)
                }
            }

            DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> highlightDropTarget(null)
        }
        return true
    }

    private fun onDragOverTopLevelTarget(target: TextView, event: DragEvent): Boolean {
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> target.setText(R.string.folder_move_top_level_target)
            DragEvent.ACTION_DRAG_ENTERED -> highlightDropTarget(target)
            DragEvent.ACTION_DRAG_EXITED -> highlightDropTarget(null)
            DragEvent.ACTION_DROP -> {
                highlightDropTarget(null)
                confirmMoveFolder(event.localState as Long, null)
            }

            DragEvent.ACTION_DRAG_ENDED -> {
                highlightDropTarget(null)
                target.setText(R.string.folder_move_hint)
            }
        }
        return true
    }

    private fun highlightDropTarget(view: View?) {
        if (view === dropTargetView) return

        dropTargetView?.foreground = null
        dropTargetView = view
        view?.foreground = ColorDrawable(
            ColorUtils.setAlphaComponent(
                requireContext().theme.resolveColorAttribute(MaterialR.attr.colorOnSurface),
                DROP_TARGET_HIGHLIGHT_ALPHA,
            ),
        )
    }

    private fun scrollWhileDragging(recyclerView: RecyclerView, y: Float) {
        val edge = recyclerView.height / DRAG_SCROLL_EDGE_FRACTION
        val distance = (DRAG_SCROLL_DISTANCE_DP * resources.displayMetrics.density).toInt()
        if (y < edge) {
            recyclerView.scrollBy(0, -distance)
        } else if (y > recyclerView.height - edge) {
            recyclerView.scrollBy(0, distance)
        }
    }

    private fun confirmMoveFolder(folderId: Long, targetFolderId: Long?) {
        val folder = folders[folderId]
        val targetFolder = targetFolderId?.let { folders[it] }
        val isTargetKnown = targetFolderId == null || targetFolder != null
        if (folder == null || !isTargetKnown || folderId == targetFolderId) return

        if (targetFolder?.isLocalOnly == true) {
            Toast.makeText(requireContext(), R.string.folder_move_not_possible, Toast.LENGTH_SHORT).show()
            return
        }

        val folderName = folderNameFormatter.displayName(folder)
        val message = if (targetFolder == null) {
            getString(R.string.folder_move_confirm_top_level_message, folderName)
        } else {
            getString(R.string.folder_move_confirm_message, folderName, folderNameFormatter.displayName(targetFolder))
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.folder_move_confirm_title)
            .setMessage(message)
            .setPositiveButton(R.string.folder_move_confirm_action) { _, _ -> moveFolder(folderId, targetFolderId) }
            .setNegativeButton(BaseR.string.cancel_action, null)
            .show()
    }

    private fun moveFolder(folderId: Long, targetFolderId: Long?) {
        val applicationContext = requireContext().applicationContext
        messagingController.moveFolder(account, folderId, targetFolderId) { error ->
            val message = if (error == null) {
                applicationContext.getString(R.string.folder_moved)
            } else {
                applicationContext.getString(R.string.folder_move_failed, error)
            }
            ContextCompat.getMainExecutor(applicationContext).execute {
                Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateFolderList(displayFolders: List<DisplayFolder>) {
        this.displayFolders = displayFolders
        folders = displayFolders.associate { it.folder.id to it.folder }
        showFolders()
    }

    /**
     * Subfolders are displayed below the folder they are in once it is expanded, like in the folder drawer. While
     * the user is looking for a folder, all folders are listed with their full names instead.
     */
    private fun showFolders() {
        val folderListItems = if (folderFilter.isEmpty()) {
            FolderTree.build(displayFolders.map { it.folder }, account.folderPathDelimiter, expandedFolderIds)
                .map { row ->
                    val parentName = row.parent?.name
                    val displayName = folderNameFormatter.displayName(row.folder)
                    FolderListItem(
                        folderId = row.folder.id,
                        folderIconResource = folderIconProvider.getFolderIcon(row.folder.type),
                        displayName = if (parentName == null) {
                            displayName
                        } else {
                            displayName.removePrefix(parentName + account.folderPathDelimiter)
                        },
                        depth = row.depth,
                        isExpanded = row.isExpanded.takeIf { row.hasSubfolders },
                        onExpandClick = ::toggleSubfolders,
                    )
                }
        } else {
            displayFolders.map { displayFolder ->
                FolderListItem(
                    folderId = displayFolder.folder.id,
                    folderIconResource = folderIconProvider.getFolderIcon(displayFolder.folder.type),
                    displayName = folderNameFormatter.displayName(displayFolder.folder),
                )
            }
        }

        itemAdapter.set(folderListItems)
        itemAdapter.filter(folderFilter)
    }

    private fun toggleSubfolders(folderId: Long) {
        if (!expandedFolderIds.remove(folderId)) {
            expandedFolderIds.add(folderId)
        }
        showFolders()
    }

    private fun filterFolders(filter: String) {
        folderFilter = filter
        showFolders()
    }

    private fun openFolderSettings(folderId: Long) {
        val folderSettingsArguments = bundleOf(
            FolderSettingsFragment.EXTRA_ACCOUNT to account.id.toString(),
            FolderSettingsFragment.EXTRA_FOLDER_ID to folderId,
        )
        findNavController().navigate(R.id.action_manageFoldersScreen_to_folderSettingsScreen, folderSettingsArguments)
    }

    private fun configureFolderSearchView(menu: Menu) {
        val folderMenuItem = menu.findItem(R.id.filter_folders)
        val folderSearchView = folderMenuItem.actionView as SearchView
        folderSearchView.maxWidth = Int.MAX_VALUE
        folderSearchView.queryHint = getString(R.string.folder_list_filter_hint)
        folderSearchView.setOnQueryTextListener(
            object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean {
                    filterFolders(query)
                    return true
                }

                override fun onQueryTextChange(newText: String): Boolean {
                    filterFolders(newText)
                    return true
                }
            },
        )
        folderMenuItem?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(p0: MenuItem): Boolean {
                return true
            }

            override fun onMenuItemActionCollapse(p0: MenuItem): Boolean {
                requireActivity().invalidateOptionsMenu()
                return true
            }
        })
    }

    private fun refreshFolderList() {
        messagingController.refreshFolderList(account)
    }

    private fun createFolder() {
        val context = requireContext()
        FolderNameDialog.show(context, R.string.folder_create_action, account.folderPathDelimiter) { name ->
            val applicationContext = context.applicationContext
            messagingController.createFolder(account, null, name) { error ->
                // The folder shows up in the list by itself. A failure has to be said.
                val message = if (error == null) {
                    applicationContext.getString(R.string.folder_created)
                } else {
                    applicationContext.getString(R.string.folder_create_failed, error)
                }
                ContextCompat.getMainExecutor(applicationContext).execute {
                    Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun folderListFilter(item: FolderListItem, constraint: CharSequence?): Boolean {
        if (constraint.isNullOrEmpty()) return true

        val locale = Locale.getDefault()
        val displayName = item.displayName.lowercase(locale)
        return constraint.splitToSequence(" ")
            .filter { it.isNotEmpty() }
            .map { it.lowercase(locale) }
            .any { it in displayName }
    }

    companion object {
        const val EXTRA_ACCOUNT = "account"

        private const val DROP_TARGET_HIGHLIGHT_ALPHA = 40
        private const val DRAG_SCROLL_EDGE_FRACTION = 6
        private const val DRAG_SCROLL_DISTANCE_DP = 12
    }
}
