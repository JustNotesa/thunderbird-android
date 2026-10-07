package com.fsck.k9.ui.messagelist

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.provider.BaseColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cursoradapter.widget.CursorAdapter
import com.fsck.k9.ui.R

private const val COLUMN_QUERY = "query"
private const val COLUMN_INDEX_QUERY = 1

/**
 * Supplies the recent search queries as suggestions for a [androidx.appcompat.widget.SearchView].
 *
 * @param queries Returns the recent search queries, most recent first.
 * @param onRemoveQuery Called when the user asks to remove a query from the search history.
 */
class SearchHistoryAdapter(
    context: Context,
    private val queries: () -> List<String>,
    private val onRemoveQuery: (String) -> Unit,
) : CursorAdapter(context, null, 0) {
    private var constraint: CharSequence? = null

    override fun runQueryOnBackgroundThread(constraint: CharSequence?): Cursor {
        this.constraint = constraint
        return createCursor(constraint)
    }

    override fun convertToString(cursor: Cursor?): CharSequence {
        return cursor?.getString(COLUMN_INDEX_QUERY).orEmpty()
    }

    override fun newView(context: Context, cursor: Cursor, parent: ViewGroup): View {
        return LayoutInflater.from(context).inflate(R.layout.search_history_item, parent, false)
    }

    override fun bindView(view: View, context: Context, cursor: Cursor) {
        val query = cursor.getString(COLUMN_INDEX_QUERY)

        view.findViewById<TextView>(R.id.search_history_query).text = query
        view.findViewById<View>(R.id.search_history_remove).apply {
            contentDescription = context.getString(R.string.search_history_remove_content_description, query)
            setOnClickListener {
                onRemoveQuery(query)
                changeCursor(createCursor(constraint))
            }
        }
    }

    fun getQuery(position: Int): String? {
        val cursor = getItem(position) as? Cursor
        return cursor?.getString(COLUMN_INDEX_QUERY)
    }

    private fun createCursor(constraint: CharSequence?): Cursor {
        val filter = constraint?.toString()?.trim().orEmpty()
        val cursor = MatrixCursor(arrayOf(BaseColumns._ID, COLUMN_QUERY))
        queries()
            .filter { it.contains(filter, ignoreCase = true) }
            .forEachIndexed { index, query -> cursor.addRow(arrayOf<Any>(index.toLong(), query)) }

        return cursor
    }
}
