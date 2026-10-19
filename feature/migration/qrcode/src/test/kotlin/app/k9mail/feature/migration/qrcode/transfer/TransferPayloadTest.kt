package app.k9mail.feature.migration.qrcode.transfer

import app.k9mail.feature.migration.qrcode.payload.QrCodeData
import app.k9mail.feature.migration.qrcode.payload.QrCodePayloadAdapter
import app.k9mail.feature.migration.qrcode.payload.QrCodePayloadParser
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlin.test.Test
import net.thunderbird.core.logging.testing.TestLogger
import net.thunderbird.legacy.logging.Log
import org.junit.Before

class TransferPayloadTest {
    private val testSubject = TransferPayload
    private val payloadParser = QrCodePayloadParser(QrCodePayloadAdapter())

    @Before
    fun setUp() {
        Log.logger = TestLogger()
    }

    @Test
    fun `written payload can be read again`() {
        val written = testSubject.write(accountsJson = ACCOUNTS_PAYLOAD, createdAt = CREATED_AT)

        val content = testSubject.read(written)

        assertThat(content).isNotNull()
        assertThat(checkNotNull(content).createdAt).isEqualTo(CREATED_AT)
    }

    @Test
    fun `accounts of a written payload are read unchanged`() {
        val written = testSubject.write(accountsJson = ACCOUNTS_PAYLOAD, createdAt = CREATED_AT)

        val content = testSubject.read(written)

        assertThat(checkNotNull(content).accountsJson).isEqualTo(ACCOUNTS_PAYLOAD)
    }

    @Test
    fun `accounts of a written payload can be parsed by the QR code payload parser`() {
        val written = testSubject.write(accountsJson = ACCOUNTS_PAYLOAD, createdAt = CREATED_AT)

        val content = testSubject.read(written)

        assertThat(payloadParser.parse(checkNotNull(content).accountsJson)).isEqualTo(EXPECTED_DATA)
    }

    @Test
    fun `payload with unknown fields and extensions is read`() {
        val json = payloadJson(
            extraFields = """"future":"value","extensions":{"android:signature":"Hello"},""",
        )

        val content = testSubject.read(json)

        assertThat(content).isNotNull()
        assertThat(checkNotNull(content).createdAt).isEqualTo(CREATED_AT)
    }

    @Test
    fun `payload with unsupported type is not read`() {
        val json = payloadJson(type = "thunderbird-account-transfer-v2")

        val result = testSubject.read(json)

        assertThat(result).isNull()
    }

    @Test
    fun `payload with unsupported version is not read`() {
        val json = payloadJson(version = 2)

        val result = testSubject.read(json)

        assertThat(result).isNull()
    }

    @Test
    fun `payload without accounts is not read`() {
        val json = """{"type":"thunderbird-account-transfer","version":1,"createdAt":$CREATED_AT}"""

        val result = testSubject.read(json)

        assertThat(result).isNull()
    }

    @Test
    fun `payload that is not valid JSON is not read`() {
        val result = testSubject.read("https://domain.example/path")

        assertThat(result).isNull()
    }

    @Test
    fun `payload with accounts that aren't a JSON array is not written`() {
        val accounts = """{"username":"user@domain.example"}"""

        val failure = assertFailure {
            testSubject.write(accountsJson = accounts, createdAt = CREATED_AT)
        }

        failure.isInstanceOf(IllegalArgumentException::class)
    }

    private fun payloadJson(
        type: String = "thunderbird-account-transfer",
        version: Int = 1,
        extraFields: String = """"extensions":{},""",
    ): String {
        return """{"type":"$type","version":$version,"createdAt":$CREATED_AT,$extraFields""" +
            """"accounts":$ACCOUNTS_PAYLOAD}"""
    }

    private companion object {
        private const val CREATED_AT = 1792300000L
        private const val ACCOUNTS_PAYLOAD = """[1,[1,1],""" +
            """[0,"imap.domain.example",993,3,2,"username","","imap-password"],""" +
            """[[[0,"smtp.domain.example",587,2,1,"username","smtp-password"],""" +
            """["user@domain.example","Firstname Lastname"]]]]"""

        private val EXPECTED_DATA = QrCodeData(
            version = 1,
            misc = QrCodeData.Misc(
                sequenceNumber = 1,
                sequenceEnd = 1,
            ),
            accounts = listOf(
                QrCodeData.Account(
                    incomingServer = QrCodeData.IncomingServer(
                        protocol = 0,
                        hostname = "imap.domain.example",
                        port = 993,
                        connectionSecurity = 3,
                        authenticationType = 2,
                        username = "username",
                        accountName = "",
                        password = "imap-password",
                    ),
                    outgoingServers = listOf(
                        QrCodeData.OutgoingServer(
                            protocol = 0,
                            hostname = "smtp.domain.example",
                            port = 587,
                            connectionSecurity = 2,
                            authenticationType = 1,
                            username = "username",
                            password = "smtp-password",
                            identities = listOf(
                                QrCodeData.Identity(
                                    emailAddress = "user@domain.example",
                                    displayName = "Firstname Lastname",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }
}
