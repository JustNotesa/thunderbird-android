package com.fsck.k9.ui.compose.richtext

import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import net.thunderbird.feature.mail.message.composer.richtext.FormattedText

private const val MAX_STEPS = 100
private const val TYPING_PAUSE_MILLIS = 1000L

/**
 * Undo and redo for the message text field, text and formatting together.
 *
 * A step is the state of the text before a change, as formatted text with its selection. Typing is one step until the
 * user pauses or ends a word; every format change is a step of its own. When the text is replaced as a whole, e.g. by
 * a draft, the history starts anew.
 *
 * @param clock the time in milliseconds, for tests
 */
internal class EditHistory(
    private val editText: EditText,
    private val onChange: () -> Unit,
    private val clock: () -> Long = SystemClock::uptimeMillis,
) : TextWatcher {
    private val undoSteps = ArrayDeque<Snapshot>()
    private val redoSteps = ArrayDeque<Snapshot>()
    private var isTypingStepOpen = false
    private var lastChangeTime = 0L
    private var isRestoring = false
    private var watchedText: Editable? = null

    val canUndo: Boolean get() = undoSteps.isNotEmpty()
    val canRedo: Boolean get() = redoSteps.isNotEmpty()

    init {
        editText.addTextChangedListener(this)
        watchedText = editText.text
    }

    /**
     * To be called right before the formatting is changed: the change becomes a step of its own.
     */
    fun beforeFormatChange() {
        isTypingStepOpen = false
        record(currentSnapshot())
    }

    fun undo() {
        val step = undoSteps.removeLastOrNull() ?: return
        redoSteps.addLast(currentSnapshot())
        restore(step)
    }

    fun redo() {
        val step = redoSteps.removeLastOrNull() ?: return
        undoSteps.addLast(currentSnapshot())
        restore(step)
    }

    override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) {
        if (isRestoring) return
        // A text set as a whole is a new text: what could be undone belonged to the old one.
        if (editText.text !== watchedText) return

        val now = clock()
        if (now - lastChangeTime > TYPING_PAUSE_MILLIS) isTypingStepOpen = false
        lastChangeTime = now
        if (!isTypingStepOpen) {
            record(currentSnapshot())
            isTypingStepOpen = true
        }
    }

    override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
        // The end of a word or a line ends the step of typing it.
        if (!isRestoring && endsWithWhitespace(text, start, count)) isTypingStepOpen = false
    }

    override fun afterTextChanged(text: Editable?) {
        if (text !== watchedText && !isRestoring) {
            undoSteps.clear()
            redoSteps.clear()
            isTypingStepOpen = false
        }
        watchedText = text
        onChange()
    }

    private fun endsWithWhitespace(text: CharSequence?, start: Int, count: Int): Boolean {
        return text != null && count > 0 && text[start + count - 1].isWhitespace()
    }

    private fun record(snapshot: Snapshot) {
        undoSteps.addLast(snapshot)
        if (undoSteps.size > MAX_STEPS) undoSteps.removeFirst()
        redoSteps.clear()
    }

    private fun restore(snapshot: Snapshot) {
        isRestoring = true
        try {
            editText.setText(RichTextFormatter.toSpanned(snapshot.formattedText))
            val length = editText.text.length
            val selectionStart = snapshot.selectionStart.coerceIn(0, length)
            editText.setSelection(selectionStart, snapshot.selectionEnd.coerceIn(0, length))
        } finally {
            isRestoring = false
        }
        isTypingStepOpen = false
        watchedText = editText.text
        onChange()
    }

    private fun currentSnapshot(): Snapshot {
        return Snapshot(
            formattedText = RichTextFormatter.formattedText(editText.text),
            selectionStart = editText.selectionStart,
            selectionEnd = editText.selectionEnd,
        )
    }

    private data class Snapshot(val formattedText: FormattedText, val selectionStart: Int, val selectionEnd: Int)
}
