package net.thunderbird.components.ui.bolt.atom.textfield

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar

/**
 * Lets the caller take over pasting into the text fields in [content], e.g. to keep the formatting of what was
 * copied. This covers "Paste" in the menu of the cursor and of a selection.
 *
 * [onPaste] returns `true` if it dealt with the paste, otherwise the field pastes as it always does. Use
 * [pasteShortcut] on the field as well for the keyboard shortcut.
 */
@Composable
fun TextFieldPasteHandler(
    onPaste: () -> Boolean,
    content: @Composable () -> Unit,
) {
    val delegate = LocalTextToolbar.current
    val currentOnPaste by rememberUpdatedState(onPaste)
    val toolbar = remember(delegate) {
        PasteInterceptingTextToolbar(delegate) { currentOnPaste() }
    }

    CompositionLocalProvider(LocalTextToolbar provides toolbar, content = content)
}

/**
 * Hands Ctrl+V of a hardware keyboard to [onPaste]. If it returns `false` the field pastes as it always does.
 */
fun Modifier.pasteShortcut(onPaste: () -> Boolean): Modifier = onPreviewKeyEvent { event ->
    event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == Key.V && onPaste()
}

private class PasteInterceptingTextToolbar(
    private val delegate: TextToolbar,
    private val onPaste: () -> Boolean,
) : TextToolbar by delegate {
    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        val interceptedPaste = onPasteRequested?.let { defaultPaste ->
            { if (!onPaste()) defaultPaste() }
        }
        delegate.showMenu(rect, onCopyRequested, interceptedPaste, onCutRequested, onSelectAllRequested)
    }
}
