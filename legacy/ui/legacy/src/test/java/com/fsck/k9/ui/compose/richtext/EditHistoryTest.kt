package com.fsck.k9.ui.compose.richtext

import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import net.thunderbird.core.android.testing.RobolectricTest
import net.thunderbird.feature.mail.message.composer.richtext.Alignment
import net.thunderbird.feature.mail.message.composer.richtext.BlockType
import net.thunderbird.feature.mail.message.composer.richtext.FormattedRange
import net.thunderbird.feature.mail.message.composer.richtext.ParagraphRange
import net.thunderbird.feature.mail.message.composer.richtext.TextFormat
import org.junit.Test

class EditHistoryTest : RobolectricTest() {
    private var now = 10_000L
    private val editText = EditText(ApplicationProvider.getApplicationContext())

    private fun createTestSubject(initialText: String = ""): EditHistory {
        editText.setText(initialText)
        editText.setSelection(initialText.length)
        return EditHistory(editText, onChange = {}, clock = { now })
    }

    private fun type(text: String) {
        for (character in text) {
            editText.text.append(character)
            now += 100
        }
    }

    @Test
    fun `undo should restore the text from before the typing`() {
        // Arrange
        val testSubject = createTestSubject("Hello")
        type("Jane")

        // Act
        testSubject.undo()

        // Assert
        assertThat(editText.text.toString()).isEqualTo("Hello")
        assertThat(testSubject.canUndo).isFalse()
    }

    @Test
    fun `a pause in typing should start a new step`() {
        // Arrange
        val testSubject = createTestSubject()
        type("ab")
        now += 2_000
        type("cd")

        // Act
        testSubject.undo()

        // Assert
        assertThat(editText.text.toString()).isEqualTo("ab")
        testSubject.undo()
        assertThat(editText.text.toString()).isEqualTo("")
    }

    @Test
    fun `the end of a word should start a new step`() {
        // Arrange
        val testSubject = createTestSubject()
        type("one two")

        // Act
        testSubject.undo()

        // Assert
        assertThat(editText.text.toString()).isEqualTo("one ")
    }

    @Test
    fun `a format change should be a step of its own`() {
        // Arrange
        val testSubject = createTestSubject("Hello Jane")
        type("!")
        testSubject.beforeFormatChange()
        RichTextFormatter.toggle(editText.text, TextFormat.Bold, selectionStart = 6, selectionEnd = 10)

        // Act
        testSubject.undo()

        // Assert
        assertThat(editText.text.toString()).isEqualTo("Hello Jane!")
        assertThat(RichTextFormatter.formattedRanges(editText.text)).isEmpty()
        assertThat(testSubject.canUndo).isTrue()
    }

    @Test
    fun `redo should bring back what was undone`() {
        // Arrange
        val testSubject = createTestSubject("Hello Jane")
        testSubject.beforeFormatChange()
        RichTextFormatter.toggle(editText.text, TextFormat.Bold, selectionStart = 6, selectionEnd = 10)
        testSubject.undo()

        // Act
        testSubject.redo()

        // Assert
        assertThat(RichTextFormatter.formattedRanges(editText.text))
            .isEqualTo(listOf(FormattedRange(6, 10, TextFormat.Bold)))
        assertThat(testSubject.canRedo).isFalse()
        assertThat(testSubject.canUndo).isTrue()
    }

    @Test
    fun `a new edit after an undo should clear what could be redone`() {
        // Arrange
        val testSubject = createTestSubject("Hello")
        type("Jane")
        testSubject.undo()
        assertThat(testSubject.canRedo).isTrue()

        // Act
        type("Max")

        // Assert
        assertThat(testSubject.canRedo).isFalse()
    }

    @Test
    fun `the history should keep at most 100 steps`() {
        // Arrange
        val testSubject = createTestSubject("Hello")
        repeat(120) { testSubject.beforeFormatChange() }

        // Act
        var undone = 0
        while (testSubject.canUndo) {
            testSubject.undo()
            undone++
        }

        // Assert
        assertThat(undone).isEqualTo(100)
    }

    @Test
    fun `setting the text as a whole should clear the history`() {
        // Arrange
        val testSubject = createTestSubject("Hello")
        type("Jane")
        testSubject.undo()
        type("Max")
        testSubject.undo()

        // Act
        editText.setText("A new text")

        // Assert
        assertThat(testSubject.canUndo).isFalse()
        assertThat(testSubject.canRedo).isFalse()
    }

    @Test
    fun `undo should restore the selection`() {
        // Arrange
        val testSubject = createTestSubject("Hello Jane")
        editText.setSelection(1, 3)
        editText.text.replace(1, 3, "Z")

        // Act
        testSubject.undo()

        // Assert
        assertThat(editText.text.toString()).isEqualTo("Hello Jane")
        assertThat(editText.selectionStart).isEqualTo(1)
        assertThat(editText.selectionEnd).isEqualTo(3)
    }

    @Test
    fun `undo of a format at a collapsed cursor should remove the format typed next`() {
        // Arrange
        val testSubject = createTestSubject("Hello")
        testSubject.beforeFormatChange()
        RichTextFormatter.toggle(editText.text, TextFormat.Bold, 5, 5)

        // Act
        testSubject.undo()

        // Assert
        val selection = editText.selectionStart
        assertThat(RichTextFormatter.isActive(editText.text, TextFormat.Bold, selection, selection)).isFalse()
    }

    @Test
    fun `undo right after Enter at the end of a list should take the new item away`() {
        // Arrange - the text of the item is typed and finished, Enter follows a pause.
        val testSubject = createTestSubject()
        editText.addTextChangedListener(ParagraphEditWatcher { true })
        type("- one")
        now += 2_000
        editText.text.append("\n")

        // Act - the marker of the new item is formatting of that item, not a step of its own.
        testSubject.undo()

        // Assert
        assertThat(editText.text.toString()).isEqualTo("one")
        assertThat(ParagraphFormatter.paragraphs(editText.text)).containsExactly(
            ParagraphRange(0, 3, BlockType.BulletList, 0, Alignment.Start),
        )
    }
}
