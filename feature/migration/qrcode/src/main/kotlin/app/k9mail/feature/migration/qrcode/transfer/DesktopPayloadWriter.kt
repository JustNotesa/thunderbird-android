package app.k9mail.feature.migration.qrcode.transfer

import com.fsck.k9.mail.AuthType
import com.fsck.k9.mail.ConnectionSecurity
import com.squareup.moshi.JsonWriter
import okio.Buffer

private const val PAYLOAD_VERSION = 1
private const val SEQUENCE_NUMBER = 1
private const val SEQUENCE_END = 1
private const val TYPE_IMAP = "imap"
private const val TYPE_POP3 = "pop3"
private const val TYPE_SMTP = "smtp"
private const val PROTOCOL_IMAP = 0
private const val PROTOCOL_POP3 = 1
private const val PROTOCOL_SMTP = 0
private const val SECURITY_NONE = 0
private const val SECURITY_STARTTLS_REQUIRED = 2
private const val SECURITY_SSL_TLS_REQUIRED = 3
private const val AUTHENTICATION_NONE = 0
private const val AUTHENTICATION_PLAIN = 1
private const val AUTHENTICATION_CRAM_MD5 = 2
private const val AUTHENTICATION_EXTERNAL = 5
private const val AUTHENTICATION_XOAUTH2 = 6

/**
 * Writes accounts in the Thunderbird desktop export payload, version 1.
 *
 * This is the exact inverse of the desktop payload reader, so a payload written here is imported by Thunderbird for
 * Android and by the desktop. The format holds all accounts in one array and includes the passwords; the payload is
 * only ever written into an encrypted transfer container.
 */
internal object DesktopPayloadWriter {
    fun write(accounts: List<TransferAccount>): String {
        val buffer = Buffer()
        JsonWriter.of(buffer).use { jsonWriter ->
            jsonWriter.beginArray()
            jsonWriter.value(PAYLOAD_VERSION.toLong())
            writeMiscellaneous(jsonWriter)
            accounts.forEach { writeAccount(jsonWriter, it) }
            jsonWriter.endArray()
        }

        return buffer.readUtf8()
    }

    private fun writeMiscellaneous(jsonWriter: JsonWriter) {
        jsonWriter.beginArray()
        jsonWriter.value(SEQUENCE_NUMBER.toLong())
        jsonWriter.value(SEQUENCE_END.toLong())
        jsonWriter.endArray()
    }

    private fun writeAccount(jsonWriter: JsonWriter, account: TransferAccount) {
        writeIncomingServer(jsonWriter, account)
        jsonWriter.beginArray()
        writeOutgoingServer(jsonWriter, account)
        jsonWriter.endArray()
    }

    private fun writeIncomingServer(jsonWriter: JsonWriter, account: TransferAccount) {
        val server = account.incoming

        jsonWriter.beginArray()
        jsonWriter.value(incomingProtocol(server.type).toLong())
        jsonWriter.value(server.host)
        jsonWriter.value(server.port.toLong())
        jsonWriter.value(connectionSecurity(server.connectionSecurity).toLong())
        jsonWriter.value(authenticationType(server.authenticationType).toLong())
        jsonWriter.value(server.username)
        jsonWriter.value(account.accountName.orEmpty())
        jsonWriter.value(server.password.orEmpty())
        jsonWriter.endArray()
    }

    private fun writeOutgoingServer(jsonWriter: JsonWriter, account: TransferAccount) {
        val server = account.outgoing

        jsonWriter.beginArray()
        jsonWriter.beginArray()
        jsonWriter.value(outgoingProtocol(server.type).toLong())
        jsonWriter.value(server.host)
        jsonWriter.value(server.port.toLong())
        jsonWriter.value(connectionSecurity(server.connectionSecurity).toLong())
        jsonWriter.value(authenticationType(server.authenticationType).toLong())
        jsonWriter.value(server.username)
        jsonWriter.value(server.password.orEmpty())
        jsonWriter.endArray()
        account.identities.forEach { writeIdentity(jsonWriter, it) }
        jsonWriter.endArray()
    }

    private fun writeIdentity(jsonWriter: JsonWriter, identity: TransferIdentity) {
        jsonWriter.beginArray()
        jsonWriter.value(identity.emailAddress)
        jsonWriter.value(identity.displayName)
        jsonWriter.endArray()
    }

    private fun incomingProtocol(type: String): Int {
        return when (type) {
            TYPE_IMAP -> PROTOCOL_IMAP
            TYPE_POP3 -> PROTOCOL_POP3
            else -> throw IllegalArgumentException("Unsupported incoming server protocol")
        }
    }

    private fun outgoingProtocol(type: String): Int {
        return when (type) {
            TYPE_SMTP -> PROTOCOL_SMTP
            else -> throw IllegalArgumentException("Unsupported outgoing server protocol")
        }
    }

    private fun connectionSecurity(connectionSecurity: ConnectionSecurity): Int {
        return when (connectionSecurity) {
            ConnectionSecurity.NONE -> SECURITY_NONE
            ConnectionSecurity.STARTTLS_REQUIRED -> SECURITY_STARTTLS_REQUIRED
            ConnectionSecurity.SSL_TLS_REQUIRED -> SECURITY_SSL_TLS_REQUIRED
        }
    }

    private fun authenticationType(authenticationType: AuthType): Int {
        return when (authenticationType) {
            AuthType.NONE -> AUTHENTICATION_NONE
            AuthType.PLAIN -> AUTHENTICATION_PLAIN
            AuthType.CRAM_MD5 -> AUTHENTICATION_CRAM_MD5
            AuthType.EXTERNAL -> AUTHENTICATION_EXTERNAL
            AuthType.XOAUTH2 -> AUTHENTICATION_XOAUTH2
        }
    }
}
