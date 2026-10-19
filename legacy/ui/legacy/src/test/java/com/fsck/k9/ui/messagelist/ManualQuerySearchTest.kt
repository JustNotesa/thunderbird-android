package com.fsck.k9.ui.messagelist

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import net.thunderbird.feature.search.legacy.SearchConditionTreeNode
import net.thunderbird.feature.search.legacy.SearchConditionTreeNode.Operator
import net.thunderbird.feature.search.legacy.api.MessageSearchField
import org.junit.Test

class ManualQuerySearchTest {
    @Test
    fun `single word should be searched in all standard fields`() {
        val testSubject = createManualQuerySearch("invoice")

        val conditions = testSubject.conditions
        assertThat(testSubject.isManualSearch).isTrue()
        assertThat(conditions.operator).isEqualTo(Operator.OR)
        assertThat(conditions.values()).containsOnly("invoice")
        assertThat(conditions.fields()).containsExactlyInAnyOrder(
            MessageSearchField.SENDER,
            MessageSearchField.TO,
            MessageSearchField.CC,
            MessageSearchField.BCC,
            MessageSearchField.SUBJECT,
            MessageSearchField.MESSAGE_CONTENTS,
        )
    }

    @Test
    fun `every word has to match but can be found in a different field`() {
        val testSubject = createManualQuerySearch("alice invoice")

        val conditions = testSubject.conditions
        assertThat(conditions.operator).isEqualTo(Operator.AND)
        val firstWord = conditions.left
        val secondWord = conditions.right
        assertThat(firstWord).isNotNull()
        assertThat(secondWord).isNotNull()
        assertThat(firstWord!!.operator).isEqualTo(Operator.OR)
        assertThat(firstWord.values()).containsOnly("alice")
        assertThat(secondWord!!.operator).isEqualTo(Operator.OR)
        assertThat(secondWord.values()).containsOnly("invoice")
    }

    @Test
    fun `quoted text should be searched as one term`() {
        val testSubject = createManualQuerySearch("\"alice example\"")

        val conditions = testSubject.conditions
        assertThat(conditions.operator).isEqualTo(Operator.OR)
        assertThat(conditions.values()).containsOnly("alice example")
    }

    private fun SearchConditionTreeNode.values(): List<String> = getLeafSet().mapNotNull { it.condition?.value }

    private fun SearchConditionTreeNode.fields() = getLeafSet().mapNotNull { it.condition?.field }
}
