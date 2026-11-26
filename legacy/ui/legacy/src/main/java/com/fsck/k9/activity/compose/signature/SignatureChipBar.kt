package com.fsck.k9.activity.compose.signature

import android.view.View
import android.widget.TextView
import com.fsck.k9.ui.R
import com.google.android.material.button.MaterialButton

/**
 * The line above the signature of a message: a chip that names the signature and opens the choice, and for an HTML
 * signature a badge and a pencil.
 */
class SignatureChipBar(private val root: View) {
    private val chip: MaterialButton = root.findViewById(R.id.signature_chip)
    private val htmlBadge: TextView = root.findViewById(R.id.signature_chip_html_badge)
    private val editButton: View = root.findViewById(R.id.signature_chip_edit)

    fun setVisible(visible: Boolean) {
        root.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun bind(state: SignatureChipState, onChipClick: Runnable, onEditClick: Runnable) {
        chip.text = label(state)
        chip.setOnClickListener { onChipClick.run() }
        chip.contentDescription = chip.text

        val isHtml = state.isHtml && state.kind != SignatureChipState.Kind.None &&
            state.kind != SignatureChipState.Kind.Create
        htmlBadge.visibility = if (isHtml) View.VISIBLE else View.GONE
        editButton.visibility = if (isHtml) View.VISIBLE else View.GONE
        editButton.setOnClickListener { onEditClick.run() }
    }

    private fun label(state: SignatureChipState): String {
        val context = root.context
        val name = when (state.kind) {
            SignatureChipState.Kind.Create -> return context.getString(R.string.message_compose_signature_chip_create)
            SignatureChipState.Kind.Default -> context.getString(R.string.message_compose_choose_signature_default)
            SignatureChipState.Kind.Saved -> state.name.orEmpty()
            SignatureChipState.Kind.Custom -> context.getString(R.string.message_compose_signature_chip_custom)
            SignatureChipState.Kind.None -> context.getString(R.string.message_compose_choose_signature_none)
        }
        val shown = if (state.isEdited) {
            context.getString(R.string.message_compose_signature_chip_edited, name)
        } else {
            name
        }

        return context.getString(R.string.message_compose_signature_chip_dropdown, shown)
    }
}
