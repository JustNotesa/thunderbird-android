package com.fsck.k9.ui.settings

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.fsck.k9.ui.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textview.MaterialTextView
import com.fsck.k9.ui.base.R as BaseR

/**
 * Asks the user to confirm deleting multiple accounts.
 *
 * Instead of a regular confirm button the user needs to hold down a button for a certain amount of time. This makes it
 * harder to accidentally delete accounts. Users of a screen reader can't hold down a button, so they can use the
 * accessibility action of the button to confirm the deletion right away.
 */
class DeleteAccountsDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialogArguments = requireArguments()
        val accountNames = requireNotNull(dialogArguments.getStringArrayList(ARG_ACCOUNT_NAMES))
        val accountIds = requireNotNull(dialogArguments.getStringArrayList(ARG_ACCOUNT_IDS))
        val allAccountsSelected = dialogArguments.getBoolean(ARG_ALL_ACCOUNTS_SELECTED)

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_delete_accounts, null)
        val messageView: MaterialTextView = dialogView.findViewById(R.id.delete_accounts_message)
        val holdToConfirmButton: HoldToConfirmButton = dialogView.findViewById(R.id.delete_accounts_hold_to_confirm)

        messageView.text = buildMessage(accountNames, allAccountsSelected)
        holdToConfirmButton.setOnConfirmListener {
            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                bundleOf(RESULT_ACCOUNT_IDS to ArrayList(accountIds)),
            )
            dismiss()
        }

        val dialogTitle = resources.getQuantityString(
            R.plurals.settings_delete_accounts_title,
            accountIds.size,
            accountIds.size,
        )

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(dialogTitle)
            .setView(dialogView)
            .setNegativeButton(BaseR.string.cancel_action, null)
            .create()
    }

    private fun buildMessage(accountNames: List<String>, allAccountsSelected: Boolean): String {
        return buildString {
            append(accountNames.joinToString("\n"))
            append("\n\n")
            append(getString(R.string.settings_delete_accounts_message))

            if (allAccountsSelected) {
                append("\n\n")
                append(getString(R.string.settings_delete_all_accounts_message))
            }
        }
    }

    companion object {
        const val REQUEST_KEY = "delete_accounts_dialog_request"
        const val RESULT_ACCOUNT_IDS = "delete_accounts_dialog_result_account_ids"

        private const val ARG_ACCOUNT_NAMES = "account_names"
        private const val ARG_ACCOUNT_IDS = "account_ids"
        private const val ARG_ALL_ACCOUNTS_SELECTED = "all_accounts_selected"

        fun newInstance(
            accountIds: List<String>,
            accountNames: List<String>,
            allAccountsSelected: Boolean,
        ): DeleteAccountsDialogFragment {
            val fragment = DeleteAccountsDialogFragment()
            fragment.arguments = bundleOf(
                ARG_ACCOUNT_NAMES to ArrayList(accountNames),
                ARG_ACCOUNT_IDS to ArrayList(accountIds),
                ARG_ALL_ACCOUNTS_SELECTED to allAccountsSelected,
            )

            return fragment
        }
    }
}
