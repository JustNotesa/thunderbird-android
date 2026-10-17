package com.fsck.k9.ui.compose.richtext

import android.content.Context
import android.view.LayoutInflater
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import com.fsck.k9.ui.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import net.thunderbird.feature.mail.message.composer.richtext.LinkUrlPolicy

/**
 * Asks for the text and the address of a link.
 *
 * With selected text the text is given and can't be changed; inside a link both are filled in, and the link can be
 * removed. Only addresses [LinkUrlPolicy] accepts can be confirmed; anything else shows why next to the field.
 */
internal class LinkDialog(private val context: Context) {

    /**
     * @param text the text of the link, `null` when the user is to type it
     * @param isTextEditable whether the user may change [text]
     * @param url the address of the link being edited, `null` for a new link
     * @param onConfirm gets the text and the normalized address
     * @param onRemove removes the link being edited; only offered when [url] is set
     */
    fun show(
        text: String?,
        isTextEditable: Boolean,
        url: String?,
        onConfirm: (text: String, url: String) -> Unit,
        onRemove: () -> Unit,
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.message_compose_link_dialog, null)
        val textField = view.findViewById<EditText>(R.id.link_text)
        val addressLayout = view.findViewById<TextInputLayout>(R.id.link_address_layout)
        val addressField = view.findViewById<EditText>(R.id.link_address)
        textField.setText(text.orEmpty())
        textField.isEnabled = isTextEditable
        addressField.setText(url.orEmpty())

        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.message_compose_link_dialog_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
        if (url != null) {
            builder.setNeutralButton(R.string.message_compose_link_dialog_remove) { _, _ -> onRemove() }
        }

        val dialog = builder.create()
        dialog.setOnShowListener {
            // Checked when OK is pressed, so that the dialog stays open while the address is not acceptable.
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val normalized = LinkUrlPolicy.normalize(addressField.text.toString())
                val linkText = textField.text.toString().ifBlank { normalized.orEmpty() }
                if (normalized == null) {
                    addressLayout.error = context.getString(R.string.message_compose_link_dialog_invalid)
                } else {
                    onConfirm(linkText, normalized)
                    dialog.dismiss()
                }
            }
        }
        addressField.doAfterTextChanged { addressLayout.error = null }
        dialog.show()
        (if (isTextEditable && text.isNullOrEmpty()) textField else addressField).requestFocus()
    }
}
