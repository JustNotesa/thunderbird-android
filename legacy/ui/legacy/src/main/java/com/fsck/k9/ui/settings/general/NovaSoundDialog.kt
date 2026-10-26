package com.fsck.k9.ui.settings.general

import android.content.Context
import android.content.DialogInterface
import android.net.Uri
import androidx.preference.Preference
import com.fsck.k9.notification.sound.NovaSound
import com.fsck.k9.notification.sound.NovaSoundChoice
import com.fsck.k9.notification.sound.NovaSoundManager
import com.fsck.k9.ui.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The notification sound setting that is being changed.
 */
enum class NovaSoundTarget {
    NEW_MAIL,
    SENT,
}

/**
 * A sound the user can select in the sound dialog.
 *
 * A `null` choice means the user wants to pick a sound file.
 */
private data class SoundEntry(val label: String, val choice: NovaSoundChoice?)

/**
 * Dialog to select the sound played for new messages or after sending a message.
 *
 * Picking a sound is applied right away and previewed. Picking a custom sound file is delegated to the caller.
 */
class NovaSoundDialog(
    private val context: Context,
    private val soundManager: NovaSoundManager,
    private val scope: CoroutineScope,
    private val onPickFile: () -> Unit,
    private val onImportFailed: () -> Unit,
) {
    private var pendingTarget: NovaSoundTarget? = null
    private var pendingPreference: Preference? = null

    /**
     * Show the dialog to select the sound used by the given preference.
     */
    fun show(target: NovaSoundTarget, preference: Preference) {
        val choice = currentChoice(target)

        scope.launch {
            val entries = withContext(Dispatchers.IO) { buildEntries(choice) }
            showDialog(target, preference, choice, entries)
        }
    }

    /**
     * Import the sound file the user picked and use it for the preference that was changed.
     */
    fun importPickedFile(uri: Uri) {
        val target = pendingTarget ?: return
        val preference = pendingPreference ?: return

        scope.launch {
            val choice = withContext(Dispatchers.IO) { soundManager.importCustomFile(uri) }
            if (choice == null) {
                onImportFailed()
            } else {
                applyChoice(target, choice)
                updateSummary(preference, target)
            }
        }
    }

    /**
     * Update the summary of the preference to show the sound that is currently used.
     */
    fun updateSummary(preference: Preference, target: NovaSoundTarget) {
        val choice = currentChoice(target)

        scope.launch {
            val summary = withContext(Dispatchers.IO) { buildSummary(target, choice) }
            preference.summary = summary
        }
    }

    private fun showDialog(
        target: NovaSoundTarget,
        preference: Preference,
        currentChoice: NovaSoundChoice,
        entries: List<SoundEntry>,
    ) {
        val labels = entries.map { it.label }.toTypedArray()
        val checkedIndex = entries.indexOfFirst { it.choice == currentChoice }

        MaterialAlertDialogBuilder(context)
            .setTitle(preference.title)
            .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                val entry = entries.getOrNull(which)
                if (entry != null) {
                    onEntrySelected(target, preference, entry, dialog)
                }
            }
            .setPositiveButton(R.string.nova_sound_done, null)
            .show()
    }

    private fun onEntrySelected(
        target: NovaSoundTarget,
        preference: Preference,
        entry: SoundEntry,
        dialog: DialogInterface,
    ) {
        val choice = entry.choice
        if (choice == null) {
            pendingTarget = target
            pendingPreference = preference
            dialog.dismiss()
            onPickFile()
        } else {
            applyChoice(target, choice)
            updateSummary(preference, target)
        }
    }

    private fun applyChoice(target: NovaSoundTarget, choice: NovaSoundChoice) {
        when (target) {
            NovaSoundTarget.NEW_MAIL -> soundManager.setNewMail(choice)
            NovaSoundTarget.SENT -> soundManager.setSent(choice)
        }

        soundManager.preview(choice)
    }

    private fun currentChoice(target: NovaSoundTarget): NovaSoundChoice {
        return when (target) {
            NovaSoundTarget.NEW_MAIL -> soundManager.currentNewMail()
            NovaSoundTarget.SENT -> soundManager.currentSent()
        }
    }

    private fun buildEntries(currentChoice: NovaSoundChoice): List<SoundEntry> {
        val entries = mutableListOf(
            SoundEntry(context.getString(R.string.nova_sound_off), NovaSoundChoice.Off),
            SoundEntry(context.getString(R.string.nova_sound_nova_new_mail), NovaSoundChoice.Nova(NovaSound.NEW_MAIL)),
            SoundEntry(context.getString(R.string.nova_sound_nova_notice), NovaSoundChoice.Nova(NovaSound.NOTICE)),
            SoundEntry(context.getString(R.string.nova_sound_nova_sent), NovaSoundChoice.Nova(NovaSound.SENT)),
        )

        if (currentChoice is NovaSoundChoice.File) {
            val label = soundManager.labelFor(currentChoice)
            if (label != null) {
                entries.add(SoundEntry(context.getString(R.string.nova_sound_custom, label), currentChoice))
            }
        }

        for ((title, uri) in soundManager.listSystemSounds()) {
            entries.add(SoundEntry(context.getString(R.string.nova_sound_system, title), NovaSoundChoice.System(uri)))
        }

        if (soundManager.canImportCustomFile()) {
            entries.add(SoundEntry(context.getString(R.string.nova_sound_pick_file), null))
        }

        return entries
    }

    private fun buildSummary(target: NovaSoundTarget, choice: NovaSoundChoice): String {
        val soundLabel = when (choice) {
            is NovaSoundChoice.Off -> context.getString(R.string.nova_sound_off)
            is NovaSoundChoice.Nova -> context.getString(novaSoundLabel(choice.sound))
            is NovaSoundChoice.System -> context.getString(
                R.string.nova_sound_system,
                soundManager.labelFor(choice) ?: choice.uri,
            )
            is NovaSoundChoice.File -> context.getString(
                R.string.nova_sound_custom,
                soundManager.labelFor(choice) ?: choice.uri,
            )
        }

        return if (target == NovaSoundTarget.NEW_MAIL) {
            context.getString(R.string.nova_sound_all_accounts, soundLabel)
        } else {
            soundLabel
        }
    }

    private fun novaSoundLabel(sound: NovaSound): Int {
        return when (sound) {
            NovaSound.NEW_MAIL -> R.string.nova_sound_nova_new_mail
            NovaSound.NOTICE -> R.string.nova_sound_nova_notice
            NovaSound.SENT -> R.string.nova_sound_nova_sent
        }
    }
}
