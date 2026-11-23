package com.fsck.k9.ui.settings.account

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckedTextView
import androidx.preference.PreferenceDialogFragmentCompat
import com.fsck.k9.ui.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * Folder selection dialog: an optional "Automatic" switch on top, below it the radio list of folders.
 */
class FolderListDialogFragment : PreferenceDialogFragmentCompat() {
    private val folderPreference: FolderListPreference
        get() = preference as FolderListPreference

    private lateinit var adapter: FolderAdapter

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        adapter = FolderAdapter()
        // After a configuration change the dialog is restored before the settings fragment has loaded the folders.
        folderPreference.onFoldersChanged = { adapter.notifyDataSetChanged() }
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(folderPreference.dialogTitle)
            .setAdapter(adapter, null)
            .create()
    }

    override fun onDestroyView() {
        (preference as? FolderListPreference)?.onFoldersChanged = null
        super.onDestroyView()
    }

    override fun onDialogClosed(positiveResult: Boolean) = Unit

    private inner class FolderAdapter : BaseAdapter() {
        private val hasSwitch: Boolean
            get() = folderPreference.automaticValue != null
        private val rows: List<Pair<String, CharSequence>>
            get() = folderPreference.dialogRows
        private val offset: Int
            get() = if (hasSwitch) 1 else 0

        override fun hasStableIds(): Boolean = true

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getCount(): Int = rows.size + offset

        override fun getItem(position: Int): Any? = rows.getOrNull(position - offset)

        override fun getViewTypeCount(): Int = 2

        override fun getItemViewType(position: Int): Int = if (hasSwitch && position == 0) 0 else 1

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            return if (getItemViewType(position) == 0) {
                getSwitchView(convertView, parent)
            } else {
                getRowView(position - offset, convertView, parent)
            }
        }

        private fun getSwitchView(convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.preference_folder_automatic_item, parent, false)
            val switchView = view.findViewById<MaterialSwitch>(R.id.automaticSwitch)
            switchView.setOnCheckedChangeListener(null)
            switchView.isChecked = FolderSelection.isAutomatic(folderPreference.value)
            switchView.setOnCheckedChangeListener { _, isChecked -> onSwitchChanged(isChecked) }
            view.findViewById<View>(R.id.automaticContainer).setOnClickListener { switchView.toggle() }
            return view
        }

        private fun getRowView(index: Int, convertView: View?, parent: ViewGroup?): View {
            val view = (convertView ?: layoutInflater.inflate(R.layout.preference_folder_item, parent, false))
                as CheckedTextView
            val (rowValue, text) = rows[index]
            view.text = text
            view.isChecked = rowValue == FolderSelection.rowValue(folderPreference.value)
            view.setOnClickListener {
                folderPreference.select(FolderSelection.rowSelected(rowValue))
                dismiss()
            }
            return view
        }

        private fun onSwitchChanged(isChecked: Boolean) {
            val automaticValue = folderPreference.automaticValue ?: return
            val newValue = if (isChecked) {
                FolderSelection.switchedOn(automaticValue)
            } else {
                FolderSelection.switchedOff(folderPreference.value)
            }
            newValue?.let { folderPreference.select(it) }
            notifyDataSetChanged()
        }
    }
}
