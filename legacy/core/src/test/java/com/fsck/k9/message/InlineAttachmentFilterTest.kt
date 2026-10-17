package com.fsck.k9.message

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import com.fsck.k9.message.Attachment.LoadingState
import org.junit.Test

class InlineAttachmentFilterTest {

    @Test
    fun `onlyReferenced should keep the attachments the html refers to`() {
        // Arrange
        val kept = attachment()
        val attachments = mapOf("kept@thunderbird.local" to kept, "gone@thunderbird.local" to attachment())

        // Act
        val result = InlineAttachmentFilter.onlyReferenced(
            attachments,
            """<div><img src="cid:kept@thunderbird.local" alt=""></div>""",
        )

        // Assert
        assertThat(result.keys).containsOnly("kept@thunderbird.local")
    }

    @Test
    fun `onlyReferenced should count references in any of the texts`() {
        // Arrange
        val attachments = mapOf("a" to attachment(), "b" to attachment(), "c" to attachment())

        // Act
        val result = InlineAttachmentFilter.onlyReferenced(
            attachments,
            "<img src='cid:a'>",
            null,
            "<blockquote><img src=cid:b></blockquote>",
        )

        // Assert
        assertThat(result.keys).containsOnly("a", "b")
    }

    @Test
    fun `onlyReferenced should match an id the html has percent-encoded`() {
        // Arrange
        val attachments = mapOf("image@host" to attachment())

        // Act
        val result = InlineAttachmentFilter.onlyReferenced(attachments, """<img src="cid:image%40host">""")

        // Assert
        assertThat(result.keys).containsOnly("image@host")
    }

    @Test
    fun `onlyReferenced should drop everything without a reference`() {
        // Arrange
        val attachments = mapOf("a" to attachment())

        // Act
        val result = InlineAttachmentFilter.onlyReferenced(attachments, "<div>text</div>", null)

        // Assert
        assertThat(result.keys).isEmpty()
    }

    private fun attachment() = object : Attachment {
        override val state = LoadingState.COMPLETE
        override val fileName = "/tmp/file"
        override val contentType = "image/png"
        override val name = "image.png"
        override val size = 1L
        override val isInternalAttachment = true
    }
}
