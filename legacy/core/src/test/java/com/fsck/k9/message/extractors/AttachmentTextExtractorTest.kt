package com.fsck.k9.message.extractors

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.fsck.k9.mail.Body
import com.fsck.k9.mail.BodyPart
import com.fsck.k9.mail.Message
import com.fsck.k9.mail.internet.MimeBodyPart
import com.fsck.k9.mail.internet.TextBody
import com.fsck.k9.mailstore.BinaryMemoryBody
import com.fsck.k9.message.MessageCreationHelper
import net.thunderbird.components.core.logging.testing.TestLogger
import net.thunderbird.legacy.logging.Log
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class AttachmentTextExtractorTest {
    private val pdfTextReader = FakePdfTextReader()
    private val attachmentTextExtractor = AttachmentTextExtractor(pdfTextReader)

    @Before
    fun setUp() {
        Log.logger = TestLogger()
    }

    @Test
    fun `message without attachments`() {
        val message = MessageCreationHelper.createTextMessage("text/plain", "Hello world")

        val text = attachmentTextExtractor.extractText(message)

        assertThat(text).isNull()
    }

    @Test
    fun `text_plain attachment`() {
        val part = createAttachmentPart("text/plain", "notes.txt", TextBody("Some text"))
        val message = createMessage(part)

        val text = attachmentTextExtractor.extractText(message)

        assertThat(text).isEqualTo("notes.txt\nSome text")
    }

    @Test
    fun `text_html attachment`() {
        val part = createAttachmentPart("text/html", "page.html", TextBody("<p>Hello world</p>"))
        val message = createMessage(part)

        val text = attachmentTextExtractor.extractText(message)

        assertThat(text).isEqualTo("page.html\nHello world")
    }

    @Test
    fun `attachment with unknown mime type but known file name extension`() {
        val part = createAttachmentPart("application/octet-stream", "notes.txt", TextBody("Some text"))
        val message = createMessage(part)

        val text = attachmentTextExtractor.extractText(message)

        assertThat(text).isEqualTo("notes.txt\nSome text")
    }

    @Test
    fun `docx attachment`() {
        val document = "<w:document><w:body><w:t>Hallo</w:t><w:t>Welt</w:t></w:body></w:document>"
        val body = createZipBody("word/document.xml" to document)
        val part = createAttachmentPart(DOCX_MIME_TYPE, "document.docx", body)
        val message = createMessage(part)

        val text = attachmentTextExtractor.extractText(message)

        assertThat(text).isEqualTo("document.docx\nHallo Welt")
    }

    @Test
    fun `xlsx attachment`() {
        val body = createZipBody("xl/sharedStrings.xml" to "<sst><si><t>Hallo</t></si><si><t>Welt</t></si></sst>")
        val part = createAttachmentPart(XLSX_MIME_TYPE, "sheet.xlsx", body)
        val message = createMessage(part)

        val text = attachmentTextExtractor.extractText(message)

        assertThat(text).isEqualTo("sheet.xlsx\nHallo Welt")
    }

    @Test
    fun `pptx attachment`() {
        val body = createZipBody(
            "ppt/slides/slide1.xml" to "<p:sld><a:t>Hallo</a:t><a:t>Welt</a:t></p:sld>",
            "ppt/slides/_rels/slide1.xml.rels" to "<Relationships/>",
        )
        val part = createAttachmentPart(PPTX_MIME_TYPE, "slides.pptx", body)
        val message = createMessage(part)

        val text = attachmentTextExtractor.extractText(message)

        assertThat(text).isEqualTo("slides.pptx\nHallo Welt")
    }

    @Test
    fun `pdf attachment`() {
        val part = createAttachmentPart("application/pdf", "document.pdf", createBinaryBody("%PDF-1.4"))
        val message = createMessage(part)

        val text = attachmentTextExtractor.extractText(message)

        assertThat(text).isEqualTo("document.pdf\nText of PDF document")
    }

    @Test
    fun `attachment with unknown type is skipped`() {
        val part = createAttachmentPart("image/png", "image.png", createBinaryBody("not an image"))
        val message = createMessage(part)

        val text = attachmentTextExtractor.extractText(message)

        assertThat(text).isNull()
    }

    @Test
    fun `attachment that is too large is skipped`() {
        val body = createBinaryBody(ByteArray(MAX_ATTACHMENT_SIZE + 1))
        val part = createAttachmentPart("text/plain", "large.txt", body)
        val message = createMessage(part)

        val text = attachmentTextExtractor.extractText(message)

        assertThat(text).isNull()
    }

    @Test
    fun `attachment with content not available on device is skipped`() {
        val part = createAttachmentPart("text/plain", "notes.txt", null)
        val message = createMessage(part)

        val text = attachmentTextExtractor.extractText(message)

        assertThat(text).isNull()
    }

    @Test
    fun `message_rfc822 attachment is skipped`() {
        val nestedMessage = MessageCreationHelper.createTextMessage("text/plain", "Hello world")
        val part = createAttachmentPart("message/rfc822", "message.eml", nestedMessage)
        val message = createMessage(part)

        val text = attachmentTextExtractor.extractText(message)

        assertThat(text).isNull()
    }

    @Test
    fun `text of multiple attachments is separated by newline`() {
        val textPart = createAttachmentPart("text/plain", "notes.txt", TextBody("Some text"))
        val htmlPart = createAttachmentPart("text/html", "page.html", TextBody("<p>Hello world</p>"))
        val message = createMessage(textPart, htmlPart)

        val text = attachmentTextExtractor.extractText(message)

        assertThat(text).isEqualTo("notes.txt\nSome text\npage.html\nHello world")
    }

    private fun createMessage(vararg parts: BodyPart): Message {
        return MessageCreationHelper.createMultipartMessage("multipart/mixed", *parts)
    }

    private fun createAttachmentPart(mimeType: String, fileName: String, body: Body?): BodyPart {
        val part = MimeBodyPart(body, mimeType)
        part.setHeader("Content-Disposition", "attachment; filename=\"$fileName\"")
        return part
    }

    private fun createBinaryBody(content: String): BinaryMemoryBody {
        return createBinaryBody(content.toByteArray())
    }

    private fun createBinaryBody(content: ByteArray): BinaryMemoryBody {
        return BinaryMemoryBody(content, "binary")
    }

    private fun createZipBody(vararg entries: Pair<String, String>): BinaryMemoryBody {
        val outputStream = ByteArrayOutputStream()
        ZipOutputStream(outputStream).use { zipOutputStream ->
            for ((name, content) in entries) {
                zipOutputStream.putNextEntry(ZipEntry(name))
                zipOutputStream.write(content.toByteArray())
                zipOutputStream.closeEntry()
            }
        }
        return createBinaryBody(outputStream.toByteArray())
    }

    private class FakePdfTextReader : PdfTextReader {
        override fun readText(input: InputStream): String? {
            return "Text of PDF document"
        }
    }

    private companion object {
        const val MAX_ATTACHMENT_SIZE = 25 * 1024 * 1024
        const val DOCX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        const val XLSX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        const val PPTX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    }
}
