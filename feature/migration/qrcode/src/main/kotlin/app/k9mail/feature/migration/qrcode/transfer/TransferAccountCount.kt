package app.k9mail.feature.migration.qrcode.transfer

import com.squareup.moshi.JsonReader
import java.io.IOException
import net.thunderbird.legacy.logging.Log
import okio.Buffer

/** The payload starts with its version and its miscellaneous entry, everything after them is an account. */
private const val PAYLOAD_HEADER_ENTRIES = 2

/** An account is the entry of its incoming server followed by the entry that holds its outgoing servers. */
private const val ENTRIES_PER_ACCOUNT = 2

/**
 * Counts the accounts of the desktop payload of an account transfer, so the screens can name the amount of accounts
 * they got without showing any of their content.
 *
 * @return The number of accounts, or `0` when the payload can't be read.
 */
internal fun countTransferAccounts(accountsJson: String): Int {
    return try {
        JsonReader.of(Buffer().writeUtf8(accountsJson)).use { jsonReader -> countAccounts(jsonReader) }
    } catch (e: IllegalArgumentException) {
        Log.d(e, "Transfer payload failed validation")
        0
    } catch (e: IOException) {
        Log.d(e, "Failed to read transfer payload")
        0
    }
}

private fun countAccounts(jsonReader: JsonReader): Int {
    var entries = 0

    jsonReader.beginArray()
    while (jsonReader.hasNext()) {
        jsonReader.skipValue()
        entries++
    }
    jsonReader.endArray()

    return ((entries - PAYLOAD_HEADER_ENTRIES).coerceAtLeast(0)) / ENTRIES_PER_ACCOUNT
}
