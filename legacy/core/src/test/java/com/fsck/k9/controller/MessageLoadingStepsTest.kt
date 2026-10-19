package com.fsck.k9.controller

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import org.junit.Test

class MessageLoadingStepsTest {
    @Test
    fun `steps should start small and grow until all messages are loaded`() {
        val visibleLimits = generateSequence(15) { visibleLimit ->
            MessageLoadingSteps.nextVisibleLimit(visibleLimit).takeIf { it != MessageLoadingSteps.ALL_MESSAGES }
        }.toList()

        assertThat(visibleLimits).containsExactly(15, 35, 115, 215, 715, 1715)
    }

    @Test
    fun `steps should continue from the size a folder already has`() {
        val result = MessageLoadingSteps.nextVisibleLimit(100)

        assertThat(result).isEqualTo(200)
    }

    @Test
    fun `last step should load all messages`() {
        val result = MessageLoadingSteps.nextVisibleLimit(1715)

        assertThat(result).isEqualTo(MessageLoadingSteps.ALL_MESSAGES)
    }

    @Test
    fun `folder with all messages should stay that way`() {
        val result = MessageLoadingSteps.nextVisibleLimit(MessageLoadingSteps.ALL_MESSAGES)

        assertThat(result).isEqualTo(MessageLoadingSteps.ALL_MESSAGES)
    }
}
