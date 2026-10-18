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
 * user pauses or ends a word; every format change is a step of its own. The end marker of the empty last line is
 * formatting, not text the user typed, so it belongs to the step of the edit that caused it. When the text is replaced
 * as a whole, e.g. by a draft, the history starts anew.
 *
 * @param clock the time in milliseconds, for tests
 */
internal class EditHistory(
    private val editText: EditText,
    private val onChange: () -> Unit,
    private val images: InlineImages = InlineImages.NONE,
    private val clock: () -> Long = SystemClock::uptimeMillis,
) : TextWatcher {
    private val undoSteps = ArrayDeque<Snapshot>()
    private val redoSteps = ArrayDeque<Snapshot>()
    private var isTypingStepOpen = false
    private var lastChangeTime = 0L
    private var isRestoring = false
    private var watchedText: Editable? = null
    private var openedStep: Snapshot? = null
    private var removesEndMarkerOnly = false

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
        openedStep = null
        removesEndMarkerOnly = false
        if (isRestoring) return
        // A text set as a whole is a new text: what could be undone belonged to the old one.
        if (editText.text !== watchedText) return

        val now = clock()
        if (now - lastChangeTime > TYPING_PAUSE_MILLIS) isTypingStepOpen = false
        lastChangeTime = now

        removesEndMarkerOnly = text.holdsOnlyEndMarkers(start, count)
        // What the change brings in is only known in onTextChanged(), so the step it would open is opened there.
        if (!isTypingStepOpen) openedStep = currentSnapshot()
    }

    override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
        if (isRestoring) return

        // The end marker is formatting of the empty last line: it belongs to the step of the edit that caused it.
        val isEndMarkerChange = removesEndMarkerOnly && text.holdsOnlyEndMarkers(start, count)
        val step = openedStep
        openedStep = null
        if (step != null && !isEndMarkerChange) {
            record(step)
            isTypingStepOpen = true
        }

        // The end of a word or a line ends the step of typing it.
        if (endsWithWhitespace(text, start, count)) isTypingStepOpen = false
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
            editText.setText(RichTextFormatter.toSpanned(snapshot.formattedText, images))
            // The marker is what gives the empty last line its formatting, so it has to stand in the restored text.
            editText.text.keepEndMarker()
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

/**
 * Whether the `count` characters of this text from [start] are end markers and nothing else. A change that removes
 * none of them takes a marker in, so this holds for it as well.
 */
private fun CharSequence?.holdsOnlyEndMarkers(start: Int, count: Int): Boolean {
    if (this == null) return false

    val end = minOf(start + count, length)

    return (start until end).all { position -> this[position] == END_MARKER }
}
