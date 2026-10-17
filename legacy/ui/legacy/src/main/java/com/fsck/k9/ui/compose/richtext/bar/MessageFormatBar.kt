package com.fsck.k9.ui.compose.richtext.bar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fsck.k9.ui.R
import com.fsck.k9.ui.compose.richtext.ImageSource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.thunderbird.components.ui.bolt.atom.DividerVertical
import net.thunderbird.components.ui.bolt.atom.Surface
import net.thunderbird.components.ui.bolt.atom.icon.Icons
import net.thunderbird.components.ui.bolt.theme.BoltTheme
import net.thunderbird.feature.mail.message.composer.richtext.Alignment
import net.thunderbird.feature.mail.message.composer.richtext.BlockType
import net.thunderbird.feature.mail.message.composer.richtext.ColorKind
import net.thunderbird.feature.mail.message.composer.richtext.TextFormat

private val BAR_HEIGHT = 48.dp
private val DIVIDER_HEIGHT = 24.dp

private val IMAGE_OPTIONS_GALLERY_ONLY = persistentListOf(
    MenuOption(ImageSource.Gallery, R.string.message_compose_image_source_gallery),
)
private val IMAGE_OPTIONS = persistentListOf(
    MenuOption(ImageSource.Gallery, R.string.message_compose_image_source_gallery),
    MenuOption(ImageSource.Camera, R.string.message_compose_image_source_camera),
)
private val ALIGNMENT_OPTIONS = persistentListOf(
    MenuOption(Alignment.Start, R.string.message_compose_format_align_left),
    MenuOption(Alignment.Center, R.string.message_compose_format_align_center),
    MenuOption(Alignment.End, R.string.message_compose_format_align_right),
)
private val SIZE_OPTIONS: ImmutableList<MenuOption<TextFormat?>> = persistentListOf(
    MenuOption(TextFormat.SizeSmall, R.string.message_compose_format_size_small),
    MenuOption(null, R.string.message_compose_format_size_normal),
    MenuOption(TextFormat.SizeLarge, R.string.message_compose_format_size_large),
    MenuOption(TextFormat.SizeHuge, R.string.message_compose_format_size_huge),
)
private val FONT_OPTIONS: ImmutableList<MenuOption<TextFormat?>> = persistentListOf(
    MenuOption(null, R.string.message_compose_format_font_default),
    MenuOption(TextFormat.FontSerif, R.string.message_compose_format_font_serif),
    MenuOption(TextFormat.FontMonospace, R.string.message_compose_format_font_monospace),
)

/**
 * The format bar of the message composer, above the keyboard: a scrollable row of buttons in groups.
 */
@Composable
internal fun MessageFormatBar(
    state: FormatBarState,
    onEvent: (FormatBarEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxWidth(), color = BoltTheme.colors.surfaceContainer) {
        Row(
            modifier = Modifier
                .height(BAR_HEIGHT)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = BoltTheme.spacings.half),
            verticalAlignment = CenterVertically,
        ) {
            if (state.isUndoVisible) {
                HistoryButtons(state, onEvent)
                GroupDivider()
            }
            CharacterButtons(state, onEvent)
            GroupDivider()
            ColorButtons(state, onEvent)
            GroupDivider()
            ParagraphButtons(state, onEvent)
            GroupDivider()
            InsertButtons(state, onEvent)
            GroupDivider()
            TypeButtons(state, onEvent)
        }
    }
}

@Composable
private fun RowScope.GroupDivider() {
    DividerVertical(modifier = Modifier.height(DIVIDER_HEIGHT).padding(horizontal = BoltTheme.spacings.half))
}

@Composable
private fun HistoryButtons(state: FormatBarState, onEvent: (FormatBarEvent) -> Unit) {
    FormatButton(Icons.Outlined.Undo, R.string.message_compose_format_undo, { onEvent(FormatBarEvent.Undo) },
        enabled = state.canUndo)
    FormatButton(Icons.Outlined.Redo, R.string.message_compose_format_redo, { onEvent(FormatBarEvent.Redo) },
        enabled = state.canRedo)
}

@Composable
private fun CharacterButtons(state: FormatBarState, onEvent: (FormatBarEvent) -> Unit) {
    val toggle = { format: TextFormat -> onEvent(FormatBarEvent.ToggleFormat(format)) }
    FormatButton(Icons.Outlined.FormatBold, R.string.message_compose_format_bold, { toggle(TextFormat.Bold) },
        isOn = TextFormat.Bold in state.activeFormats)
    FormatButton(Icons.Outlined.FormatItalic, R.string.message_compose_format_italic, { toggle(TextFormat.Italic) },
        isOn = TextFormat.Italic in state.activeFormats)
    FormatButton(Icons.Outlined.FormatUnderlined, R.string.message_compose_format_underline,
        { toggle(TextFormat.Underline) }, isOn = TextFormat.Underline in state.activeFormats)
    FormatButton(Icons.Outlined.FormatStrikethrough, R.string.message_compose_format_strikethrough,
        { toggle(TextFormat.Strikethrough) }, isOn = TextFormat.Strikethrough in state.activeFormats)
}

