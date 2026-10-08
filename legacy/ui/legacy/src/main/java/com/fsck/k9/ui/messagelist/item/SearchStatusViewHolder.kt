package com.fsck.k9.ui.messagelist.item

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import app.k9mail.core.ui.legacy.designsystem.atom.icon.Icons
import com.fsck.k9.ui.R
import com.fsck.k9.ui.messagelist.ServerSearchStatus
import com.fsck.k9.ui.messagelist.ServerSearchStatus.Result
import com.fsck.k9.ui.resolveColorAttribute
import androidx.appcompat.R as AppCompatR
import com.google.android.material.R as MaterialR

/**
 * Displays the state of the server search as the first row of the search results.
 *
 * The row always has the same height, no matter what it displays. And because it is part of the list, it only moves
 * when the user scrolls the list. So the messages below never move on their own.
 */
class SearchStatusViewHolder(view: View) : MessageListViewHolder(view) {
    private val spinner: View = view.findViewById(R.id.search_status_spinner)
    private val icon: ImageView = view.findViewById(R.id.search_status_icon)
    private val title: TextView = view.findViewById(R.id.search_status_text)
    private val details: ConstraintLayout = view.findViewById(R.id.search_status_details)
    private val progressGroup: View = view.findViewById(R.id.search_status_progress_group)
    private val progress: ProgressBar = view.findViewById(R.id.search_status_progress)
    private val folder: TextView = view.findViewById(R.id.search_status_folder)
    private val detail: TextView = view.findViewById(R.id.search_status_detail)
    private val message: TextView = view.findViewById(R.id.search_status_message)
    private val contentsButton: View = view.findViewById(R.id.search_contents_button)

    init {
        // Reserve the space for the button and one line of text above it.
        val unspecified = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        contentsButton.measure(unspecified, unspecified)
        details.minHeight = contentsButton.measuredHeight + message.lineHeight + message.paddingBottom

        // Display as many lines of the message as fit into the space that is available.
        message.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
            val maxLines = ((bottom - top - message.paddingBottom) / message.lineHeight).coerceAtLeast(1)
            if (message.maxLines != maxLines) {
                message.post { message.maxLines = maxLines }
            }
        }
    }

    fun bind(status: ServerSearchStatus) {
        title.text = status.title

        when (status) {
            is ServerSearchStatus.Running -> bindRunning(status)
            is ServerSearchStatus.Ended -> bindEnded(status)
        }
    }

    private fun bindRunning(status: ServerSearchStatus.Running) {
        spinner.isVisible = true
        icon.isVisible = false

        progressGroup.isInvisible = false
        progress.isInvisible = status.progress == null
        status.progress?.let { searchProgress ->
            progress.max = searchProgress.folderCount
            progress.progress = searchProgress.folderIndex - 1
        }
        folder.text = status.progress?.folderLabel
        detail.text = status.activity ?: status.detail

        message.isInvisible = true
        contentsButton.isVisible = false
    }

    private fun bindEnded(status: ServerSearchStatus.Ended) {
        spinner.isVisible = false
        icon.isVisible = true
        when (status.result) {
            Result.COMPLETE -> setIcon(Icons.Outlined.Check, AppCompatR.attr.colorPrimary)
            Result.STOPPED -> setIcon(Icons.Outlined.Info, MaterialR.attr.colorOnSurfaceVariant)
            Result.PROBLEM -> setIcon(Icons.Outlined.Error, AppCompatR.attr.colorError)
        }

        progressGroup.isInvisible = true

        message.text = status.message
        message.isInvisible = status.message.isNullOrBlank()
        contentsButton.isVisible = status.isMessageContentsSearchOffered
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
        ): SearchStatusViewHolder {
            val view = layoutInflater.inflate(R.layout.message_list_item_search_status, parent, false)
            view.findViewById<View>(R.id.search_contents_button)
                .setOnClickListener(onSearchMessageContentsClickListener)
            return SearchStatusViewHolder(view)
        }
    }
}
