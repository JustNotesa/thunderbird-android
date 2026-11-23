package com.fsck.k9.ui.messagelist.item

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import app.k9mail.core.ui.legacy.designsystem.atom.icon.Icons
import com.fsck.k9.ui.R
import com.fsck.k9.ui.messagelist.SearchScope
import com.fsck.k9.ui.messagelist.SearchStatusPanelState
import com.fsck.k9.ui.messagelist.ServerSearchStatus
import com.fsck.k9.ui.messagelist.ServerSearchStatus.Result
import com.fsck.k9.ui.resolveColorAttribute
import com.google.android.material.chip.Chip
import androidx.appcompat.R as AppCompatR
import com.google.android.material.R as MaterialR

/**
 * Displays the state of the server search in the bottom area (one-handed mode): one line of text, and below it the
 * buttons for searching the contents and the attachments of the messages, side by side.
 */
class SearchStatusPanel(
    val view: View,
    onScopeSelected: (SearchScope) -> Unit,
) {
    private val scopeGroup: View = view.findViewById(R.id.search_scope)
    private val scopeFolder: Chip = view.findViewById(R.id.search_scope_folder)
    private val scopeEverywhere: Chip = view.findViewById(R.id.search_scope_everywhere)
    private val spinner: View = view.findViewById(R.id.search_status_spinner)
    private val icon: ImageView = view.findViewById(R.id.search_status_icon)
    private val text: TextView = view.findViewById(R.id.search_status_text)
    private val buttons: View = view.findViewById(R.id.search_status_buttons)
    private val contentsButton: View = view.findViewById(R.id.search_contents_button)
    private val attachmentsButton: View = view.findViewById(R.id.search_attachments_button)

    init {
        scopeFolder.setOnClickListener { onScopeSelected(SearchScope.FOLDER) }
        scopeEverywhere.setOnClickListener { onScopeSelected(SearchScope.EVERYWHERE) }
    }

    fun bind(status: ServerSearchStatus, scope: SearchScope?) {
        view.isVisible = true

        scopeGroup.isVisible = scope != null
        scopeFolder.isChecked = scope == SearchScope.FOLDER
        scopeEverywhere.isChecked = scope == SearchScope.EVERYWHERE

        val state = SearchStatusPanelState.of(status)
        text.text = state.text
        spinner.isVisible = state.isRunning
        icon.isVisible = !state.isRunning
        when (state.result) {
            Result.COMPLETE -> setIcon(Icons.Outlined.Check, AppCompatR.attr.colorPrimary)
            Result.STOPPED -> setIcon(Icons.Outlined.Info, MaterialR.attr.colorOnSurfaceVariant)
            Result.PROBLEM -> setIcon(Icons.Outlined.Error, AppCompatR.attr.colorError)
            null -> Unit
        }

        buttons.isVisible = state.hasButtons
        contentsButton.isVisible = state.isContentsButtonVisible
        attachmentsButton.isVisible = state.isAttachmentsButtonVisible
    }

    fun hide() {
        view.isVisible = false
    }

    private fun setIcon(drawable: Int, colorAttribute: Int) {
        icon.setImageResource(drawable)
        val color = icon.context.theme.resolveColorAttribute(colorAttribute)
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(color))
    }

    companion object {
        fun create(
            layoutInflater: LayoutInflater,
            parent: ViewGroup,
            onSearchMessageContentsClickListener: View.OnClickListener,
            onSearchAttachmentsClickListener: View.OnClickListener,
            onScopeSelected: (SearchScope) -> Unit,
        ): SearchStatusPanel {
            val view = layoutInflater.inflate(R.layout.message_list_search_panel, parent, false)
            view.findViewById<View>(R.id.search_contents_button)
                .setOnClickListener(onSearchMessageContentsClickListener)
            view.findViewById<View>(R.id.search_attachments_button)
                .setOnClickListener(onSearchAttachmentsClickListener)
            return SearchStatusPanel(view, onScopeSelected)
        }
    }
}
