package com.fsck.k9.ui.settings

import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.view.ActionMode
import com.fsck.k9.ui.R

/**
 * Action mode shown while the user selects accounts in the settings list.
 */
internal class AccountSelectionActionModeCallback(
    private val onSelectAll: () -> Unit,
    private val onDelete: () -> Unit,
    private val onDestroy: () -> Unit,
) : ActionMode.Callback {
    private var selectAllItem: MenuItem? = null
    private var deleteItem: MenuItem? = null

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.menuInflater.inflate(R.menu.settings_list_selection_mode, menu)
        updateMenuItems(menu)

        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        updateMenuItems(menu)

        return true
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.select_all_accounts -> {
                onSelectAll()
                true
            }
            R.id.delete_accounts -> {
                onDelete()
                true
            }
            else -> false
        }
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        selectAllItem = null
        deleteItem = null
        onDestroy()
    }

    fun setDeleteEnabled(enabled: Boolean) {
        deleteItem?.isEnabled = enabled
    }

    private fun updateMenuItems(menu: Menu) {
        selectAllItem = menu.findItem(R.id.select_all_accounts)
        deleteItem = menu.findItem(R.id.delete_accounts)
    }
}
