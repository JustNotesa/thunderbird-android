package com.fsck.k9.mailstore

import java.util.Locale
import net.thunderbird.feature.mail.folder.FolderType
import net.thunderbird.feature.mail.folder.api.RemoteFolder

/**
 * Implements the automatic special folder selection strategy.
 *
 * The special folder is selected in two steps:
 *
 * 1. The first folder whose [RemoteFolder.type] is the requested [FolderType]. The type is set by the server, e.g. from
 *    the IMAP SPECIAL-USE attributes.
 * 2. If the server doesn't mark any folder with that type, fall back to the folder name. Servers without support for
 *    SPECIAL-USE (or without LSUB) report every folder as [FolderType.REGULAR], so only those folders are searched for
 *    one of the well-known names of the requested type. Folders nested inside another folder are never matched.
 */
class SpecialFolderSelectionStrategy {
    fun selectSpecialFolder(
        folders: List<RemoteFolder>,
        type: FolderType,
    ): RemoteFolder? {
        return folders.firstOrNull { folder -> folder.type == type }
            ?: selectFolderByName(folders, type)
    }

    /**
     * Returns the folder matching the first of the [WELL_KNOWN_NAMES] of [type] that is present in [folders].
     */
    private fun selectFolderByName(folders: List<RemoteFolder>, type: FolderType): RemoteFolder? {
        val names = WELL_KNOWN_NAMES[type].orEmpty()
        val regularFolders = folders.filter { folder -> folder.type == FolderType.REGULAR }

        for (name in names) {
            val folder = regularFolders.findFolderByName(name)
            if (folder != null) return folder
        }

        return null
    }

    /**
     * Finds the folder named [name], preferring a top level folder over one inside the inbox.
     */
    private fun List<RemoteFolder>.findFolderByName(name: String): RemoteFolder? {
        return filter { folder -> normalizeName(folder.name) == name }
            .minByOrNull { folder -> hasInboxPrefix(folder.name) }
    }

    /**
     * Returns the name without surrounding whitespace and without the inbox prefix, or `null` if the folder is nested
     * deeper than the inbox.
     */
    private fun normalizeName(name: String): String? {
        val nameWithoutInboxPrefix = removeInboxPrefix(name.trim())
        val isNested = SEPARATOR_CHARACTERS.any { separator -> nameWithoutInboxPrefix.contains(separator) }
        return if (isNested) null else nameWithoutInboxPrefix.lowercase(Locale.ROOT)
    }

    private fun removeInboxPrefix(name: String): String {
        return INBOX_PREFIXES.firstOrNull { prefix -> name.startsWith(prefix, ignoreCase = true) }
            ?.let { prefix -> name.substring(prefix.length) }
            ?: name
    }

    private fun hasInboxPrefix(name: String): Boolean {
        return INBOX_PREFIXES.any { prefix -> name.trim().startsWith(prefix, ignoreCase = true) }
    }

    private companion object {
        private val INBOX_PREFIXES = listOf("inbox.", "inbox/")
        private val SEPARATOR_CHARACTERS = listOf('.', '/')

        /** Well-known folder names in lower case, per type, in the order they are preferred in. */
        private val WELL_KNOWN_NAMES: Map<FolderType, List<String>> = mapOf(
            FolderType.SENT to listOf(
                "sent", "sent items", "sent messages", "sent mail", "gesendet", "gesendete elemente",
                "gesendete objekte", "gesendete nachrichten", "envoyés", "éléments envoyés", "enviados",
                "elementos enviados", "posta inviata", "inviata", "verzonden", "verzonden items",
            ),
            FolderType.DRAFTS to listOf(
                "drafts", "draft", "entwürfe", "entwurf", "brouillons", "borradores", "bozze", "concepten",
            ),
            FolderType.TRASH to listOf(
                "trash", "deleted", "deleted items", "deleted messages", "bin", "papierkorb", "gelöscht",
                "gelöschte elemente", "gelöschte objekte", "corbeille", "papelera", "cestino", "prullenbak",
            ),
            FolderType.SPAM to listOf(
                "spam", "junk", "junk e-mail", "junk email", "junk-e-mail", "bulk mail", "courrier indésirable",
                "indésirables", "correo no deseado", "posta indesiderata", "ongewenst",
            ),
            FolderType.ARCHIVE to listOf(
                "archive", "archives", "archiv", "archivo", "archivio", "archief",
            ),
        )
    }
}
