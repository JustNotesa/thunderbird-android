package com.fsck.k9.ui.settings

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import net.thunderbird.feature.account.AccountIdFactory
import org.junit.Test

class AccountSelectionTest {
    private val firstAccountId = AccountIdFactory.create()
    private val secondAccountId = AccountIdFactory.create()
    private val accountSelection = AccountSelection()

    @Test
    fun `toggling an account adds it to the selection`() {
        accountSelection.toggle(firstAccountId)

        assertThat(accountSelection.selectedIds).isEqualTo(setOf(firstAccountId))
        assertThat(accountSelection.size).isEqualTo(1)
    }

    @Test
    fun `toggling a selected account removes it from the selection`() {
        accountSelection.toggle(firstAccountId)

        accountSelection.toggle(firstAccountId)

        assertThat(accountSelection.selectedIds).isEmpty()
        assertThat(accountSelection.size).isEqualTo(0)
    }

    @Test
    fun `selectAll selects all given accounts`() {
        accountSelection.selectAll(listOf(firstAccountId, secondAccountId))

        assertThat(accountSelection.selectedIds).isEqualTo(setOf(firstAccountId, secondAccountId))
    }

    @Test
    fun `selectAll replaces a previous selection`() {
        accountSelection.toggle(firstAccountId)

        accountSelection.selectAll(listOf(secondAccountId))

        assertThat(accountSelection.selectedIds).isEqualTo(setOf(secondAccountId))
    }

    @Test
    fun `clear removes all selected accounts`() {
        accountSelection.selectAll(listOf(firstAccountId, secondAccountId))

        accountSelection.clear()

        assertThat(accountSelection.selectedIds).isEmpty()
    }

    @Test
    fun `isSelected tells whether an account is selected`() {
        accountSelection.toggle(firstAccountId)

        assertThat(accountSelection.isSelected(firstAccountId)).isTrue()
        assertThat(accountSelection.isSelected(secondAccountId)).isFalse()
    }

    @Test
    fun `isAllSelected is true when all accounts are selected`() {
        accountSelection.selectAll(listOf(firstAccountId, secondAccountId))

        assertThat(accountSelection.isAllSelected(2)).isTrue()
    }

    @Test
    fun `isAllSelected is false when not all accounts are selected`() {
        accountSelection.toggle(firstAccountId)

        assertThat(accountSelection.isAllSelected(2)).isFalse()
    }

    @Test
    fun `isAllSelected is false when there are no accounts`() {
        assertThat(accountSelection.isAllSelected(0)).isFalse()
    }
}
