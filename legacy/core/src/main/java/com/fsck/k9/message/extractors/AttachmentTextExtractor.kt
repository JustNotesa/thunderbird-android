package com.fsck.k9.message.extractors

import android.util.Xml
import com.fsck.k9.mail.Body
import com.fsck.k9.mail.Message
import com.fsck.k9.mail.Part
import com.fsck.k9.mail.internet.MessageExtractor
import com.fsck.k9.mail.internet.MimeUtility
import com.fsck.k9.mail.internet.charset
import com.fsck.k9.message.html.HtmlConverter
import net.thunderbird.core.common.exception.MessagingException
import net.thunderbird.legacy.logging.Log
import org.apache.commons.io.input.BoundedInputStream
import org.apache.james.mime4j.Charsets
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

private const val TAG = "AttachmentTextExtractor"
private const val MAX_ATTACHMENT_SIZE = 25L * 1024 * 1024
private const val MAX_ATTACHMENT_TEXT_LENGTH = 200 * 1024
private const val MAX_TEXT_LENGTH = 1024 * 1024
private const val DOCX_DOCUMENT_ENTRY = "word/document.xml"
private const val XLSX_SHARED_STRINGS_ENTRY = "xl/sharedStrings.xml"
private const val PPTX_SLIDE_PREFIX = "ppt/slides/slide"
private const val XML_SUFFIX = ".xml"

/**
 * Extracts the text of the attachments of a message that are available on the device.
 */
class AttachmentTextExtractor(private val pdfTextReader: PdfTextReader) {
    /**
     * Extract the text of all attachments of [message].
     *
     * @return The text of the attachments or `null` if no text was found.
     */
    fun extractText(message: Message): String? {
        val attachments = mutableListOf<Part>()
        try {
            MessageExtractor.findViewablesAndAttachments(message, null, attachments)
        } catch (e: MessagingException) {
            Log.w(TAG, e, "Unable to find attachments of message")
            return null
        }

        val text = StringBuilder()
        for (attachment in attachments) {
            val attachmentText = extractTextFromAttachment(attachment)
            if (attachmentText != null) {
                if (text.isNotEmpty()) {
                    text.append('\n')
                }
                text.append(attachmentText)
            }

            if (text.length >= MAX_TEXT_LENGTH) {
                break
            }
        }

        return text.toString().takeIf { it.isNotEmpty() }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun extractTextFromAttachment(part: Part): String? {
        val body = part.body ?: return null
        val attachmentType = getAttachmentType(part)

        return if (attachmentType == AttachmentType.UNKNOWN) {
            null
        } else {
            val text = try {
                readAttachmentText(part, body, attachmentType)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to extract text from attachment with MIME type %s", part.mimeType)
                null
            }

            text?.let {
                val limitedText = it.take(MAX_ATTACHMENT_TEXT_LENGTH)
                val fileName = getFileName(part)
                if (fileName != null) "$fileName\n$limitedText" else limitedText
            }
        }
    }

    private fun readAttachmentText(part: Part, body: Body, attachmentType: AttachmentType): String? {
        val input = MimeUtility.decodeBody(body)
        try {
            val boundedInput = BoundedInputStream(input, MAX_ATTACHMENT_SIZE + 1)
            val text = readText(attachmentType, part, boundedInput)
            if (boundedInput.count > MAX_ATTACHMENT_SIZE) {
                return null
            }
            return text
        } finally {
            MimeUtility.closeInputStreamWithoutDeletingTemporaryFiles(input)
        }
    }

    private fun readText(attachmentType: AttachmentType, part: Part, input: BoundedInputStream): String? {
        return when (attachmentType) {
            AttachmentType.TEXT -> readPlainText(input, getCharset(part))
            AttachmentType.HTML -> readPlainText(input, getCharset(part))?.let { HtmlConverter.htmlToText(it) }
            AttachmentType.DOCX -> readZipText(input) { it == DOCX_DOCUMENT_ENTRY }
            AttachmentType.XLSX -> readZipText(input) { it == XLSX_SHARED_STRINGS_ENTRY }
            AttachmentType.PPTX -> readZipText(input) { it.startsWith(PPTX_SLIDE_PREFIX) && it.endsWith(XML_SUFFIX) }
            AttachmentType.PDF -> pdfTextReader.readText(input)
            AttachmentType.UNKNOWN -> null
        }
    }

    private fun readPlainText(input: BoundedInputStream, charset: Charset?): String? {
        val bytes = input.readBytes()
        if (input.count > MAX_ATTACHMENT_SIZE) {
            return null
        }
        return charset?.let { String(bytes, it) }
    }

    private fun readZipText(input: BoundedInputStream, isRelevantEntry: (String) -> Boolean): String? {
        val entryTexts = mutableListOf<String>()

        ZipInputStream(input).use { zipInputStream ->
            var entry = zipInputStream.nextEntry
            while (entry != null) {
                if (isRelevantEntry(entry.name)) {
                    val text = readXmlText(zipInputStream)
                    if (text.isNotEmpty()) {
                        entryTexts += text
                    }
                }
                entry = zipInputStream.nextEntry
            }
        }

        return entryTexts.joinToString(" ").takeIf { it.isNotEmpty() }
    }

    private fun readXmlText(input: InputStream): String {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        val textNodes = mutableListOf<String>()
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.TEXT || eventType == XmlPullParser.CDSECT) {
                val text = parser.text?.trim()
                if (!text.isNullOrEmpty()) {
                    textNodes += text
                }
            }
            eventType = parser.next()
        }

