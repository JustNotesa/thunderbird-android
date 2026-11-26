package com.fsck.k9.ui.settings.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.fsck.k9.ui.R
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textview.MaterialTextView

/** What the result list needs from its owner. */
internal interface SettingsSearchResultActions {
    fun isOn(entry: SettingsSearchEntry): Boolean
    fun isEnabled(entry: SettingsSearchEntry): Boolean

    /** Called when the user flips the switch of [entry]. */
    fun onToggled(entry: SettingsSearchEntry, isOn: Boolean)

    /** Called when the user taps a result that is not a switch. */
    fun onOpen(entry: SettingsSearchEntry)
}

internal class SettingsSearchResultsAdapter(
    private val actions: SettingsSearchResultActions,
) : RecyclerView.Adapter<SettingsSearchResultsAdapter.ViewHolder>() {
    private var entries: List<SettingsSearchEntry> = emptyList()
    private var showBreadcrumb = false

    init {
        setHasStableIds(true)
    }

    fun submit(entries: List<SettingsSearchEntry>, showBreadcrumb: Boolean) {
        this.entries = entries
        this.showBreadcrumb = showBreadcrumb
        notifyDataSetChanged()
    }

    /** Reads the state of all switches again, e.g. after one of them changed what others depend on. */
    fun refresh() = notifyItemRangeChanged(0, itemCount)

    override fun getItemCount() = entries.size

    override fun getItemId(position: Int) = entries[position].id.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.settings_search_result_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(entries[position])
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val breadcrumb: MaterialTextView = view.findViewById(R.id.result_breadcrumb)
        private val title: MaterialTextView = view.findViewById(R.id.result_title)
        private val summary: MaterialTextView = view.findViewById(R.id.result_summary)
        private val switch: MaterialSwitch = view.findViewById(R.id.result_switch)

        fun bind(entry: SettingsSearchEntry) {
            val isToggle = entry.isToggle
            val isOn = if (isToggle) actions.isOn(entry) else null
            val enabled = !isToggle || actions.isEnabled(entry)

            breadcrumb.text = SettingsSearcher.formatBreadcrumb(entry.breadcrumb)
            breadcrumb.isVisible = showBreadcrumb && entry.breadcrumb.isNotEmpty()
            title.text = entry.title
            val summaryText = entry.summaryFor(isOn)
            summary.text = summaryText
            summary.isVisible = !summaryText.isNullOrBlank()

            switch.setOnCheckedChangeListener(null)
            switch.isVisible = isToggle
            switch.isChecked = isOn == true
            switch.isEnabled = enabled
            title.isEnabled = enabled
            summary.isEnabled = enabled

            if (isToggle) {
                switch.setOnCheckedChangeListener { _, checked -> actions.onToggled(entry, checked) }
                itemView.setOnClickListener { if (switch.isEnabled) switch.toggle() }
            } else {
                itemView.setOnClickListener { actions.onOpen(entry) }
            }
        }
    }
}
