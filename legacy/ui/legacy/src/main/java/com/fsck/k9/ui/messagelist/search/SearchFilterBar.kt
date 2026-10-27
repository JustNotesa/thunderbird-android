package com.fsck.k9.ui.messagelist.search

import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import com.fsck.k9.ui.R
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText

/**
 * The filters of a search, displayed below the search field of the search results.
 *
 * The chips show the filters that are set. Tapping a chip opens the editor of that filter below the bar. Only one
 * editor is displayed at a time.
 */
class SearchFilterBar(view: View, private val onFiltersChanged: (SearchFilters) -> Unit) {
    private val panel: View = view.findViewById(R.id.search_filter_panel)
    private val unreadChip: Chip = view.findViewById(R.id.search_filter_unread)
    private val scopeEditor = ScopeFilterEditor(view)
    private val fromEditor = FromFilterEditor(view)
    private val periodEditor = PeriodFilterEditor(view)
    private val moreEditor = MoreFilterEditor(view)

    private val editors = listOf<FilterEditor>(scopeEditor, fromEditor, periodEditor, moreEditor)

    private var filters = SearchFilters()

    init {
        scopeEditor.onToggle = { toggle(scopeEditor) }
        fromEditor.onToggle = { toggle(fromEditor) }
        periodEditor.onToggle = { toggle(periodEditor) }
        moreEditor.onToggle = { toggle(moreEditor) }

        scopeEditor.onChanged = ::filtersChanged
        fromEditor.onChanged = ::filtersChanged
        periodEditor.onChanged = ::filtersChanged
        moreEditor.onChanged = ::filtersChanged

        unreadChip.setOnClickListener { filtersChanged(filters.copy(isUnreadOnly = !filters.isUnreadOnly)) }
    }

    /**
     * Display the filters that are set.
     */
    fun bind(filters: SearchFilters) {
        this.filters = filters

        unreadChip.isChecked = filters.isUnreadOnly
        scopeEditor.bind(filters)
        fromEditor.bind(filters)
        periodEditor.bind(filters)
        moreEditor.bind(filters)
    }

    /**
     * Open the editor of [editor]. It is closed when it is already open.
     */
    private fun toggle(editor: FilterEditor) {
        val isOpen = !editor.isOpen

        editors.forEach { if (it !== editor) it.close() }
        if (isOpen) editor.open() else editor.close()

        panel.isVisible = isOpen
    }

    private fun filtersChanged(filters: SearchFilters) {
        bind(filters)
        onFiltersChanged(filters)
    }
}

/**
 * One filter that can be edited below the filter bar.
 */
private interface FilterEditor {
    val isOpen: Boolean

    fun bind(filters: SearchFilters)

    fun open()

    fun close()
}

/**
 * Where the text of the search field is looked for.
 */
private class ScopeFilterEditor(view: View) : FilterEditor {
    private val context = view.context
    private val chip: Chip = view.findViewById(R.id.search_filter_scope)
    private val panel: View = view.findViewById(R.id.search_filter_scope_panel)
    private val headersChip: Chip = view.findViewById(R.id.search_filter_scope_headers)
    private val bodyChip: Chip = view.findViewById(R.id.search_filter_scope_body)
    private val everywhereChip: Chip = view.findViewById(R.id.search_filter_scope_everywhere)

    private var filters = SearchFilters()

    var onToggle: () -> Unit = {}
    var onChanged: (SearchFilters) -> Unit = {}

    init {
        // The scope is always set, so the chip stays checked.
        chip.setOnClickListener {
            chip.isChecked = true
            onToggle()
        }
        headersChip.setOnClickListener { selectScope(SearchFilters.TextScope.HEADERS) }
        bodyChip.setOnClickListener { selectScope(SearchFilters.TextScope.BODY) }
        everywhereChip.setOnClickListener { selectScope(SearchFilters.TextScope.EVERYWHERE) }
    }

    override fun bind(filters: SearchFilters) {
        this.filters = filters

        chip.text = context.getString(scopeLabel(filters.textScope))
        chip.isChecked = true

        headersChip.isChecked = filters.textScope == SearchFilters.TextScope.HEADERS
        bodyChip.isChecked = filters.textScope == SearchFilters.TextScope.BODY
        everywhereChip.isChecked = filters.textScope == SearchFilters.TextScope.EVERYWHERE
    }

    override fun open() {
        panel.isVisible = true
    }

