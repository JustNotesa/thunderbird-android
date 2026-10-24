package app.k9mail.feature.migration.qrcode.transfer

import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import java.io.IOException
import net.thunderbird.legacy.logging.Log
import okio.Buffer

private const val TAG = "TransferPayload"
private const val KEY_TYPE = "type"
private const val KEY_VERSION = "version"
private const val KEY_CREATED_AT = "createdAt"
private const val KEY_ACCOUNTS = "accounts"
private const val KEY_EXTENSIONS = "extensions"
private const val PAYLOAD_TYPE = "thunderbird-account-transfer"
private const val PAYLOAD_VERSION = 1

/**
 * Plaintext content of an account transfer: a JSON object holding the Thunderbird desktop export payload.
 *
 * The payload is versioned independently of the desktop format, so unknown fields - including unknown entries in
 * `extensions` - are ignored when reading.
 */
internal object TransferPayload {
    /**
     * Creates the JSON object that gets encrypted into the transfer container.
     *
     * The accounts payload is inserted verbatim. Turning it into a JSON value and back would turn its integers into
     * doubles, which the strict parsers of the other platforms reject.
     *
     * @param accountsJson Thunderbird desktop "Export to Mobile" payload, inserted as the value of `accounts`.
     * @param createdAt Creation time in Unix seconds.
     * @throws IllegalArgumentException if [accountsJson] isn't a JSON array.
     */
    fun write(accountsJson: String, createdAt: Long): String {
        requireJsonArray(accountsJson)

        val buffer = Buffer()
        JsonWriter.of(buffer).use { jsonWriter ->
            jsonWriter.beginObject()
            jsonWriter.name(KEY_TYPE).value(PAYLOAD_TYPE)
            jsonWriter.name(KEY_VERSION).value(PAYLOAD_VERSION.toLong())
            jsonWriter.name(KEY_CREATED_AT).value(createdAt)
            jsonWriter.name(KEY_ACCOUNTS).valueSink().writeUtf8(accountsJson).close()
            jsonWriter.name(KEY_EXTENSIONS).beginObject().endObject()
            jsonWriter.endObject()
        }

        return buffer.readUtf8()
    }

    /**
     * Reads the JSON object of a decrypted transfer container.
     *
     * @return [TransferPayloadContent] if the JSON was read successfully, `null` otherwise.
     */
    fun read(json: String): TransferPayloadContent? {
        return try {
            JsonReader.of(Buffer().writeUtf8(json)).use { readContent(it) }
        } catch (e: IllegalArgumentException) {
            Log.d(TAG, e, "Transfer payload failed validation")
            null
        } catch (e: IOException) {
            Log.d(TAG, e, "Failed to read transfer payload")
            null
        }
    }

    private fun readContent(jsonReader: JsonReader): TransferPayloadContent? {
        var type: String? = null
        var version: Int? = null
        var createdAt: Long? = null
        var accountsJson: String? = null

        jsonReader.beginObject()
        while (jsonReader.hasNext()) {
            when (jsonReader.nextName()) {
                KEY_TYPE -> type = jsonReader.nextString()
                KEY_VERSION -> version = jsonReader.nextInt()
                KEY_CREATED_AT -> createdAt = jsonReader.nextLong()
                KEY_ACCOUNTS -> accountsJson = readJsonArray(jsonReader)
                else -> jsonReader.skipValue()
            }
        }
        jsonReader.endObject()

        return if (isSupported(type = type, version = version)) {
            createContent(createdAt = createdAt, accountsJson = accountsJson)
        } else {
            Log.d(TAG, "Unsupported transfer payload: type %s, version %s", type, version)
            null
        }
    }

    private fun isSupported(type: String?, version: Int?): Boolean {
        return type == PAYLOAD_TYPE && version == PAYLOAD_VERSION
    }

    private fun createContent(createdAt: Long?, accountsJson: String?): TransferPayloadContent {
        val validCreatedAt = requireNotNull(createdAt) { "Transfer payload is missing the creation time" }
        val validAccounts = requireNotNull(accountsJson) { "Transfer payload is missing the accounts" }

        return TransferPayloadContent(createdAt = validCreatedAt, accountsJson = validAccounts)
    }

    private fun requireJsonArray(json: String) {
        val buffer = Buffer()
        buffer.writeUtf8(json)

        JsonReader.of(buffer).use { jsonReader ->
            require(jsonReader.peek() == JsonReader.Token.BEGIN_ARRAY) {
                "The accounts payload isn't a JSON array"
            }
        }
    }

    private fun readJsonArray(jsonReader: JsonReader): String {
        require(jsonReader.peek() == JsonReader.Token.BEGIN_ARRAY) {
            "The accounts of the transfer payload aren't a JSON array"
        }

        return jsonReader.nextSource().readUtf8()
    }
}

internal data class TransferPayloadContent(
    val createdAt: Long,
    val accountsJson: String,
)
