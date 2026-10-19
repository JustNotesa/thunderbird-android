package app.k9mail.feature.migration.qrcode.transfer

import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import java.io.IOException
import net.thunderbird.legacy.logging.Log
import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

private const val PREFIX = "TBX1:"
private const val SEPARATOR = "."
private const val PADDING = '='
private const val TRANSFER_ID_LENGTH = 16
private const val KEY_LENGTH = 32
private const val TRANSFER_ID_TEXT_LENGTH = 22
private const val KEY_TEXT_LENGTH = 43
private const val MIN_PART_COUNT = 2
private const val PART_COUNT = 3
private const val KEY_TYPE = "t"
private const val KEY_HOST = "h"
private const val KEY_PORT = "p"
private const val KEY_SSID = "s"
private const val KEY_PASSPHRASE = "k"
private const val KEY_COUNT = "n"
private const val ROUTE_LAN = "lan"
private const val ROUTE_HOTSPOT = "hs"
private const val ROUTE_SEQUENCE = "seq"
private const val IPV4_OCTET_COUNT = 4
private const val OCTET_MIN = 0
private const val OCTET_MAX = 255
private const val PORT_MIN = 1
private const val PORT_MAX = 65535
private const val SSID_MIN_LENGTH = 1
private const val SSID_MAX_LENGTH = 32
private const val PASSPHRASE_MIN_LENGTH = 8
private const val PASSPHRASE_MAX_LENGTH = 63
private const val COUNT_MIN = 1
private const val COUNT_MAX = 200

/**
 * QR code content of an account transfer: the transfer ID, the AES key and, optionally, the route the encrypted
 * container is transported over.
 *
 * The content is not a URL scheme on purpose. A camera app or Lens would otherwise open, display or store the key.
 * It is only read by Thunderbird's own scanner.
 */
internal data class TransferQrCode(
    val secrets: TransferSecrets,
    val route: TransferRoute?,
) {
    fun format(): String {
        val builder = StringBuilder(PREFIX)
        builder.append(encode(secrets.transferId))
        builder.append(SEPARATOR).append(encode(secrets.key))

        if (route != null) {
            builder.append(SEPARATOR).append(encode(writeRoute(route).encodeToByteArray()))
        }

        return builder.toString()
    }

    companion object {
        /**
         * Parses the QR code content.
         *
         * @return [TransferQrCode] if the content is valid, `null` otherwise.
         */
        fun parse(text: String): TransferQrCode? {
            val parts = text.split(SEPARATOR)

            return if (parts.size in MIN_PART_COUNT..PART_COUNT && parts[0].startsWith(PREFIX)) {
                parseParts(parts)
            } else {
                null
            }
        }

        private fun parseParts(parts: List<String>): TransferQrCode? {
            return try {
                val transferId = decode(parts[0].removePrefix(PREFIX), TRANSFER_ID_TEXT_LENGTH, TRANSFER_ID_LENGTH)
                val key = decode(parts[1], KEY_TEXT_LENGTH, KEY_LENGTH)
                val hasRoute = parts.size == PART_COUNT
                val route = if (hasRoute) readRoute(parts[2]) else null
                val isRouteValid = !hasRoute || route != null

                if (transferId != null && key != null && isRouteValid) {
                    TransferQrCode(TransferSecrets(transferId = transferId, key = key), route)
                } else {
                    null
                }
            } catch (e: IllegalArgumentException) {
                Log.d(e, "Transfer route failed validation")
                null
            } catch (e: IOException) {
                Log.d(e, "Failed to read transfer route")
                null
            }
        }
    }
}

internal sealed interface TransferRoute {
    data class Lan(val host: String, val port: Int) : TransferRoute

    data class Hotspot(
        val ssid: String,
        val passphrase: String,
        val host: String,
        val port: Int,
    ) : TransferRoute

    data class Sequence(val count: Int) : TransferRoute
}

