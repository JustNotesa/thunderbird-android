package net.thunderbird.core.common.mail

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import kotlin.test.Test

class SearchQueryTest {
    @Test
    fun `single word`() {
        val result = splitSearchQuery("invoice")

        assertThat(result).containsExactly("invoice")
    }

    @Test
    fun `words separated by whitespace are separate terms`() {
        val result = splitSearchQuery("  alice   invoice\treminder ")

        assertThat(result).containsExactly("alice", "invoice", "reminder")
    }

    @Test
    fun `quoted text is kept together`() {
        val result = splitSearchQuery("\"alice example\" invoice")

        assertThat(result).containsExactly("alice example", "invoice")
    }

    @Test
    fun `unbalanced quote is ignored`() {
        val result = splitSearchQuery("\"alice invoice")

        assertThat(result).containsExactly("alice", "invoice")
    }

    @Test
    fun `blank query has no terms`() {
        val result = splitSearchQuery("   ")

        assertThat(result).isEmpty()
    }

    @Test
    fun `empty quotes are ignored`() {
        val result = splitSearchQuery("\"\" invoice")

        assertThat(result).containsExactly("invoice")
    }
}
