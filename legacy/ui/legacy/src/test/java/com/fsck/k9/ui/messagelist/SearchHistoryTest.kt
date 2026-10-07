package com.fsck.k9.ui.messagelist

import android.content.Context
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import net.thunderbird.core.android.testing.RobolectricTest
import org.junit.Test
import org.robolectric.RuntimeEnvironment

class SearchHistoryTest : RobolectricTest() {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val testSubject = SearchHistory(context)

    @Test
    fun `getQueries should return empty list when nothing was stored`() {
        val result = testSubject.getQueries()

        assertThat(result).isEmpty()
    }

    @Test
    fun `add should put most recent query first`() {
        testSubject.add("first")
        testSubject.add("second")

        val result = testSubject.getQueries()

        assertThat(result).containsExactly("second", "first")
    }

    @Test
    fun `add should move repeated query to the front instead of storing it twice`() {
        testSubject.add("invoice")
        testSubject.add("other")

        testSubject.add("Invoice")

        assertThat(testSubject.getQueries()).containsExactly("Invoice", "other")
    }

    @Test
    fun `add should ignore blank query`() {
        testSubject.add("   ")

        assertThat(testSubject.getQueries()).isEmpty()
    }

    @Test
    fun `add should trim query`() {
        testSubject.add("  invoice  ")

        assertThat(testSubject.getQueries()).containsExactly("invoice")
    }

    @Test
    fun `add should drop oldest queries when limit is exceeded`() {
        repeat(10) { index -> testSubject.add("query $index") }

        val result = testSubject.getQueries()

        assertThat(result).hasSize(8)
        assertThat(result.first()).isEqualTo("query 9")
        assertThat(result.last()).isEqualTo("query 2")
    }

    @Test
    fun `remove should delete only the given query`() {
        testSubject.add("first")
        testSubject.add("second")

        testSubject.remove("first")

        assertThat(testSubject.getQueries()).containsExactly("second")
    }

    @Test
    fun `clear should delete all queries`() {
        testSubject.add("first")
        testSubject.add("second")

        testSubject.clear()

        assertThat(testSubject.getQueries()).isEmpty()
    }

    @Test
    fun `queries should survive creating a new instance`() {
        testSubject.add("first")

        val result = SearchHistory(context).getQueries()

        assertThat(result).containsExactly("first")
    }
}
