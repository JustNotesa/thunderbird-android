package com.fsck.k9.activity

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test

class AppBarPlacementTest {
    @Test
    fun `setting off should keep the toolbar at the top on every screen`() {
        AppBarScreen.entries.forEach { screen ->
            val result = appBarPlacement(isBottomAppBarEnabled = false, screen = screen)

            assertThat(result).isEqualTo(AppBarPlacement.TOP)
        }
    }

    @Test
    fun `message list should overlay the content with the toolbar at the bottom`() {
        val result = appBarPlacement(isBottomAppBarEnabled = true, screen = AppBarScreen.MESSAGE_LIST)

        assertThat(result).isEqualTo(AppBarPlacement.BOTTOM_OVER_CONTENT)
    }

    @Test
    fun `message view should end the content above the toolbar at the bottom`() {
        val result = appBarPlacement(isBottomAppBarEnabled = true, screen = AppBarScreen.MESSAGE_VIEW)

        assertThat(result).isEqualTo(AppBarPlacement.BOTTOM_BELOW_CONTENT)
    }

    @Test
    fun `split view should keep the toolbar at the top`() {
        val result = appBarPlacement(isBottomAppBarEnabled = true, screen = AppBarScreen.SPLIT_VIEW)

        assertThat(result).isEqualTo(AppBarPlacement.TOP)
    }

    @Test
    fun `isAtBottom should be true only for the bottom placements`() {
        assertThat(AppBarPlacement.TOP.isAtBottom).isFalse()
        assertThat(AppBarPlacement.BOTTOM_OVER_CONTENT.isAtBottom).isTrue()
        assertThat(AppBarPlacement.BOTTOM_BELOW_CONTENT.isAtBottom).isTrue()
    }

    @Test
    fun `message header should be at the bottom only in the message view with the setting on`() {
        val results = AppBarScreen.entries.associateWith { screen ->
            shouldPinMessageHeaderAtBottom(isBottomAppBarEnabled = true, screen = screen)
        }

        assertThat(results).isEqualTo(
            mapOf(
                AppBarScreen.MESSAGE_LIST to false,
                AppBarScreen.MESSAGE_VIEW to true,
                AppBarScreen.SPLIT_VIEW to false,
            ),
        )
    }

    @Test
    fun `message header should stay at the top with the setting off`() {
        val result = shouldPinMessageHeaderAtBottom(isBottomAppBarEnabled = false, screen = AppBarScreen.MESSAGE_VIEW)

        assertThat(result).isFalse()
    }

    @Test
    fun `list should keep the toolbar height reserved while a message is displayed`() {
        val result = listBottomBarHeight(true, AppBarScreen.MESSAGE_VIEW, appBarHeight = 240)

        assertThat(result).isEqualTo(240)
    }

    @Test
    fun `list should reserve the toolbar height in the message list`() {
        val result = listBottomBarHeight(true, AppBarScreen.MESSAGE_LIST, appBarHeight = 240)

        assertThat(result).isEqualTo(240)
    }

    @Test
    fun `list should reserve nothing with the toolbar at the top or in the split view`() {
        assertThat(listBottomBarHeight(false, AppBarScreen.MESSAGE_LIST, appBarHeight = 240)).isEqualTo(0)
        assertThat(listBottomBarHeight(true, AppBarScreen.SPLIT_VIEW, appBarHeight = 240)).isEqualTo(0)
    }
}
