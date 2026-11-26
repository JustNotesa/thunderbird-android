package com.fsck.k9.ui.settings.search

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import androidx.core.content.getSystemService
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.k9mail.core.ui.legacy.designsystem.atom.icon.Icons
import com.fsck.k9.ui.R
import com.google.android.material.textview.MaterialTextView
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.thunderbird.components.core.logging.Logger

/**
 * Shows the search field on the settings start screen and the settings it finds in place of the normal list.
 *
 * @param isFieldAtBottom Whether the field sits at the bottom of the content (one-handed mode) or at the top.
 * @param loadIndex Reads all searchable settings, off the main thread. Called when the user starts searching and
 *   again after [invalidateIndex]. While it runs, the results area shows a progress indicator.
 * @param onSearchActiveChanged Called when the search starts or ends, so the owner can let Back end it first.
 */
@Suppress("TooManyFunctions")
internal class SettingsSearchController(
    root: View,
    private val isFieldAtBottom: Boolean,
    private val scope: CoroutineScope,
    loadIndex: suspend () -> SettingsSearchIndex,
    private val openEntry: (SettingsSearchEntry) -> Unit,
    private val onMessage: (messageResId: Int) -> Unit,
    private val onSearchActiveChanged: (Boolean) -> Unit,
    private val logger: Logger,
) : SettingsSearchResultActions {
    private val context: Context = root.context
    private val settingsList: RecyclerView = root.findViewById(R.id.settings_list)
    private val resultsList: RecyclerView = root.findViewById(R.id.settings_search_results)
    private val info: View = root.findViewById(R.id.settings_search_info)
    private val breadcrumb: MaterialTextView = root.findViewById(R.id.settings_search_breadcrumb)
    private val count: MaterialTextView = root.findViewById(R.id.settings_search_count)
    private val notice: MaterialTextView = root.findViewById(R.id.settings_search_notice)
    private val progress: View = root.findViewById(R.id.settings_search_progress)
    private val emptyView: View = root.findViewById(R.id.settings_search_empty)
    private val fieldContainer: View = root.findViewById(R.id.settings_search_field_container)
    private val field: EditText = root.findViewById(R.id.settings_search_field)
    private val clearButton: ImageButton = root.findViewById(R.id.settings_search_clear)

    private val adapter = SettingsSearchResultsAdapter(this)
    private val cache = SettingsSearchIndexCache { loadIndexSafely(loadIndex) }
    private var loadJob: Job? = null
    private var wasSearching = false

    init {
        placeField(root as ViewGroup)

        resultsList.layoutManager = LinearLayoutManager(context).apply {
            // At the bottom, the first hit is closest to the thumb and the list grows upwards.
            reverseLayout = isFieldAtBottom
        }
        resultsList.adapter = adapter

        field.setCompoundDrawablesRelativeWithIntrinsicBounds(Icons.Outlined.Search, 0, 0, 0)
        clearButton.setImageResource(Icons.Outlined.Close)
        clearButton.setOnClickListener { field.text.clear() }
        field.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                true
            } else {
                false
            }
        }
        field.doAfterTextChanged { update() }
        update()
    }

    /** Forgets the settings that were read, e.g. because the values or the accounts changed. */
    fun invalidateIndex() {
        cache.invalidate()
        loadJob?.cancel()
        loadJob = null
        if (isSearching()) update()
    }

    /** Ends the search and shows the normal list again. */
    fun clearSearch() {
        field.text.clear()
    }

    private fun placeField(root: ViewGroup) {
        if (!isFieldAtBottom) {
            root.removeView(fieldContainer)
            root.addView(fieldContainer, 0)
        }
    }

    private fun isSearching() = field.text.isNotBlank()

    private fun update() {
        val query = field.text.toString()
        val searching = isSearching()
        clearButton.isVisible = query.isNotEmpty()
        settingsList.isGone = searching
        info.isVisible = searching
        if (searching != wasSearching) {
            wasSearching = searching
            onSearchActiveChanged(searching)
        }
        if (!searching) {
            progress.isGone = true
            resultsList.isGone = true
            emptyView.isGone = true
            return
        }

        val index = cache.peek()
        if (index == null) {
            showLoading()
            startLoading()
        } else {
            showResults(index, query)
        }
    }

    private fun showLoading() {
        progress.isVisible = true
        resultsList.isGone = true
        emptyView.isGone = true
        breadcrumb.isGone = true
        notice.isGone = true
        count.text = ""
    }

    private fun startLoading() {
        if (loadJob?.isActive == true) return

        loadJob = scope.launch {
            cache.get()
            loadJob = null
            if (isSearching()) update()
        }
    }

    private fun showResults(index: SettingsSearchIndex, query: String) {
        progress.isGone = true
        val results = SettingsSearcher.search(index.entries, query)
        val trimmed = query.trim()

        count.text = context.resources.getQuantityString(
            R.plurals.settings_search_result_count,
            results.size,
            results.size,
            trimmed,
        )
        val single = results.singleOrNull()
        breadcrumb.text = single?.let { SettingsSearcher.formatBreadcrumb(it.breadcrumb) }
        breadcrumb.isVisible = single != null && single.breadcrumb.isNotEmpty()
        notice.isVisible = index.failedScopes.isNotEmpty()
        notice.setText(R.string.settings_search_index_failed)

        // With one hit the breadcrumb is in the info area, with more every hit shows its own.
        adapter.submit(results, showBreadcrumb = results.size > 1)
        resultsList.isVisible = results.isNotEmpty()
        emptyView.isVisible = results.isEmpty()
        resultsList.scrollToPosition(0)
    }

    private suspend fun loadIndexSafely(loadIndex: suspend () -> SettingsSearchIndex): SettingsSearchIndex {
        return try {
            loadIndex()
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
            logger.error(tag = TAG, throwable = e) { "Could not read the settings for the search" }
            // Shown to the user by the notice below the hit count.
            SettingsSearchIndex(
                entries = emptyList(),
                failedScopes = listOf(SettingsScope.General),
            )
        }
    }

    private fun hideKeyboard() {
        context.getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(field.windowToken, 0)
    }

    override fun isOn(entry: SettingsSearchEntry): Boolean {
        val key = entry.highlightKey ?: return false
        return cache.peek()?.isOn(entry.scope, key) ?: false
    }

    override fun isEnabled(entry: SettingsSearchEntry): Boolean {
        val dependency = entry.dependencyKey ?: return true
        return cache.peek()?.isOn(entry.scope, dependency) ?: true
    }

    override fun onToggled(entry: SettingsSearchEntry, isOn: Boolean) {
        val key = entry.highlightKey ?: return
        try {
            checkNotNull(cache.peek()) { "The settings were not read" }.setOn(entry.scope, key, isOn)
        } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
            logger.error(tag = TAG, throwable = e) { "Could not change a setting from the search" }
            onMessage(R.string.settings_search_toggle_failed)
        }

        // Shows the value that is really stored (also after a failure) and the state of dependent settings.
        resultsList.post { adapter.refresh() }
    }

    override fun onOpen(entry: SettingsSearchEntry) {
        hideKeyboard()
        openEntry(entry)
    }

    private companion object {
        const val TAG = "SettingsSearchController"
    }
}
