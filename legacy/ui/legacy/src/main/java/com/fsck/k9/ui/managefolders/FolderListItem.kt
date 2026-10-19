package com.fsck.k9.ui.managefolders

import androidx.core.view.isVisible
import androidx.core.view.updatePaddingRelative
import app.k9mail.core.ui.legacy.designsystem.atom.icon.Icons
import android.view.View
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.fsck.k9.ui.R
import com.google.android.material.textview.MaterialTextView
import com.mikepenz.fastadapter.items.AbstractItem

/**
 * @param depth How many folders this folder is nested in. It is indented accordingly.
 * @param isExpanded Whether the subfolders are displayed, or `null` if the folder has none.
 */
class FolderListItem(
    val folderId: Long,
    private val folderIconResource: Int,
    val displayName: String,
    private val depth: Int = 0,
    private val isExpanded: Boolean? = null,
    private val onExpandClick: (Long) -> Unit = {},
) : AbstractItem<FolderListViewHolder>() {
    override var identifier: Long = folderId
    override val layoutRes = R.layout.folder_list_item
    override val type: Int = 1

    override fun getViewHolder(v: View) = FolderListViewHolder(v)

    override fun bindView(holder: FolderListViewHolder, payloads: List<Any>) {
        super.bindView(holder, payloads)
        holder.icon.setImageResource(folderIconResource)
        holder.name.text = displayName

        val indentation = holder.itemView.resources.getDimensionPixelSize(R.dimen.folderListSubfolderIndentation)
        holder.itemView.updatePaddingRelative(start = depth * indentation)

        holder.expand.isVisible = isExpanded != null
        holder.expand.setImageResource(if (isExpanded == true) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore)
        holder.expand.contentDescription = holder.itemView.context.getString(
            if (isExpanded == true) R.string.folder_list_collapse_action else R.string.folder_list_expand_action,
        )
        holder.expand.setOnClickListener { onExpandClick(folderId) }
    }

    override fun unbindView(holder: FolderListViewHolder) {
        super.unbindView(holder)
        holder.expand.setOnClickListener(null)
    }
}

class FolderListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val icon: ImageView = itemView.findViewById(R.id.folder_icon)
    val name: MaterialTextView = itemView.findViewById(R.id.folder_name)
    val expand: ImageView = itemView.findViewById(R.id.folder_expand)
}
