package com.fsck.k9.message

import java.util.UUID
import kotlin.io.encoding.Base64

private val DATA_IMAGE_SOURCE = Regex(
    """(\ssrc\s*=\s*)(["'])data:(image/[a-zA-Z0-9.+-]+);base64,([A-Za-z0-9+/=\s]*)\2""",
    RegexOption.IGNORE_CASE,
)

/**
 * An image of a signature that is sent as its own part of the message, referred to from the HTML as `cid:contentId`.
 */
class SignatureImagePart(
    val contentId: String,
    val contentType: String,
    val fileName: String,
    val data: ByteArray,
)

/**
 * A signature whose embedded images ([html] before) are replaced by references to [parts].
 */
class ExtractedSignature(
    val html: String,
    val parts: List<SignatureImagePart>,
)

/**
 * Many mail clients don't show images embedded as `data:` addresses, so the images of an HTML signature are sent as
 * parts of a `multipart/related` message and the HTML refers to them by their Content-ID.
 */
class SignatureImageExtractor(
    private val contentIdGenerator: () -> String = { "signature-image-${UUID.randomUUID()}@thunderbird.local" },
) {
    fun extract(html: String): ExtractedSignature {
        val parts = mutableListOf<SignatureImagePart>()

        val rewritten = DATA_IMAGE_SOURCE.replace(html) { match ->
            val data = decode(match.groupValues[4])
            if (data == null) {
                match.value
            } else {
                val contentId = contentIdGenerator()
                val contentType = match.groupValues[3].lowercase()
                parts += SignatureImagePart(
                    contentId = contentId,
                    contentType = contentType,
                    fileName = "signature-image-${parts.size + 1}.${extensionOf(contentType)}",
                    data = data,
                )
                "${match.groupValues[1]}${match.groupValues[2]}cid:$contentId${match.groupValues[2]}"
            }
        }

        return ExtractedSignature(html = rewritten, parts = parts)
    }

    private fun decode(base64: String): ByteArray? {
        return try {
            Base64.Mime.decode(base64.filterNot { it.isWhitespace() }).takeIf { it.isNotEmpty() }
        } catch (@Suppress("SwallowedException") e: IllegalArgumentException) {
            // The image stays embedded as it is, which is better than losing it.
            null
        }
    }

    private fun extensionOf(contentType: String): String {
        return when (contentType.substringAfter('/').substringBefore('+')) {
            "jpeg", "jpg" -> "jpg"
            "svg" -> "svg"
            else -> contentType.substringAfter('/').substringBefore('+')
        }
    }
}
