package net.thunderbird.app.common.account

import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.first
import net.thunderbird.feature.account.profile.AccountProfileRepository

internal class AccountColorPicker(
    private val repository: AccountProfileRepository,
    private val accountColors: ImmutableList<Int>,
) {
    suspend fun pickColor(): Int {
        val usedCounts = usedColorCounts()

        return pickLeastUsedColor(usedCounts)
    }

    /**
     * Picks the colors for [count] accounts that are created at the same time.
     *
     * The colors of the accounts that already exist are taken into account, and the colors picked for this batch are
     * counted as used, so every account of the batch gets its own color.
     */
    suspend fun pickColors(count: Int): List<Int> {
        val usedCounts = usedColorCounts()

        return List(count) { pickLeastUsedColor(usedCounts) }
    }

    private suspend fun usedColorCounts(): MutableMap<Int, Int> {
        val profiles = repository.getAll().first()

        return profiles.groupingBy { it.color }.eachCount().toMutableMap()
    }

    private fun pickLeastUsedColor(usedCounts: MutableMap<Int, Int>): Int {
        val minCount = accountColors.minOf { usedCounts[it] ?: 0 }
        val candidates = accountColors.filter {
            (usedCounts[it] ?: 0) == minCount
        }

        val color = if (candidates.isNotEmpty()) {
            candidates.shuffled().first()
        } else {
            accountColors.shuffled().first()
        }
        usedCounts[color] = (usedCounts[color] ?: 0) + 1

        return color
    }
}
