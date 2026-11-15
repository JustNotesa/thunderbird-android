package com.fsck.k9.message.extractors

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.fsck.k9.mail.Body
import com.fsck.k9.mail.BodyPart
import com.fsck.k9.mail.internet.MimeBodyPart
import com.fsck.k9.mailstore.BinaryMemoryBody
import com.fsck.k9.message.MessageCreationHelper
import net.thunderbird.components.core.logging.testing.TestLogger
import net.thunderbird.legacy.logging.Log
import org.junit.Before
import org.junit.Test
import java.io.InputStream

class MessageFulltextCreatorTest {
    private val messageFulltextCreator = MessageFulltextCreator(
        textPartFinder = TextPartFinder(),
        attachmentTextExtractor = AttachmentTextExtractor(FakePdfTextReader()),
    )

    @Before
    fun setUp() {
        Log.logger = TestLogger()
    }

    @Test
    fun `message with text part`() {
        val message = MessageCreationHelper.createTextMessage("text/plain", "Hello world")

        val fulltext = messageFulltextCreator.createFulltext(message)

        assertThat(fulltext).isEqualTo("Hello world")
    }

    @Test
    fun `message with text part and attachment`() {
        val textPart = MessageCreationHelper.createTextPart("text/plain", "Hello world")
        val attachmentPart = createPdfAttachment()
        val message = MessageCreationHelper.createMultipartMessage("multipart/mixed", textPart, attachmentPart)

        val fulltext = messageFulltextCreator.createFulltext(message)

        assertThat(fulltext).isEqualTo("Hello world\ndocument.pdf\nText of PDF document")
    }

    @Test
    fun `message with attachment but without text part`() {
        val attachmentPart = createPdfAttachment()
        val message = MessageCreationHelper.createMultipartMessage("multipart/mixed", attachmentPart)

        val fulltext = messageFulltextCreator.createFulltext(message)

        assertThat(fulltext).isEqualTo("document.pdf\nText of PDF document")
    }

    @Test
    fun `message without text part and without attachment`() {
        val message = MessageCreationHelper.createMultipartMessage("multipart/mixed")

        val fulltext = messageFulltextCreator.createFulltext(message)

        assertThat(fulltext).isNull()
    }

    private fun createPdfAttachment(): BodyPart {
        val body: Body = BinaryMemoryBody("%PDF-1.4".toByteArray(), "binary")
        val part = MimeBodyPart(body, "application/pdf")
        part.setHeader("Content-Disposition", "attachment; filename=\"document.pdf\"")
        return part
    }

    private class FakePdfTextReader : PdfTextReader {
        override fun readText(input: InputStream): String? {
            return "Text of PDF document"
        }
    }
}
