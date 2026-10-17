package com.fsck.k9.ui.compose.richtext

import android.text.Layout
import android.text.style.AlignmentSpan
import android.text.style.LeadingMarginSpan
import net.thunderbird.feature.mail.message.composer.richtext.Alignment
import net.thunderbird.feature.mail.message.composer.richtext.BlockType

/**
 * The alignment as the Android spans ask for it.
 */
internal fun Alignment.toAlign(): Layout.Alignment {
    return when (this) {
        Alignment.Start -> Layout.Alignment.ALIGN_NORMAL
        Alignment.Center -> Layout.Alignment.ALIGN_CENTER
        Alignment.End -> Layout.Alignment.ALIGN_OPPOSITE
    }
}

internal fun LeadingMarginSpan?.blockType(): BlockType {
    return when (this) {
        is BulletListSpan -> BlockType.BulletList
        is NumberedListSpan -> BlockType.NumberedList
        is QuoteBlockSpan -> BlockType.Quote
        else -> BlockType.Normal
    }
}

internal fun LeadingMarginSpan?.indentLevel(): Int {
    return when (this) {
        is BulletListSpan -> level
        is NumberedListSpan -> level
        is QuoteBlockSpan -> level
        is IndentSpan -> level
        else -> 0
    }
}

internal fun AlignmentSpan?.alignment(): Alignment {
    return when (this?.alignment) {
        Layout.Alignment.ALIGN_CENTER -> Alignment.Center
        Layout.Alignment.ALIGN_OPPOSITE -> Alignment.End
        else -> Alignment.Start
    }
}
