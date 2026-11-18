package com.fsck.k9.ui.settings.account

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.util.AttributeSet
import androidx.core.content.res.TypedArrayUtils
import androidx.preference.ListPreference
import com.fsck.k9.ui.R
import net.thunderbird.feature.mail.folder.api.RemoteFolder

/**
 * A [ListPreference] that allows selecting one of an account's folders.
 */
@SuppressLint("RestrictedApi")
class FolderListPreference
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = TypedArrayUtils.getAttr(
        context,
        androidx.preference.R.attr.dialogPreferenceStyle,
        android.R.attr.dialogPreferenceStyle,
    ),
    defStyleRes: Int = 0,
) : ListPreference(context, attrs, defStyleAttr, defStyleRes) {
    private val noFolderSelectedName = context.getString(R.string.account_settings_no_folder_selected).italicize()
    private lateinit var automaticFolderOption: CharSequence

    init {
        entries = emptyArray()
        entryValues = emptyArray()
        isEnabled = false
    }

    fun setFolders(folders: List<RemoteFolder>, folderText: (RemoteFolder) -> CharSequence) {
        entries = (listOf(noFolderSelectedName) + folders.map(folderText)).toTypedArray()
        entryValues = (listOf(NO_FOLDER_SELECTED_VALUE) + getFolderValues(folders)).toTypedArray()
        isEnabled = true
    }

    fun setFolders(
        folders: List<RemoteFolder>,
        automaticFolder: RemoteFolder?,
        folderText: (RemoteFolder) -> CharSequence,
    ) {
        val automaticFolderName = if (automaticFolder != null) {
            folderText(automaticFolder)
        } else {
            context.getString(R.string.account_settings_no_folder_selected)
        }
        val automaticFolderValue = AUTOMATIC_PREFIX + (automaticFolder?.id?.toString() ?: NO_FOLDER_VALUE)

        automaticFolderOption = formatAutomaticFolderOption(automaticFolderName).italicize()

        entries = (listOf(automaticFolderOption) + noFolderSelectedName + folders.map(folderText)).toTypedArray()
        entryValues =
            (listOf(automaticFolderValue) + NO_FOLDER_SELECTED_VALUE + getFolderValues(folders)).toTypedArray()

        isEnabled = true
    }

    override fun getSummary(): CharSequence? {
        // While folders are being loaded the summary returned by ListPreference will be empty. This leads to the
        // summary view being hidden. Once folders are loaded the summary updates and the list height changes. This
        // adds quite a bit of visual clutter. We avoid that by returning a placeholder summary value.
        return when {
            entries.isEmpty() -> PLACEHOLDER_SUMMARY
            value == NO_FOLDER_SELECTED_VALUE -> noFolderSelectedName
            value.startsWith(AUTOMATIC_PREFIX) -> automaticFolderOption
            else -> entry ?: super.getSummary()
        }
    }

    /**
     * Inserts the folder text into the "Automatic (%s)" template without losing the folder text's spans.
     */
    private fun formatAutomaticFolderOption(folderName: CharSequence): CharSequence {
        val template = context.getString(R.string.account_settings_automatic_special_folder)
        val placeholder = FORMAT_PLACEHOLDER.find(template) ?: return template

        return SpannableStringBuilder(template)
            .replace(placeholder.range.first, placeholder.range.last + 1, folderName)
    }

    private fun getFolderValues(folders: List<RemoteFolder>) = folders.map { MANUAL_PREFIX + it.id.toString() }

    private fun CharSequence.italicize(): CharSequence {
        return SpannableString(this).apply { setSpan(StyleSpan(Typeface.ITALIC), 0, this.length, 0) }
    }

    companion object {
        const val FOLDER_VALUE_DELIMITER = "|"
        const val AUTOMATIC_PREFIX = "AUTOMATIC|"
        const val MANUAL_PREFIX = "MANUAL|"
        const val NO_FOLDER_VALUE = ""
        private const val NO_FOLDER_SELECTED_VALUE = MANUAL_PREFIX + NO_FOLDER_VALUE
        private const val PLACEHOLDER_SUMMARY = " "
        private val FORMAT_PLACEHOLDER = Regex("%(1\\$)?s")
    }
}
