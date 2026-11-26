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
 * The server search with one-handed mode. What is only read, the title of the scope and the state of the search, is
 * displayed in [infoView] at the top of the screen. What is operated, the choice of the scope and the buttons for
 * searching the contents and the attachments of the messages, is in [view] in the bottom area.
 */
class SearchStatusPanel(
    val view: View,
    val infoView: View,
    onScopeSelected: (SearchScope) -> Unit,
) {
    private val scopeTitle: TextView = infoView.findViewById(R.id.search_info_scope)
    private val scopeGroup: View = view.findViewById(R.id.search_scope)
    private val scopeFolder: Chip = view.findViewById(R.id.search_scope_folder)
    private val scopeEverywhere: Chip = view.findViewById(R.id.search_scope_everywhere)
    private val spinner: View = infoView.findViewById(R.id.search_status_spinner)
    private val icon: ImageView = infoView.findViewById(R.id.search_status_icon)
    private val text: TextView = infoView.findViewById(R.id.search_status_text)
    private val buttons: View = view.findViewById(R.id.search_status_buttons)
    private val contentsButton: View = view.findViewById(R.id.search_contents_button)
    private val attachmentsButton: View = view.findViewById(R.id.search_attachments_button)

    init {
        scopeFolder.setOnClickListener { onScopeSelected(SearchScope.FOLDER) }
        scopeEverywhere.setOnClickListener { onScopeSelected(SearchScope.EVERYWHERE) }
    }

    /**
     * @param scopeTitle Where the search looks for messages, e.g. the name of the folder, or null to not display it.
     */
    fun bind(status: ServerSearchStatus, scope: SearchScope?, scopeTitle: String? = null) {
        infoView.isVisible = true

        this.scopeTitle.text = scopeTitle
        this.scopeTitle.isVisible = scopeTitle != null

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

        // Nothing to operate: the bottom area stays free.
        view.isVisible = scope != null || state.hasButtons
        buttons.isVisible = state.hasButtons
        contentsButton.isVisible = state.isContentsButtonVisible
        attachmentsButton.isVisible = state.isAttachmentsButtonVisible
    }

    fun hide() {
        view.isVisible = false
        infoView.isVisible = false
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
            infoView: View,
            onSearchMessageContentsClickListener: View.OnClickListener,
            onSearchAttachmentsClickListener: View.OnClickListener,
            onScopeSelected: (SearchScope) -> Unit,
        ): SearchStatusPanel {
            val view = layoutInflater.inflate(R.layout.message_list_search_panel, parent, false)
            view.findViewById<View>(R.id.search_contents_button)
                .setOnClickListener(onSearchMessageContentsClickListener)
            view.findViewById<View>(R.id.search_attachments_button)
                .setOnClickListener(onSearchAttachmentsClickListener)
            return SearchStatusPanel(view, infoView, onScopeSelected)
        }
    }
}
