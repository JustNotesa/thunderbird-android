package com.fsck.k9.ui.settings

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test

class AccountsSectionCollapseTest {
    @Test
    fun `five accounts are not collapsible`() {
        val testSubject = collapse(accountCount = 5)

        assertThat(testSubject).isEqualTo(result(5, hasPeek = false, isCollapsible = false, isExpanded = true))
    }

    @Test
    fun `six accounts collapsed show three plus one peek`() {
        val testSubject = collapse(accountCount = 6)

        assertThat(testSubject).isEqualTo(result(3, hasPeek = true, isCollapsible = true, isExpanded = false))
    }

    @Test
    fun `expanded shows all accounts without peek`() {
        val testSubject = collapse(accountCount = 8, expanded = true)

        assertThat(testSubject).isEqualTo(result(8, hasPeek = false, isCollapsible = true, isExpanded = true))
    }

    @Test
    fun `selection mode shows all accounts without peek`() {
        val testSubject = collapse(accountCount = 8, selection = true)

        assertThat(testSubject).isEqualTo(result(8, hasPeek = false, isCollapsible = true, isExpanded = true))
    }

    private fun collapse(accountCount: Int, expanded: Boolean = false, selection: Boolean = false) =
        accountsSectionCollapse(accountCount, isExpandedByUser = expanded, isSelectionMode = selection)

    private fun result(visibleCount: Int, hasPeek: Boolean, isCollapsible: Boolean, isExpanded: Boolean) =
        AccountsSectionCollapse(visibleCount, hasPeek, isCollapsible, isExpanded)
}