    override fun close() {
        panel.isVisible = false
    }

    override val isOpen: Boolean
        get() = panel.isVisible

    private fun selectScope(textScope: SearchFilters.TextScope) {
        onChanged(filters.copy(textScope = textScope))
    }

    private fun scopeLabel(textScope: SearchFilters.TextScope): Int = when (textScope) {
        SearchFilters.TextScope.HEADERS -> R.string.search_filter_scope_headers
        SearchFilters.TextScope.BODY -> R.string.search_filter_scope_body
        SearchFilters.TextScope.EVERYWHERE -> R.string.search_filter_scope_everywhere
    }
}

/**
 * The sender of the messages.
 */
private class FromFilterEditor(view: View) : FilterEditor {
    private val context = view.context
    private val chip: Chip = view.findViewById(R.id.search_filter_from)
    private val editor = TextFilterEditor(
        panel = view.findViewById(R.id.search_filter_from_panel),
        editText = view.findViewById(R.id.search_filter_from_edit),
        removeButton = view.findViewById(R.id.search_filter_from_remove),
        doneButton = view.findViewById(R.id.search_filter_from_done),
        getValue = { it.from },
        withValue = { filters, value -> filters.copy(from = value) },
    )

    private var filters = SearchFilters()

    var onToggle: () -> Unit = {}
    var onChanged: (SearchFilters) -> Unit = {}

    init {
        chip.setOnClickListener { onToggle() }
        chip.setOnCloseIconClickListener { onChanged(filters.copy(from = null)) }
        editor.onChanged = { newFilters -> onChanged(newFilters) }
    }

    override fun bind(filters: SearchFilters) {
        this.filters = filters

        val from = filters.from
        chip.text = if (from == null) {
            context.getString(R.string.search_filter_from)
        } else {
            context.getString(R.string.search_filter_from_value, from)
        }
        chip.isCloseIconVisible = from != null

        editor.bind(filters)
    }

    override fun open() {
        editor.show()
    }

    override fun close() {
        editor.hide()
    }

    override val isOpen: Boolean
        get() = editor.isOpen
}

/**
 * The time the messages were received in.
 */
private class PeriodFilterEditor(view: View) : FilterEditor {
    private val context = view.context
    private val chip: Chip = view.findViewById(R.id.search_filter_period)
    private val panel: View = view.findViewById(R.id.search_filter_period_panel)
    private val todayChip: Chip = view.findViewById(R.id.search_filter_period_today)
    private val last7DaysChip: Chip = view.findViewById(R.id.search_filter_period_7_days)
    private val last30DaysChip: Chip = view.findViewById(R.id.search_filter_period_30_days)
    private val thisYearChip: Chip = view.findViewById(R.id.search_filter_period_this_year)

    private var filters = SearchFilters()

    var onToggle: () -> Unit = {}
    var onChanged: (SearchFilters) -> Unit = {}

    init {
        chip.setOnClickListener { onToggle() }
        chip.setOnCloseIconClickListener { onChanged(filters.copy(period = null)) }
        todayChip.setOnClickListener { selectPeriod(SearchPeriod.Today) }
        last7DaysChip.setOnClickListener { selectPeriod(SearchPeriod.Last7Days) }
        last30DaysChip.setOnClickListener { selectPeriod(SearchPeriod.Last30Days) }
        thisYearChip.setOnClickListener { selectPeriod(SearchPeriod.ThisYear) }
    }

    override fun bind(filters: SearchFilters) {
        this.filters = filters

        chip.text = periodText(filters.period)
        chip.isCloseIconVisible = filters.period != null

        todayChip.isChecked = filters.period == SearchPeriod.Today
        last7DaysChip.isChecked = filters.period == SearchPeriod.Last7Days
        last30DaysChip.isChecked = filters.period == SearchPeriod.Last30Days
        thisYearChip.isChecked = filters.period == SearchPeriod.ThisYear
    }

    override fun open() {
        panel.isVisible = true
    }

    override fun close() {
        panel.isVisible = false
    }

    override val isOpen: Boolean
        get() = panel.isVisible

    /**
     * Select the period, or clear it when it is already selected.
     */
    private fun selectPeriod(period: SearchPeriod) {
        val newPeriod = if (filters.period == period) null else period
        onChanged(filters.copy(period = newPeriod))
    }

