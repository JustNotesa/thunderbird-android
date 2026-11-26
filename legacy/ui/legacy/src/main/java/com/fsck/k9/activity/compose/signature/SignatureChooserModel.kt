package com.fsck.k9.activity.compose.signature

import net.thunderbird.core.android.account.AccountSignature

private const val PREVIEW_LENGTH = 60
private val TAG = Regex("<[^>]*>")
private val BLOCK_END = Regex("(?i)</(p|div|li|tr|h[1-6])>|<br\\s*/?>")
private val WHITESPACE = Regex("\\s+")

/**
 * The entries of the sheet to choose a signature.
 */
data class SignatureChooserModel(
    val defaultEntry: Entry?,
    val savedEntries: List<Entry>,
    val currentSelection: Selection,
) {
    /** Shown when the account has no saved signature yet: the sheet explains and offers to create one. */
    val hasSavedSignatures: Boolean
        get() = savedEntries.isNotEmpty()

    data class Entry(val id: String?, val name: String, val preview: String, val isHtml: Boolean)

    sealed interface Selection {
        data object Default : Selection
        data class Saved(val id: String) : Selection
        data object None : Selection
        data object Custom : Selection
    }

    companion object {
        @JvmStatic
        fun of(
            saved: List<AccountSignature>,
            defaultText: String?,
            defaultIsHtml: Boolean,
            defaultName: String,
            chip: SignatureChipState,
        ): SignatureChooserModel {
            val savedEntries = saved.map { Entry(it.id, it.name, previewLine(it.text, it.isHtml), it.isHtml) }
            val defaultEntry = defaultText?.let {
                Entry(null, defaultName, previewLine(it, defaultIsHtml), defaultIsHtml)
            }
            val selection = when (chip.kind) {
                SignatureChipState.Kind.Default -> Selection.Default
                SignatureChipState.Kind.Saved -> chip.id?.let { Selection.Saved(it) } ?: Selection.Custom
                SignatureChipState.Kind.Custom -> Selection.Custom
                SignatureChipState.Kind.None, SignatureChipState.Kind.Create -> Selection.None
            }
            return SignatureChooserModel(defaultEntry, savedEntries, selection)
        }

        /**
         * One short line that tells signatures apart: the text without markup, line breaks and extra spaces.
         */
        @JvmStatic
        fun previewLine(text: String, isHtml: Boolean): String {
            val plain = if (isHtml) {
                decodeEntities(TAG.replace(BLOCK_END.replace(text, " · "), ""))
            } else {
                text
            }
            val line = WHITESPACE.replace(plain.replace(Regex("(\\s*·\\s*)+$"), ""), " ").trim()

            return if (line.length > PREVIEW_LENGTH) line.take(PREVIEW_LENGTH - 1).trimEnd() + "…" else line
        }

        private fun decodeEntities(text: String): String {
            return text
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&")
        }
    }
}