private fun readRoute(routeText: String): TransferRoute? {
    val routeJson = routeText.decodeBase64()?.utf8() ?: return null

    return JsonReader.of(Buffer().writeUtf8(routeJson)).use { jsonReader ->
        var type: String? = null
        var host: String? = null
        var port: Int? = null
        var ssid: String? = null
        var passphrase: String? = null
        var count: Int? = null

        jsonReader.beginObject()
        while (jsonReader.hasNext()) {
            when (jsonReader.nextName()) {
                KEY_TYPE -> type = jsonReader.nextString()
                KEY_HOST -> host = jsonReader.nextString()
                KEY_PORT -> port = jsonReader.nextInt()
                KEY_SSID -> ssid = jsonReader.nextString()
                KEY_PASSPHRASE -> passphrase = jsonReader.nextString()
                KEY_COUNT -> count = jsonReader.nextInt()
                else -> jsonReader.skipValue()
            }
        }
        jsonReader.endObject()

        when (type) {
            ROUTE_LAN -> TransferRoute.Lan(host = checkHost(host), port = checkPort(port))

            ROUTE_HOTSPOT -> {
                val validSsid = requireNotNull(ssid) { "Transfer route is missing the SSID" }
                val validPassphrase = requireNotNull(passphrase) { "Transfer route is missing the passphrase" }
                require(validSsid.length in SSID_MIN_LENGTH..SSID_MAX_LENGTH)
                require(validPassphrase.length in PASSPHRASE_MIN_LENGTH..PASSPHRASE_MAX_LENGTH)
                TransferRoute.Hotspot(
                    ssid = validSsid,
                    passphrase = validPassphrase,
                    host = checkHost(host),
                    port = checkPort(port),
                )
            }

            ROUTE_SEQUENCE -> {
                val validCount = requireNotNull(count) { "Transfer route is missing the frame count" }
                require(validCount in COUNT_MIN..COUNT_MAX)
                TransferRoute.Sequence(validCount)
            }

            else -> {
                Log.d("Unsupported transfer route type")
                null
            }
        }
    }
}

private fun writeRoute(route: TransferRoute): String {
    val buffer = Buffer()
    JsonWriter.of(buffer).use { jsonWriter ->
        jsonWriter.beginObject()
        when (route) {
            is TransferRoute.Lan -> {
                jsonWriter.name(KEY_TYPE).value(ROUTE_LAN)
                jsonWriter.name(KEY_HOST).value(route.host)
                jsonWriter.name(KEY_PORT).value(route.port.toLong())
            }

            is TransferRoute.Hotspot -> {
                jsonWriter.name(KEY_TYPE).value(ROUTE_HOTSPOT)
                jsonWriter.name(KEY_SSID).value(route.ssid)
                jsonWriter.name(KEY_PASSPHRASE).value(route.passphrase)
                jsonWriter.name(KEY_HOST).value(route.host)
                jsonWriter.name(KEY_PORT).value(route.port.toLong())
            }

            is TransferRoute.Sequence -> {
                jsonWriter.name(KEY_TYPE).value(ROUTE_SEQUENCE)
                jsonWriter.name(KEY_COUNT).value(route.count.toLong())
            }
        }
        jsonWriter.endObject()
    }

    return buffer.readUtf8()
}

private fun encode(bytes: ByteArray): String {
    // okio pads the Base64 URL encoding, the QR code format doesn't use padding.
    return bytes.toByteString().base64Url().trimEnd(PADDING)
}

private fun decode(value: String, textLength: Int, byteLength: Int): ByteArray? {
    if (value.length != textLength) {
        return null
    }

    val decoded = value.decodeBase64()

    return decoded?.takeIf { it.size == byteLength }?.toByteArray()
}

private fun checkHost(host: String?): String {
    val value = requireNotNull(host) { "Transfer route is missing the host" }
    require(isIpv4Address(value)) { "Transfer route host has to be an IPv4 address" }

    return value
}

private fun checkPort(port: Int?): Int {
    val value = requireNotNull(port) { "Transfer route is missing the port" }
    require(value in PORT_MIN..PORT_MAX) { "Transfer route port out of range" }

    return value
}

private fun isIpv4Address(host: String): Boolean {
    val octets = host.split('.')
    if (octets.size != IPV4_OCTET_COUNT) {
        return false
    }

    return octets.all { octet ->
        val value = octet.toIntOrNull()
        value != null && value in OCTET_MIN..OCTET_MAX && octet.all(Char::isDigit)
    }
}
