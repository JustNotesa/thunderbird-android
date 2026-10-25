package com.fsck.k9.notification.sound

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.annotation.RequiresApi
import java.io.IOException
import net.thunderbird.legacy.logging.Log

private const val TAG = "NovaSoundFiles"
private const val MAX_SYSTEM_SOUNDS = 200
private const val MAX_FILE_SIZE = 5L * 1024L * 1024L
private const val COPY_BUFFER_SIZE = 8 * 1024
private const val DEFAULT_MIME_TYPE = "audio/*"
private val IMPORT_RELATIVE_PATH = "${Environment.DIRECTORY_NOTIFICATIONS}/Thunderbird"

/**
 * The sound files that can be used as notification sounds.
 */
interface NovaSoundFiles {
    /**
     * The notification sounds of the device.
     *
     * @return a list of title to Uri pairs.
     */
    fun listSystemSounds(): List<Pair<String, String>>

    /**
     * Import a sound file the user picked.
     *
     * @return the choice pointing to the imported file, or `null` if the file couldn't be imported.
     */
    fun importCustomFile(source: Uri): NovaSoundChoice?

    /**
     * Whether importing a custom sound file is supported.
     */
    fun canImportCustomFile(): Boolean

    /**
     * The label of the sound, e.g. the title of a system sound.
     *
     * @return the label, or `null` if the sound doesn't have one.
     */
    fun labelFor(choice: NovaSoundChoice): String?
}

/**
 * [NovaSoundFiles] implementation using the system sounds and the MediaStore.
 */
internal class DefaultNovaSoundFiles(private val context: Context) : NovaSoundFiles {
    @Suppress("TooGenericExceptionCaught")
    override fun listSystemSounds(): List<Pair<String, String>> {
        return try {
            readSystemSounds().sortedBy { it.first }
        } catch (e: Exception) {
            Log.e(TAG, e, "Failed to list the system sounds")
            emptyList()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun importCustomFile(source: Uri): NovaSoundChoice? {
        if (!canImportCustomFile()) return null

        return try {
            importSoundFile(source)
        } catch (e: Exception) {
            Log.e(TAG, e, "Failed to import the sound file")
            null
        }
    }

    override fun canImportCustomFile(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    @Suppress("TooGenericExceptionCaught")
    override fun labelFor(choice: NovaSoundChoice): String? {
        val uri = when (choice) {
            is NovaSoundChoice.System -> choice.uri
            is NovaSoundChoice.File -> choice.uri
            else -> return null
        }

        return try {
            val ringtone: Ringtone? = RingtoneManager.getRingtone(context, Uri.parse(uri))
            ringtone?.getTitle(context)
        } catch (e: Exception) {
            Log.e(TAG, e, "Failed to get the label of the sound")
            null
        }
    }

    private fun readSystemSounds(): List<Pair<String, String>> {
        val ringtoneManager = RingtoneManager(context).apply {
            setType(RingtoneManager.TYPE_NOTIFICATION)
        }
        val cursor = ringtoneManager.cursor ?: return emptyList()

        return cursor.use { ringtoneCursor ->
            buildList {
                while (ringtoneCursor.moveToNext() && size < MAX_SYSTEM_SOUNDS) {
                    val title = ringtoneCursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                    val uri = ringtoneManager.getRingtoneUri(ringtoneCursor.position).toString()
                    add(title to uri)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @Suppress("TooGenericExceptionCaught")
    private fun importSoundFile(source: Uri): NovaSoundChoice? {
        val resolver = context.contentResolver
        val displayName = getDisplayName(resolver, source) ?: return null
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = buildContentValues(displayName, resolver.getType(source))

        return resolver.insert(collection, values)?.let { newUri ->
            try {
                val choice = copySoundFile(resolver, newUri, source)
                if (choice == null) {
                    resolver.delete(newUri, null, null)
                }
                choice
            } catch (e: Exception) {
                resolver.delete(newUri, null, null)
                throw e
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun copySoundFile(resolver: ContentResolver, newUri: Uri, source: Uri): NovaSoundChoice? {
        val size = copyBytes(resolver, source, newUri)
        if (size > MAX_FILE_SIZE) return null

        val updateValues = ContentValues().apply {
            put(MediaStore.Audio.Media.IS_PENDING, 0)
        }
        resolver.update(newUri, updateValues, null, null)

        return NovaSoundChoice.File(newUri.toString())
    }

    private fun copyBytes(resolver: ContentResolver, source: Uri, target: Uri): Long {
        val input = resolver.openInputStream(source) ?: throw IOException("Could not open the source file")
        val output = resolver.openOutputStream(target) ?: throw IOException("Could not open the target file")

        return input.use { inputStream ->
            output.use { outputStream ->
                val buffer = ByteArray(COPY_BUFFER_SIZE)
                var totalBytes = 0L
                var count = inputStream.read(buffer)

                while (count != -1) {
                    outputStream.write(buffer, 0, count)
                    totalBytes += count
                    count = inputStream.read(buffer)
                }

                totalBytes
            }
        }
    }

    private fun getDisplayName(resolver: ContentResolver, source: Uri): String? {
        val cursor = resolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)

        return cursor?.use { nameCursor ->
            if (nameCursor.moveToFirst()) {
                nameCursor.getString(nameCursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            } else {
                null
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun buildContentValues(displayName: String, mimeType: String?): ContentValues {
        return ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType ?: DEFAULT_MIME_TYPE)
            put(MediaStore.Audio.Media.RELATIVE_PATH, IMPORT_RELATIVE_PATH)
            put(MediaStore.Audio.Media.IS_NOTIFICATION, 1)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
    }
}