@Composable
private fun ColorButtons(state: FormatBarState, onEvent: (FormatBarEvent) -> Unit) {
    ColorMenuButton(
        icon = Icons.Outlined.FormatColorText,
        descriptionRes = R.string.message_compose_format_text_color,
        colors = FormatBarColors.TEXT,
        removeRes = R.string.message_compose_format_color_default,
        selected = state.textColor,
        onSelect = { color -> onEvent(FormatBarEvent.SetColor(ColorKind.Text, color)) },
    )
    ColorMenuButton(
        icon = Icons.Outlined.FormatColorFill,
        descriptionRes = R.string.message_compose_format_highlight,
        colors = FormatBarColors.HIGHLIGHT,
        removeRes = R.string.message_compose_format_highlight_none,
        selected = state.highlightColor,
        onSelect = { color -> onEvent(FormatBarEvent.SetColor(ColorKind.Highlight, color)) },
    )
}

@Composable
private fun ParagraphButtons(state: FormatBarState, onEvent: (FormatBarEvent) -> Unit) {
    val toggle = { block: BlockType -> onEvent(FormatBarEvent.ToggleBlock(block)) }
    FormatButton(Icons.Outlined.FormatListBulleted, R.string.message_compose_format_bullet_list,
        { toggle(BlockType.BulletList) }, isOn = state.block == BlockType.BulletList)
    FormatButton(Icons.Outlined.FormatListNumbered, R.string.message_compose_format_numbered_list,
        { toggle(BlockType.NumberedList) }, isOn = state.block == BlockType.NumberedList)
    FormatButton(Icons.Outlined.FormatIndentDecrease, R.string.message_compose_format_outdent,
        { onEvent(FormatBarEvent.ChangeLevel(-1)) })
    FormatButton(Icons.Outlined.FormatIndentIncrease, R.string.message_compose_format_indent,
        { onEvent(FormatBarEvent.ChangeLevel(1)) })
    FormatButton(Icons.Outlined.FormatQuote, R.string.message_compose_format_quote,
        { toggle(BlockType.Quote) }, isOn = state.block == BlockType.Quote)
    OptionMenuButton(
        icon = state.alignment.icon(),
        descriptionRes = R.string.message_compose_format_align,
        options = ALIGNMENT_OPTIONS,
        selected = state.alignment,
        onSelect = { alignment -> onEvent(FormatBarEvent.SetAlignment(alignment)) },
    )
}

@Composable
private fun InsertButtons(state: FormatBarState, onEvent: (FormatBarEvent) -> Unit) {
    FormatButton(Icons.Outlined.Link, R.string.message_compose_format_link, { onEvent(FormatBarEvent.EditLink) },
        isOn = state.isInLink)
    if (state.isImageVisible) {
        OptionMenuButton(
            icon = Icons.Outlined.Image,
            descriptionRes = R.string.message_compose_format_image,
            options = if (state.isCameraAvailable) IMAGE_OPTIONS else IMAGE_OPTIONS_GALLERY_ONLY,
            selected = null,
            onSelect = { source -> onEvent(FormatBarEvent.InsertImage(source)) },
        )
    }
}

@Composable
private fun TypeButtons(state: FormatBarState, onEvent: (FormatBarEvent) -> Unit) {
    OptionMenuButton(
        icon = Icons.Outlined.FormatSize,
        descriptionRes = R.string.message_compose_format_size,
        options = SIZE_OPTIONS,
        selected = state.size,
        onSelect = { size -> onEvent(FormatBarEvent.SelectSize(size)) },
        isOn = state.size != null,
    )
    OptionMenuButton(
        icon = Icons.Outlined.FontDownload,
        descriptionRes = R.string.message_compose_format_font,
        options = FONT_OPTIONS,
        selected = state.font,
        onSelect = { font -> onEvent(FormatBarEvent.SelectFont(font)) },
        isOn = state.font != null,
    )
    FormatButton(Icons.Outlined.FormatClear, R.string.message_compose_format_clear,
        { onEvent(FormatBarEvent.ClearFormatting) })
}

private fun Alignment.icon() = when (this) {
    Alignment.Start -> Icons.Outlined.FormatAlignLeft
    Alignment.Center -> Icons.Outlined.FormatAlignCenter
    Alignment.End -> Icons.Outlined.FormatAlignRight
}
