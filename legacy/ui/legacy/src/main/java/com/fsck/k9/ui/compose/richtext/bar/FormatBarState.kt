package com.fsck.k9.ui.compose.richtext.bar

import com.fsck.k9.ui.compose.richtext.ImageSource
import net.thunderbird.feature.mail.message.composer.richtext.Alignment
import net.thunderbird.feature.mail.message.composer.richtext.BlockType
import net.thunderbird.feature.mail.message.composer.richtext.ColorKind
import net.thunderbird.feature.mail.message.composer.richtext.TextFormat

/**
 * What the format bar of the message composer shows: the formatting of the selection, or of what is typed next at the
 * cursor, and which buttons are there.
 */
internal data class FormatBarState(
    val activeFormats: Set<TextFormat> = emptySet(),
    val size: TextFormat? = null,
    val font: TextFormat? = null,
    val textColor: Int? = null,
    val highlightColor: Int? = null,
    val block: BlockType = BlockType.Normal,
    val alignment: Alignment = Alignment.Start,
    val isInLink: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isUndoVisible: Boolean = false,
    val isImageVisible: Boolean = false,
    val isCameraAvailable: Boolean = false,
)

/**
 * What the user asked for with a button of the format bar.
 */
internal sealed interface FormatBarEvent {
    data class ToggleFormat(val format: TextFormat) : FormatBarEvent
    data class SelectSize(val format: TextFormat?) : FormatBarEvent
    data class SelectFont(val format: TextFormat?) : FormatBarEvent
    data class SetColor(val kind: ColorKind, val color: Int?) : FormatBarEvent
    data class ToggleBlock(val block: BlockType) : FormatBarEvent
    data class ChangeLevel(val delta: Int) : FormatBarEvent
    data class SetAlignment(val alignment: Alignment) : FormatBarEvent
    data object EditLink : FormatBarEvent
    data class InsertImage(val source: ImageSource) : FormatBarEvent
    data object Undo : FormatBarEvent
    data object Redo : FormatBarEvent
    data object ClearFormatting : FormatBarEvent
}
