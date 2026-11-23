package com.fsck.k9.ui.messageview

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test

class HeaderCollapseStateTest {
    private val threshold = 40

    private fun HeaderCollapseState.afterScrollTo(scrollY: Int, scrollRange: Int = 2000) =
        nextHeaderCollapseState(this, scrollY, scrollRange, collapseDelta = 200, threshold = threshold)

    @Test
    fun `header should stay expanded while the body is scrolled less than the threshold`() {
        val testSubject = initialHeaderCollapseState(threshold)

        val result = testSubject.afterScrollTo(39)

        assertThat(result.isCollapsed).isFalse()
    }

    @Test
    fun `header should collapse once the body is scrolled to the threshold`() {
        val testSubject = initialHeaderCollapseState(threshold)

        val result = testSubject.afterScrollTo(40)

        assertThat(result.isCollapsed).isTrue()
    }

    @Test
    fun `header should not collapse when the body would not be scrollable afterwards`() {
        val testSubject = initialHeaderCollapseState(threshold)

        val result = testSubject.afterScrollTo(100, scrollRange = 150)

        assertThat(result.isCollapsed).isFalse()
    }

    @Test
    fun `collapsed header should stay collapsed while the body is scrolled`() {
        val testSubject = HeaderCollapseState(isCollapsed = true, collapseFromY = threshold)

        val result = testSubject.afterScrollTo(5)

        assertThat(result.isCollapsed).isTrue()
    }

    @Test
    fun `collapsed header should expand when the body is back at the top`() {
        val testSubject = HeaderCollapseState(isCollapsed = true, collapseFromY = threshold)

        val result = testSubject.afterScrollTo(0)

        assertThat(result).isEqualTo(initialHeaderCollapseState(threshold))
    }

    @Test
    fun `header unfolded by the user should not collapse again right away`() {
        val testSubject = headerUnfoldedByUser(scrollY = 500, threshold = threshold)

        val result = testSubject.afterScrollTo(520)

        assertThat(result.isCollapsed).isFalse()
    }

    @Test
    fun `header unfolded by the user should collapse after scrolling further down`() {
        val testSubject = headerUnfoldedByUser(scrollY = 500, threshold = threshold)

        val result = testSubject.afterScrollTo(540)

        assertThat(result.isCollapsed).isTrue()
    }

    @Test
    fun `header unfolded by the user should collapse again after a trip to the top`() {
        val unfolded = headerUnfoldedByUser(scrollY = 500, threshold = threshold)
        val atTop = unfolded.afterScrollTo(0)

        val result = atTop.afterScrollTo(60)

        assertThat(result.isCollapsed).isTrue()
    }
}