    private fun periodText(period: SearchPeriod?): String {
        val label = period?.let { periodLabel(it) }
        return if (label == null) {
            context.getString(R.string.search_filter_period)
        } else {
            context.getString(R.string.search_filter_period_value, context.getString(label))
        }
    }

    private fun periodLabel(period: SearchPeriod): Int? = when (period) {
        SearchPeriod.Today -> R.string.search_filter_period_today
        SearchPeriod.Last7Days -> R.string.search_filter_period_7_days
        SearchPeriod.Last30Days -> R.string.search_filter_period_30_days
        SearchPeriod.ThisYear -> R.string.search_filter_period_this_year
        is SearchPeriod.Custom -> null
    }
}

/**
 * The filters that don't have a chip of their own.
 */
private class MoreFilterEditor(view: View) : FilterEditor {
    private val context = view.context
    private val chip: Chip = view.findViewById(R.id.search_filter_more)
    private val panel: View = view.findViewById(R.id.search_filter_more_panel)
    private val toChip: Chip = view.findViewById(R.id.search_filter_more_to)
    private val subjectChip: Chip = view.findViewById(R.id.search_filter_more_subject)
    private val starredChip: Chip = view.findViewById(R.id.search_filter_more_starred)
    private val answeredChip: Chip = view.findViewById(R.id.search_filter_more_answered)
    private val attachmentChip: Chip = view.findViewById(R.id.search_filter_more_attachment)
    private val largerThanChip: Chip = view.findViewById(R.id.search_filter_more_larger_than)
    private val sizePanel: View = view.findViewById(R.id.search_filter_size_choices)
    private val removeAllButton: View = view.findViewById(R.id.search_filter_remove_all)

    private val sizeChips: Map<Int, Chip> = mapOf(
        SIZE_1_MB to view.findViewById(R.id.search_filter_size_1),
        SIZE_5_MB to view.findViewById(R.id.search_filter_size_5),
        SIZE_10_MB to view.findViewById(R.id.search_filter_size_10),
        SIZE_25_MB to view.findViewById(R.id.search_filter_size_25),
    )

    private val toEditor = TextFilterEditor(
        panel = view.findViewById(R.id.search_filter_to_panel),
        editText = view.findViewById(R.id.search_filter_to_edit),
        removeButton = view.findViewById(R.id.search_filter_to_remove),
        doneButton = view.findViewById(R.id.search_filter_to_done),
        getValue = { it.to },
        withValue = { filters, value -> filters.copy(to = value) },
    )

    private val subjectEditor = TextFilterEditor(
        panel = view.findViewById(R.id.search_filter_subject_panel),
        editText = view.findViewById(R.id.search_filter_subject_edit),
        removeButton = view.findViewById(R.id.search_filter_subject_remove),
        doneButton = view.findViewById(R.id.search_filter_subject_done),
        getValue = { it.subject },
        withValue = { filters, value -> filters.copy(subject = value) },
    )

    private var filters = SearchFilters()
    private var isSizePanelVisible = false

    var onToggle: () -> Unit = {}
    var onChanged: (SearchFilters) -> Unit = {}

    init {
        chip.setOnClickListener { onToggle() }
        toChip.setOnClickListener {
            toEditor.toggle()
            toChip.isChecked = filters.to != null
        }
        subjectChip.setOnClickListener {
            subjectEditor.toggle()
            subjectChip.isChecked = filters.subject != null
        }
        starredChip.setOnClickListener { onChanged(filters.copy(isStarredOnly = !filters.isStarredOnly)) }
        answeredChip.setOnClickListener { onChanged(filters.copy(isAnsweredOnly = !filters.isAnsweredOnly)) }
        attachmentChip.setOnClickListener { onChanged(filters.copy(hasAttachment = !filters.hasAttachment)) }
        largerThanChip.setOnClickListener {
            isSizePanelVisible = !isSizePanelVisible
            updateSizePanel()
        }
        removeAllButton.setOnClickListener { onChanged(SearchFilters(textScope = filters.textScope)) }
        sizeChips.forEach { (megabytes, sizeChip) ->
            sizeChip.setOnClickListener { selectSize(megabytes) }
        }
        toEditor.onChanged = { newFilters -> onChanged(newFilters) }
        subjectEditor.onChanged = { newFilters -> onChanged(newFilters) }
    }

