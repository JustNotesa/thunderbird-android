package com.fsck.k9.ui.managefolders

import android.content.Context
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.view.updateLayoutParams
import com.fsck.k9.ui.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.fsck.k9.ui.base.R as BaseR

private const val DIALOG_CONTENT_MARGIN_DP = 24

/**
 * Asks for the name of a folder that is to be created.
 */
internal object FolderNameDialog {
    /**
     * @param pathDelimiter Separates the names of nested folders on the server, so it can't be part of a name.
     */
    fun show(context: Context, @StringRes title: Int, pathDelimiter: String, onNameEntered: (String) -> Unit) {
        val nameView = EditText(context).apply {
            setHint(R.string.folder_name_dialog_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine()
        }
        val container = FrameLayout(context).apply {
            addView(nameView)
        }
        val margin = (DIALOG_CONTENT_MARGIN_DP * context.resources.displayMetrics.density).toInt()
        nameView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            marginStart = margin
            marginEnd = margin
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(container)
            .setPositiveButton(R.string.folder_create_confirm_action) { _, _ ->
                val name = nameView.text.toString().trim()
                if (name.isEmpty() || (pathDelimiter.isNotEmpty() && pathDelimiter in name)) {
                    val message = context.getString(R.string.folder_name_invalid, pathDelimiter)
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                } else {
                    onNameEntered(name)
                }
            }
            .setNegativeButton(BaseR.string.cancel_action, null)
            .show()

        nameView.requestFocus()
    }
}
