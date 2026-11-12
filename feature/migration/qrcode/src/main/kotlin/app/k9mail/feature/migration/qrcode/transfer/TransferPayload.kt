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
private const val KEY_OAUTH_STATES = "oauthStates"
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
     * @param oAuthStates Sign-in states of the OAuth accounts, aligned by index with the accounts of the payload. The
     * states are secret, they are only written when at least one of them is set.
     * @throws IllegalArgumentException if [accountsJson] isn't a JSON array.
     */
    fun write(accountsJson: String, createdAt: Long, oAuthStates: List<String?> = emptyList()): String {
        requireJsonArray(accountsJson)

        val buffer = Buffer()
        JsonWriter.of(buffer).use { jsonWriter ->
            jsonWriter.beginObject()
            jsonWriter.name(KEY_TYPE).value(PAYLOAD_TYPE)
            jsonWriter.name(KEY_VERSION).value(PAYLOAD_VERSION.toLong())
            jsonWriter.name(KEY_CREATED_AT).value(createdAt)
            jsonWriter.name(KEY_ACCOUNTS).valueSink().writeUtf8(accountsJson).close()
            jsonWriter.name(KEY_EXTENSIONS).beginObject()
            writeOAuthStates(jsonWriter, oAuthStates)
            jsonWriter.endObject()
            jsonWriter.endObject()
        }

        return buffer.readUtf8()
    }

    /**
     * Writes the sign-in states of the OAuth accounts into the extensions of the payload. The states are aligned by
     * index with the accounts of the payload, `null` for the accounts that don't have one.
     */
    private fun writeOAuthStates(jsonWriter: JsonWriter, oAuthStates: List<String?>) {
        if (oAuthStates.any { it != null }) {
            jsonWriter.name(KEY_OAUTH_STATES).beginArray()
            oAuthStates.forEach { state ->
                if (state == null) {
                    jsonWriter.nullValue()
                } else {
                    jsonWriter.value(state)
                }
            }
            jsonWriter.endArray()
        }
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
        var oAuthStates: List<String?> = emptyList()

        jsonReader.beginObject()
        while (jsonReader.hasNext()) {
            when (jsonReader.nextName()) {
                KEY_TYPE -> type = jsonReader.nextString()
                KEY_VERSION -> version = jsonReader.nextInt()
                KEY_CREATED_AT -> createdAt = jsonReader.nextLong()
                KEY_ACCOUNTS -> accountsJson = readJsonArray(jsonReader)
                KEY_EXTENSIONS -> oAuthStates = readExtensions(jsonReader)
                else -> jsonReader.skipValue()
            }
        }
        jsonReader.endObject()

        return if (isSupported(type = type, version = version)) {
            createContent(createdAt = createdAt, accountsJson = accountsJson, oAuthStates = oAuthStates)
        } else {
            Log.d(TAG, "Unsupported transfer payload: type %s, version %s", type, version)
            null
        }
    }

    private fun isSupported(type: String?, version: Int?): Boolean {
        return type == PAYLOAD_TYPE && version == PAYLOAD_VERSION
    }

    private fun createContent(
        createdAt: Long?,
        accountsJson: String?,
        oAuthStates: List<String?>,
    ): TransferPayloadContent {
        val validCreatedAt = requireNotNull(createdAt) { "Transfer payload is missing the creation time" }
        val validAccounts = requireNotNull(accountsJson) { "Transfer payload is missing the accounts" }

        return TransferPayloadContent(
            createdAt = validCreatedAt,
            accountsJson = validAccounts,
            oAuthStates = oAuthStates,
        )
    }

    /**
     * Reads the extensions of the payload. Unknown extensions are ignored, an extension that can't be read is treated
     * as if it wasn't there.
     */
    private fun readExtensions(jsonReader: JsonReader): List<String?> {
        if (jsonReader.peek() != JsonReader.Token.BEGIN_OBJECT) {
            jsonReader.skipValue()
            return emptyList()
        }

        var oAuthStates: List<String?> = emptyList()

        jsonReader.beginObject()
        while (jsonReader.hasNext()) {
            when (jsonReader.nextName()) {
                KEY_OAUTH_STATES -> oAuthStates = readOAuthStates(jsonReader)
                else -> jsonReader.skipValue()
            }
        }
        jsonReader.endObject()

        return oAuthStates
    }

    /**
     * Reads the sign-in states of the OAuth accounts. The states are aligned by index with the accounts of the
     * payload, `null` for the accounts that don't have one. A value that isn't a list of states is ignored.
     */
    private fun readOAuthStates(jsonReader: JsonReader): List<String?> {
        if (jsonReader.peek() != JsonReader.Token.BEGIN_ARRAY) {
            jsonReader.skipValue()
            return emptyList()
        }

        val states = mutableListOf<String?>()
        var readable = true

        jsonReader.beginArray()
        while (jsonReader.hasNext()) {
            when (jsonReader.peek()) {
                JsonReader.Token.STRING -> states.add(jsonReader.nextString())

                JsonReader.Token.NULL -> {
                    jsonReader.skipValue()
                    states.add(null)
                }

                else -> {
                    jsonReader.skipValue()
                    readable = false
                }
            }
        }
        jsonReader.endArray()

        return if (readable) states else emptyList()
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
    val oAuthStates: List<String?> = emptyList(),
)