    override fun bind(filters: SearchFilters) {
        this.filters = filters

        chip.text = moreText(filters)
        toChip.isChecked = filters.to != null
        subjectChip.isChecked = filters.subject != null
        starredChip.isChecked = filters.isStarredOnly
        answeredChip.isChecked = filters.isAnsweredOnly
        attachmentChip.isChecked = filters.hasAttachment

        largerThanChip.text = largerThanText(filters.largerThanBytes)
        sizeChips.forEach { (megabytes, sizeChip) ->
            sizeChip.isChecked = filters.largerThanBytes == megabytes * ONE_MEGABYTE
        }
        if (filters.largerThanBytes != null) {
            isSizePanelVisible = true
        }
        updateSizePanel()

        toEditor.bind(filters)
        subjectEditor.bind(filters)
    }

    override fun open() {
        panel.isVisible = true
        chip.isChecked = true
    }

    override fun close() {
        panel.isVisible = false
        chip.isChecked = false
        toEditor.hide()
        subjectEditor.hide()
    }

    override val isOpen: Boolean
        get() = panel.isVisible

    /**
     * Select the size of the messages, or clear it when it is already selected.
     */
    private fun selectSize(megabytes: Int) {
        val size = megabytes * ONE_MEGABYTE
        val newSize = if (filters.largerThanBytes == size) null else size
        onChanged(filters.copy(largerThanBytes = newSize))
    }

    private fun updateSizePanel() {
        sizePanel.isVisible = isSizePanelVisible
        largerThanChip.isChecked = isSizePanelVisible
    }

    private fun largerThanText(size: Long?): String {
        val label = size?.let { sizeChips[(it / ONE_MEGABYTE).toInt()]?.text }
        return if (label == null) {
            context.getString(R.string.search_filter_larger_than)
        } else {
            context.getString(R.string.search_filter_larger_than_value, label)
        }
    }

    /**
     * The number of filters that are set, but don't have a chip of their own.
     */
    private fun moreText(filters: SearchFilters): String {
        val filterCount = listOf(
            filters.to != null,
            filters.subject != null,
            filters.isStarredOnly,
            filters.isAnsweredOnly,
            filters.hasAttachment,
            filters.largerThanBytes != null,
        ).count { it }

        return if (filterCount == 0) {
            context.getString(R.string.search_filter_more)
        } else {
            context.getString(R.string.search_filter_more_count, filterCount)
        }
    }

    private companion object {
        const val ONE_MEGABYTE = 1024L * 1024L
        const val SIZE_1_MB = 1
        const val SIZE_5_MB = 5
        const val SIZE_10_MB = 10
        const val SIZE_25_MB = 25
    }
}

/**
 * A filter whose value is a text the user types in.
 */
private class TextFilterEditor(
    private val panel: View,
    private val editText: TextInputEditText,
    removeButton: View,
    doneButton: View,
    private val getValue: (SearchFilters) -> String?,
    private val withValue: (SearchFilters, String?) -> SearchFilters,
) {
    private var filters = SearchFilters()

    var onChanged: (SearchFilters) -> Unit = {}

    val isOpen: Boolean
        get() = panel.isVisible

    init {
        removeButton.setOnClickListener {
            onChanged(withValue(filters, null))
            hide()
        }
        doneButton.setOnClickListener { applyValue() }
        editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                applyValue()
                true
            } else {
                false
            }
        }
    }

    fun bind(filters: SearchFilters) {
        this.filters = filters

        val value = getValue(filters).orEmpty()
        if (editText.text.toString() != value) {
            editText.setText(value)
        }
    }

    /**
     * The text field gets the focus and the keyboard, instead of the search field above the filters.
     */
    fun show() {
        panel.isVisible = true
        editText.requestFocus()
        ViewCompat.getWindowInsetsController(editText)?.show(WindowInsetsCompat.Type.ime())
    }

    fun hide() {
        if (editText.hasFocus()) {
            ViewCompat.getWindowInsetsController(editText)?.hide(WindowInsetsCompat.Type.ime())
            // The filter bar takes the focus, otherwise it would go to the search field and open the keyboard again
            (panel.rootView.findViewById<View>(R.id.search_filter_bar) ?: panel).requestFocus()
        }
        panel.isVisible = false
    }

    fun toggle() {
        if (isOpen) hide() else show()
    }

    /**
     * An empty text removes the filter.
     */
    private fun applyValue() {
        val value = editText.text.toString().trim().takeIf { it.isNotEmpty() }
        onChanged(withValue(filters, value))
        hide()
    }
}
