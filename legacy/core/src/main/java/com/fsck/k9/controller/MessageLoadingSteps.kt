package com.fsck.k9.controller

/**
 * How many messages of a folder are kept on the device while the user scrolls through it.
 *
 * Only a few more messages are loaded at first. The further the user scrolls, the bigger the steps get. In the end
 * all messages of the folder are loaded.
 */
object MessageLoadingSteps {
    /**
     * The visible limit of a folder whose messages are all kept on the device.
     */
    const val ALL_MESSAGES = 0

    // Starting with the messages that fill a screen, each of these steps is taken once.
    private val steps = listOf(
        Step(untilVisibleLimit = 30, additionalMessages = 20),
        Step(untilVisibleLimit = 100, additionalMessages = 80),
        Step(untilVisibleLimit = 200, additionalMessages = 100),
        Step(untilVisibleLimit = 700, additionalMessages = 500),
        Step(untilVisibleLimit = 1700, additionalMessages = 1000),
    )

    /**
     * @return The visible limit to use after [visibleLimit], or [ALL_MESSAGES] once the steps are used up.
     */
    fun nextVisibleLimit(visibleLimit: Int): Int {
        val step = steps.firstOrNull { visibleLimit in 1 until it.untilVisibleLimit }
        return if (step != null) visibleLimit + step.additionalMessages else ALL_MESSAGES
    }

    private data class Step(val untilVisibleLimit: Int, val additionalMessages: Int)
}
