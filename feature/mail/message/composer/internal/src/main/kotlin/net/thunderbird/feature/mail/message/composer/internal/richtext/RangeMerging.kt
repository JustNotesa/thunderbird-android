package net.thunderbird.feature.mail.message.composer.internal.richtext

import net.thunderbird.feature.mail.message.composer.richtext.ColorRange
import net.thunderbird.feature.mail.message.composer.richtext.FormattedRange
import net.thunderbird.feature.mail.message.composer.richtext.LinkRange

/**
 * The formatted ranges of a text, where runs of the same format that touch or overlap are one range.
 */
internal fun mergeRanges(ranges: List<FormattedRange>): List<FormattedRange> {
    return ranges.mergedBy(
        key = { it.format },
        touches = { item, merged -> item.start <= merged.end },
        order = compareBy({ it.start }, { it.format }, { it.end }),
        combine = { item, merged -> merged.copy(end = maxOf(merged.end, item.end)) },
    )
}

/**
 * The colours of a text, where runs of the same colour and kind that touch or overlap are one range.
 */
internal fun mergeColors(colors: List<ColorRange>): List<ColorRange> {
    return colors.mergedBy(
        key = { it.kind to it.color },
        touches = { item, merged -> item.start <= merged.end },
        order = compareBy({ it.start }, { it.kind }, { it.end }),
        combine = { item, merged -> merged.copy(end = maxOf(merged.end, item.end)) },
    )
}

/**
 * The links of a text, where runs of the same address that touch or overlap are one range.
 */
internal fun mergeLinks(links: List<LinkRange>): List<LinkRange> {
    return links.mergedBy(
        key = { it.url },
        touches = { item, merged -> item.start <= merged.end },
        order = compareBy({ it.start }, { it.end }),
        combine = { item, merged -> merged.copy(end = maxOf(merged.end, item.end)) },
    )
}

/**
 * Merges the ranges that carry the same [key] and touch or overlap into one, which is how the model keeps them.
 * Ranges are merged per key, because a range of another format that stands between them does not end them: two bold
 * runs with an italic run in between are one bold run.
 */
private fun <T, K> List<T>.mergedBy(
    key: (T) -> K,
    touches: (item: T, merged: T) -> Boolean,
    order: Comparator<in T>,
    combine: (item: T, merged: T) -> T,
): List<T> {
    return groupBy(key)
        .values
        .flatMap { ofOneKey -> ofOneKey.sortedWith(order).merge(touches, combine) }
        .sortedWith(order)
}

/**
 * Merges every range of this list that touches or overlaps the one merged before it into that one.
 */
private fun <T> List<T>.merge(touches: (item: T, merged: T) -> Boolean, combine: (item: T, merged: T) -> T): List<T> {
    val merged = mutableListOf<T>()

    for (item in this) {
        val last = merged.lastOrNull()
        if (last != null && touches(item, last)) {
            merged[merged.lastIndex] = combine(item, last)
        } else {
            merged.add(item)
        }
    }

    return merged
}
