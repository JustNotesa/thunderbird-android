package com.fsck.k9.ui.compose

import net.thunderbird.feature.mail.message.composer.richtext.FormattedText

/**
 * This text without the inline images whose Content-ID is not in [contentIds], i.e. whose picture is not part of the
 * message and could not be sent along. Their character in the text is removed as well.
 */
internal fun FormattedText.withoutImagesOutside(contentIds: Set<String>): FormattedText {
    val missing = images.filter { image -> image.contentId !in contentIds }.map { image -> image.position }

    return missing.sortedDescending().fold(this) { text, position -> text.withoutCharacterAt(position) }
}

private fun FormattedText.withoutCharacterAt(position: Int): FormattedText {
    fun move(index: Int) = if (index > position) index - 1 else index

    return FormattedText(
        text = text.removeRange(position, position + 1),
        ranges = ranges.map { range -> range.copy(start = move(range.start), end = move(range.end)) }
            .filter { range -> range.start < range.end },
        colors = colors.map { range -> range.copy(start = move(range.start), end = move(range.end)) }
            .filter { range -> range.start < range.end },
        links = links.map { range -> range.copy(start = move(range.start), end = move(range.end)) }
            .filter { range -> range.start < range.end },
        paragraphs = paragraphs.map { range -> range.copy(start = move(range.start), end = move(range.end)) },
        images = images.filter { image -> image.position != position }
            .map { image -> image.copy(position = move(image.position)) },
    )
}
