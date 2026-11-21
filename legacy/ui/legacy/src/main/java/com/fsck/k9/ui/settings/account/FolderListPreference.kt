package com.fsck.k9.ui.settings.account

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.util.TypedValue
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

    /**
     * Optional explanation shown in a smaller, secondary-colored line below the summary.
     */
    var summaryHint: CharSequence? = null
        set(value) {
            field = value
            notifyChanged()
        }

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
        if (entries.isEmpty()) return PLACEHOLDER_SUMMARY

        val folderSummary = when {
            value == NO_FOLDER_SELECTED_VALUE -> noFolderSelectedName
            value.startsWith(AUTOMATIC_PREFIX) -> automaticFolderOption
            else -> entry ?: super.getSummary()
        }

        val hint = summaryHint
        return if (hint.isNullOrEmpty()) folderSummary else appendHint(folderSummary, hint)
    }

    private fun appendHint(summary: CharSequence?, hint: CharSequence): CharSequence {
        val hintStart = (summary?.length ?: 0) + 1
        return SpannableStringBuilder().apply {
            if (summary != null) append(summary)
            append("\n")
            append(hint)
            setSpan(RelativeSizeSpan(HINT_RELATIVE_SIZE), hintStart, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            resolveSecondaryTextColor()?.let {
                setSpan(ForegroundColorSpan(it), hintStart, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    private fun resolveSecondaryTextColor(): Int? {
        val typedValue = TypedValue()
        if (!context.theme.resolveAttribute(android.R.attr.textColorSecondary, typedValue, true)) return null

        return when {
            typedValue.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT -> typedValue.data
            typedValue.resourceId != 0 -> runCatching { context.getColorStateList(typedValue.resourceId).defaultColor }
                .getOrNull()
            else -> null
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
        private const val HINT_RELATIVE_SIZE = 0.9f
        private val FORMAT_PLACEHOLDER = Regex("%(1\\$)?s")
    }
}
