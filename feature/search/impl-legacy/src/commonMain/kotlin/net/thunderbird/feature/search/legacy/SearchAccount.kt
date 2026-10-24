package net.thunderbird.feature.search.legacy

import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.UnifiedAccountId
import net.thunderbird.feature.mail.folder.FolderType
import net.thunderbird.feature.search.legacy.api.MessageSearchField
import net.thunderbird.feature.search.legacy.api.SearchAttribute

/**
 * This class is basically a wrapper around a LocalSearch. It allows to expose it as an account.
 * This is a meta-account containing all the messages that match the search.
 */
class SearchAccount(
    val id: AccountId,
    search: LocalMessageSearch,
    val name: String,
    val email: String,
) {
    val relatedSearch: LocalMessageSearch = search

    companion object {
        /**
         * Create a unified search for the special folder of the given [folderType] in every account.
         *
         * The folder of a unified search is resolved per account, because every account has its own folder ids. Use
         * `LocalMessageSearch.unifiedSpecialFolderType` and `LocalMessageSearch.conditionsForAccount()` to evaluate the
         * search for a single account.
         *
         * @throws IllegalArgumentException if [folderType] isn't a special folder that can be unified.
         */
        @JvmStatic
        fun createUnifiedSearch(
            folderType: FolderType,
            title: String,
            detail: String,
        ): SearchAccount {
            if (folderType == FolderType.INBOX) {
                return createUnifiedInboxSearch(title, detail)
            }

            val folderSelection = when (folderType) {
                FolderType.DRAFTS, FolderType.SENT, FolderType.ARCHIVE, FolderType.SPAM, FolderType.TRASH -> {
                    UnifiedFolderSelection.Special(folderType)
                }

                FolderType.INBOX, FolderType.OUTBOX, FolderType.REGULAR -> {
                    throw IllegalArgumentException("Unsupported unified folder type: $folderType")
                }
            }

            val search = LocalMessageSearch().apply {
                id = UnifiedAccountId
                type = LocalMessageSearchType.Unified(folderSelection)
            }

            return SearchAccount(
                id = UnifiedAccountId,
                search = search,
                name = title,
                email = detail,
            )
        }

        @JvmStatic
        fun createUnifiedInboxSearch(
            title: String,
            detail: String,
        ): SearchAccount {
            val tmpSearch = LocalMessageSearch().apply {
                id = UnifiedAccountId
                type = LocalMessageSearchType.Unified(UnifiedFolderSelection.Special(FolderType.INBOX))
                // The ingrate field is used to identify the unified folders.
                and(MessageSearchField.INTEGRATE, "1", SearchAttribute.EQUALS)
            }

            return SearchAccount(
                id = UnifiedAccountId,
                search = tmpSearch,
                name = title,
                email = detail,
            )
        }
    }
}