        return textNodes.joinToString(" ")
    }

    private fun getAttachmentType(part: Part): AttachmentType {
        val mimeType = part.mimeType
        if (MimeUtility.isMessageType(mimeType)) {
            return AttachmentType.UNKNOWN
        }

        val type = mimeType?.let { MIME_TYPE_TO_TYPE[it.lowercase()] }
        val extension = getFileName(part)?.substringAfterLast('.', "")?.lowercase()
        return type ?: extension?.let { EXTENSION_TO_TYPE[it] } ?: AttachmentType.UNKNOWN
    }

    private fun getFileName(part: Part): String? {
        val disposition = part.disposition
        val contentType = part.contentType
        return MimeUtility.getHeaderParameter(disposition, "filename")
            ?: MimeUtility.getHeaderParameter(contentType, "name")
    }

    /**
     * The charset of the attachment, or UTF-8 when it doesn't have one or the app doesn't know it.
     */
    private fun getCharset(part: Part): Charset {
        val charsetName = part.charset ?: "UTF-8"

        return try {
            Charset.forName(charsetName)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, e, "Unknown charset of attachment with MIME type %s, using UTF-8", part.mimeType)
            Charsets.UTF_8
        }
    }
}

private enum class AttachmentType {
    TEXT,
    HTML,
    DOCX,
    XLSX,
    PPTX,
    PDF,
    UNKNOWN,
}

private val MIME_TYPE_TO_TYPE = mapOf(
    "text/plain" to AttachmentType.TEXT,
    "text/csv" to AttachmentType.TEXT,
    "text/markdown" to AttachmentType.TEXT,
    "text/html" to AttachmentType.HTML,
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to AttachmentType.DOCX,
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to AttachmentType.XLSX,
    "application/vnd.openxmlformats-officedocument.presentationml.presentation" to AttachmentType.PPTX,
    "application/pdf" to AttachmentType.PDF,
)

private val EXTENSION_TO_TYPE = mapOf(
    "txt" to AttachmentType.TEXT,
    "csv" to AttachmentType.TEXT,
    "md" to AttachmentType.TEXT,
    "log" to AttachmentType.TEXT,
    "htm" to AttachmentType.HTML,
    "html" to AttachmentType.HTML,
    "docx" to AttachmentType.DOCX,
    "xlsx" to AttachmentType.XLSX,
    "pptx" to AttachmentType.PPTX,
    "pdf" to AttachmentType.PDF,
)
