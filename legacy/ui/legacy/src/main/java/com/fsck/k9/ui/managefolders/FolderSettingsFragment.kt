package com.fsck.k9.ui.managefolders

import android.widget.FrameLayout
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.widget.Toast
import net.thunderbird.feature.mail.folder.FolderType
import net.thunderbird.feature.mail.folder.api.Folder
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import app.k9mail.legacy.ui.folder.FolderNameFormatter
import com.fsck.k9.fragment.ConfirmationDialogFragment
import com.fsck.k9.fragment.ConfirmationDialogFragment.ConfirmationDialogFragmentListener
import com.fsck.k9.ui.R
import com.fsck.k9.ui.base.livedata.observeNotNull
import com.takisoft.preferencex.PreferenceFragmentCompat
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import com.fsck.k9.ui.base.R as BaseR

class FolderSettingsFragment : PreferenceFragmentCompat(), ConfirmationDialogFragmentListener {
    private val viewModel: FolderSettingsViewModel by viewModel()
    private val folderNameFormatter: FolderNameFormatter by inject()
    private var folder: Folder? = null

    override fun onCreatePreferencesFix(savedInstanceState: Bundle?, rootKey: String?) {
        // Set empty preferences resource while data is being loaded
        setPreferencesFromResource(R.xml.empty_preferences, null)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.folder_settings_option, menu)

                    val clearFolderItem = menu.findItem(R.id.clear_local_folder)
                    clearFolderItem.isVisible = viewModel.showClearFolderInMenu

