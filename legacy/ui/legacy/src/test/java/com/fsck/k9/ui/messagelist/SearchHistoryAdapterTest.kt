package com.fsck.k9.ui.messagelist

import android.database.Cursor
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import net.thunderbird.core.android.testing.RobolectricTest
import org.junit.Test
import org.robolectric.RuntimeEnvironment

class SearchHistoryAdapterTest : RobolectricTest() {
    private val queries = mutableListOf("Invoice 2026", "holiday", "invoice reminder")
    private val testSubject = SearchHistoryAdapter(
        context = RuntimeEnvironment.getApplication(),
        queries = { queries },
        onRemoveQuery = { query -> queries.remove(query) },
    )

    @Test
    fun `should suggest all queries when nothing was typed`() {
        val result = testSubject.runQueryOnBackgroundThread(null).readQueries()

        assertThat(result).containsExactly("Invoice 2026", "holiday", "invoice reminder")
    }

    @Test
    fun `should only suggest queries containing the typed text ignoring case`() {
        val result = testSubject.runQueryOnBackgroundThread("INVOICE").readQueries()

        assertThat(result).containsExactly("Invoice 2026", "invoice reminder")
    }

    @Test
    fun `should suggest nothing when no query matches`() {
        val result = testSubject.runQueryOnBackgroundThread("unknown").readQueries()

        assertThat(result).isEmpty()
    }

    @Test
    fun `getQuery should return query at position`() {
        testSubject.changeCursor(testSubject.runQueryOnBackgroundThread(null))

        val result = testSubject.getQuery(1)

        assertThat(result).isEqualTo("holiday")
    }

    private fun Cursor.readQueries(): List<String> = use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(testSubject.convertToString(cursor).toString())
            }
        }
    }
}
