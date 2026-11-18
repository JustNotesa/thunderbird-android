package com.fsck.k9.ui.settings.account

import android.util.Base64
import net.thunderbird.feature.mail.folder.FolderType
import net.thunderbird.feature.mail.folder.api.RemoteFolder

private const val INBOX_SERVER_ID = "INBOX"
private const val LAST_ASCII_CODE = 127
private const val BASE64_BLOCK_SIZE = 4
private const val UTF16_BYTES_PER_CHAR = 2

internal data class FolderPathSegment(val name: String, val type: FolderType)

/**
 * Splits a folder's server path into its hierarchy levels, so the folder settings can show where a folder lives and
 * which levels are special folders.
 */
internal class FolderPathFormatter(
    folders: List<RemoteFolder>,
    private val pathDelimiter: String,
    private val useServerPath: Boolean,
) {
    private val folderTypes: Map<String, FolderType> = folders.associate { it.serverId.normalized() to it.type }

    fun segments(folder: RemoteFolder): List<FolderPathSegment> {
        if (!useServerPath) return listOf(FolderPathSegment(folder.name, folder.type))

        val parts = splitServerId(folder.serverId)
        return parts.mapIndexed { index, part ->
            val type = if (index == parts.lastIndex) {
                folder.type
            } else {
                val parentServerId = parts.subList(0, index + 1).joinToString(pathDelimiter)
                folderTypes[parentServerId.normalized()] ?: FolderType.REGULAR
            }
            FolderPathSegment(name = decodeModifiedUtf7(part), type = type)
        }
    }

    fun serverLabel(folder: RemoteFolder): String? = when (folder.type) {
        FolderType.ARCHIVE -> "\\Archive"
        FolderType.DRAFTS -> "\\Drafts"
        FolderType.SENT -> "\\Sent"
        FolderType.SPAM -> "\\Junk"
        FolderType.TRASH -> "\\Trash"
        FolderType.REGULAR, FolderType.INBOX, FolderType.OUTBOX -> null
    }

    private fun splitServerId(serverId: String): List<String> {
        val parts = if (pathDelimiter.isEmpty()) listOf(serverId) else serverId.split(pathDelimiter)
        return parts.filter { it.isNotEmpty() }.ifEmpty { listOf(serverId) }
    }

    private fun String.normalized(): String {
        return if (equals(INBOX_SERVER_ID, ignoreCase = true) || startsWith(INBOX_SERVER_ID + pathDelimiter, true)) {
            INBOX_SERVER_ID + substring(INBOX_SERVER_ID.length)
        } else {
            this
        }
    }
}

/**
 * Decodes a folder name in the modified UTF-7 of RFC 3501, section 5.1.3. Returns the input unchanged when it is not
 * correctly encoded.
 */
internal fun decodeModifiedUtf7(encoded: String): String {
    if (encoded.any { it.code > LAST_ASCII_CODE } || '&' !in encoded) return encoded
    return runCatching { decodeShifts(encoded) }.getOrDefault(encoded)
}

private fun decodeShifts(encoded: String): String {
    val result = StringBuilder()
    var index = 0
    while (index < encoded.length) {
        val char = encoded[index]
        if (char != '&') {
            result.append(char)
            index++
        } else {
            val end = encoded.indexOf('-', startIndex = index + 1)
            require(end != -1) { "Unterminated shift sequence" }
            val base64 = encoded.substring(index + 1, end)
            result.append(if (base64.isEmpty()) "&" else decodeUtf16Base64(base64))
            index = end + 1
        }
    }
    return result.toString()
}

private fun decodeUtf16Base64(base64: String): String {
    val padding = "=".repeat((BASE64_BLOCK_SIZE - base64.length % BASE64_BLOCK_SIZE) % BASE64_BLOCK_SIZE)
    val bytes = Base64.decode(base64.replace(',', '/') + padding, Base64.DEFAULT)
    require(bytes.size % UTF16_BYTES_PER_CHAR == 0) { "Incomplete UTF-16 character" }
    return String(bytes, Charsets.UTF_16BE)
}