                    // Folders that only exist on the device can't have subfolders. Only regular folders can be
                    // deleted; the app relies on the others.
                    val folder = folder
                    val isRemoteFolder = viewModel.showClearFolderInMenu && folder != null && !folder.isLocalOnly
                    menu.findItem(R.id.create_subfolder).isVisible = isRemoteFolder
                    val isRegularFolder = isRemoteFolder && folder?.type == FolderType.REGULAR
                    menu.findItem(R.id.rename_folder).isVisible = isRegularFolder
                    menu.findItem(R.id.delete_folder).isVisible = isRegularFolder
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    return when (menuItem.itemId) {
                        R.id.clear_local_folder -> {
                            viewModel.showClearFolderConfirmationDialog()
                            true
                        }
                        R.id.create_subfolder -> {
                            showCreateSubfolderDialog()
                            true
                        }
                        R.id.rename_folder -> {
                            showRenameFolderDialog()
                            true
                        }
                        R.id.delete_folder -> {
                            viewModel.showDeleteFolderConfirmationDialog()
                            true
                        }
                        else -> false
                    }
                }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED,
        )

        val arguments = arguments ?: error("Arguments missing")
        val accountUuid = arguments.getString(EXTRA_ACCOUNT) ?: error("Missing argument '$EXTRA_ACCOUNT'")
        val folderId = arguments.getLong(EXTRA_FOLDER_ID)

        viewModel.getFolderSettingsLiveData(accountUuid, folderId)
            .observeNotNull(viewLifecycleOwner) { folderSettingsResult ->
                when (folderSettingsResult) {
                    is FolderNotFound -> navigateBack()
                    is FolderSettingsData -> initPreferences(folderSettingsResult)
                }
            }

        viewModel.getActionEvents().observeNotNull(this) { handleActionEvents(it) }
    }

    private fun navigateBack() {
        findNavController().popBackStack()
    }

    private fun initPreferences(folderSettings: FolderSettingsData) {
        folder = folderSettings.folder
        preferenceManager.preferenceDataStore = folderSettings.dataStore
        setPreferencesFromResource(R.xml.folder_settings_preferences, null)

        setCategoryTitle(folderSettings)
        updateMenu()
        setPreferenceVisibility(folderSettings)
    }

    private fun updateMenu() {
        requireActivity().invalidateOptionsMenu()
    }

    private fun setCategoryTitle(folderSettings: FolderSettingsData) {
        val folderDisplayName = folderNameFormatter.displayName(folderSettings.folder)
        findPreference<Preference>(PREFERENCE_TOP_CATEGORY)!!.title = folderDisplayName
    }

    private fun handleActionEvents(action: Action) {
        when (action) {
            is Action.ShowClearFolderConfirmationDialog -> showClearFolderConfirmationDialog()
            is Action.ShowDeleteFolderConfirmationDialog -> showDeleteFolderConfirmationDialog(action.subfolderCount)
            is Action.FolderRenamed -> {
                showMessage(getString(R.string.folder_renamed))
                navigateBack()
            }
            is Action.RenameFolderFailed -> showMessage(getString(R.string.folder_rename_failed, action.reason))
            is Action.FolderCreated -> showMessage(getString(R.string.folder_created))
            is Action.CreateFolderFailed -> showMessage(getString(R.string.folder_create_failed, action.reason))
            is Action.FolderDeleted -> {
                showMessage(getString(R.string.folder_deleted))
                navigateBack()
            }
            is Action.DeleteFolderFailed -> showMessage(getString(R.string.folder_delete_failed, action.reason))
        }
    }

    private fun showMessage(message: String) {
        Toast.makeText(requireContext().applicationContext, message, Toast.LENGTH_LONG).show()
    }

    private fun showCreateSubfolderDialog() {
        FolderNameDialog.show(
            context = requireContext(),
            title = R.string.folder_create_subfolder_action,
            pathDelimiter = viewModel.folderPathDelimiter,
            onNameEntered = viewModel::createSubfolder,
        )
    }

    private fun showRenameFolderDialog() {
        val pathDelimiter = viewModel.folderPathDelimiter
        val name = folder?.name.orEmpty()
        FolderNameDialog.show(
            context = requireContext(),
            title = R.string.folder_rename_action,
            pathDelimiter = pathDelimiter,
            confirmAction = R.string.folder_rename_confirm_action,
            initialName = if (pathDelimiter.isEmpty()) name else name.substringAfterLast(pathDelimiter),
            onNameEntered = viewModel::renameFolder,
        )
    }

    private fun showDeleteFolderConfirmationDialog(subfolderCount: Int) {
        val context = requireContext()
        val folderName = folder?.let { folderNameFormatter.displayName(it) }.orEmpty()
        var message = getString(R.string.dialog_confirm_delete_folder_message, folderName)

        // Only a folder with subfolders offers to delete them as well. They are kept unless the user asks for it.
        val subfoldersCheckBox = MaterialCheckBox(context).apply {
            text = resources.getQuantityString(
                R.plurals.dialog_confirm_delete_folder_subfolders,
                subfolderCount,
                subfolderCount,
            )
        }
        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.dialog_confirm_delete_folder_title)
        if (subfolderCount > 0) {
            message += " " + getString(R.string.dialog_confirm_delete_folder_subfolders_kept)
            val margin = (DIALOG_CONTENT_MARGIN_DP * resources.displayMetrics.density).toInt()
            val container = FrameLayout(context).apply {
                setPadding(margin, 0, margin, 0)
                addView(subfoldersCheckBox)
            }
            builder.setView(container)
        }

        builder
            .setMessage(message)
            .setPositiveButton(R.string.folder_delete_action) { _, _ ->
                viewModel.onDeleteFolderConfirmation(includeSubfolders = subfoldersCheckBox.isChecked)
            }
            .setNegativeButton(BaseR.string.cancel_action, null)
            .show()
    }

    private fun showClearFolderConfirmationDialog() {
        val dialogFragment = ConfirmationDialogFragment.newInstance(
            DIALOG_CLEAR_FOLDER,
            getString(R.string.dialog_confirm_clear_local_folder_title),
            getString(R.string.dialog_confirm_clear_local_folder_message),
            getString(R.string.dialog_confirm_clear_local_folder_action),
            getString(BaseR.string.cancel_action),
        )
        dialogFragment.setTargetFragment(this, REQUEST_CLEAR_FOLDER)
        dialogFragment.show(requireFragmentManager(), TAG_CLEAR_FOLDER_CONFIRMATION)
    }

    private fun setPreferenceVisibility(folderSettings: FolderSettingsData) {
        if (folderSettings.folder.isLocalOnly) {
            requirePreference<Preference>(PREFERENCE_SYNC).isVisible = false
            requirePreference<Preference>(PREFERENCE_PUSH).isVisible = false
            requirePreference<Preference>(PREFERENCE_NOTIFICATIONS).isVisible = false
        }
    }

    override fun doPositiveClick(dialogId: Int) {
        when (dialogId) {
            DIALOG_CLEAR_FOLDER -> {
                viewModel.onClearFolderConfirmation()
            }
        }
    }

    override fun doNegativeClick(dialogId: Int) = Unit

    override fun dialogCancelled(dialogId: Int) = Unit

    private fun <T : Preference> requirePreference(key: String): T {
        return findPreference(key) ?: error("Preference '$key' not found")
    }

    companion object {
        const val EXTRA_ACCOUNT = "account"
        const val EXTRA_FOLDER_ID = "folderId"

        private const val DIALOG_CONTENT_MARGIN_DP = 16

        private const val DIALOG_CLEAR_FOLDER = 1
        private const val REQUEST_CLEAR_FOLDER = 1
        private const val TAG_CLEAR_FOLDER_CONFIRMATION = "clear_folder_confirmation"

        private const val PREFERENCE_TOP_CATEGORY = "folder_settings"
        private const val PREFERENCE_SYNC = "folder_settings_sync"
        private const val PREFERENCE_PUSH = "folder_settings_push"
        private const val PREFERENCE_NOTIFICATIONS = "folder_settings_notifications"
    }
}
