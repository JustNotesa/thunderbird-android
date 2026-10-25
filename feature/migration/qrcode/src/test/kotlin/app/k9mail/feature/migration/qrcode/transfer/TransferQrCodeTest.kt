package app.k9mail.feature.migration.qrcode.transfer

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test
import net.thunderbird.components.core.logging.testing.TestLogger
import net.thunderbird.legacy.logging.Log
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import org.junit.Before

class TransferQrCodeTest {
    private val secrets = TransferSecrets(
        transferId = ByteArray(16) { it.toByte() },
        key = ByteArray(32) { (it * 2).toByte() },
    )
    private val transferIdText = secrets.transferId.toByteString().base64Url().trimEnd('=')
    private val keyText = secrets.key.toByteString().base64Url().trimEnd('=')

    @Before
    fun setUp() {
        Log.logger = TestLogger()
    }

    @Test
    fun `QR code without route has the expected length`() {
        val testSubject = TransferQrCode(secrets = secrets, route = null)

        val text = testSubject.format()

        assertThat(text.length).isEqualTo(5 + 22 + 1 + 43)
    }

    @Test
    fun `QR code without route can be parsed again`() {
        val testSubject = TransferQrCode(secrets = secrets, route = null)

        val result = TransferQrCode.parse(testSubject.format())

        assertThat(result).isEqualTo(testSubject)
    }

    @Test
    fun `QR code with LAN route can be parsed again`() {
        val testSubject = TransferQrCode(
            secrets = secrets,
            route = TransferRoute.Lan(host = "192.168.1.23", port = 48213),
        )

        val result = TransferQrCode.parse(testSubject.format())

        assertThat(result).isEqualTo(testSubject)
    }

    @Test
    fun `QR code with LAN route of several hosts can be parsed again`() {
        val testSubject = TransferQrCode(
            secrets = secrets,
            route = TransferRoute.Lan(hosts = listOf("192.168.178.20", "192.168.1.24"), port = 53111),
        )

        val result = TransferQrCode.parse(testSubject.format())

        assertThat(result).isEqualTo(testSubject)
    }

    @Test
    fun `QR code with hotspot route can be parsed again`() {
        val testSubject = TransferQrCode(
            secrets = secrets,
            route = TransferRoute.Hotspot(
                ssid = "Thunderbird",
                passphrase = "a-secure-passphrase",
                host = "192.168.49.1",
                port = 48213,
            ),
        )

        val result = TransferQrCode.parse(testSubject.format())

        assertThat(result).isEqualTo(testSubject)
    }

    @Test
    fun `QR code with sequence route can be parsed again`() {
        val testSubject = TransferQrCode(secrets = secrets, route = TransferRoute.Sequence(count = 7))

        val result = TransferQrCode.parse(testSubject.format())

        assertThat(result).isEqualTo(testSubject)
    }

    @Test
    fun `QR code with unknown prefix is not parsed`() {
        val result = TransferQrCode.parse("TBX2:$transferIdText.$keyText")

        assertThat(result).isNull()
    }

    @Test
    fun `QR code without key is not parsed`() {
        val result = TransferQrCode.parse("TBX1:$transferIdText")

        assertThat(result).isNull()
    }

    @Test
    fun `QR code with invalid transfer ID length is not parsed`() {
        val result = TransferQrCode.parse("TBX1:${transferIdText.dropLast(1)}.$keyText")

        assertThat(result).isNull()
    }

    @Test
    fun `QR code with invalid key length is not parsed`() {
        val result = TransferQrCode.parse("TBX1:$transferIdText.${keyText.dropLast(1)}")

        assertThat(result).isNull()
    }

    @Test
    fun `QR code with invalid transfer ID encoding is not parsed`() {
        val invalidId = "!".repeat(22)

        val result = TransferQrCode.parse("TBX1:$invalidId.$keyText")

        assertThat(result).isNull()
    }

    @Test
    fun `QR code with invalid key encoding is not parsed`() {
        val invalidKey = "!".repeat(43)

        val result = TransferQrCode.parse("TBX1:$transferIdText.$invalidKey")

        assertThat(result).isNull()
    }

    @Test
    fun `route with hostname instead of IP address is not parsed`() {
        val text = withRoute("""{"t":"lan","h":"desktop.local","p":48213}""")

        val result = TransferQrCode.parse(text)

        assertThat(result).isNull()
    }

    @Test
    fun `route with port below range is not parsed`() {
        val text = withRoute("""{"t":"lan","h":"192.168.1.23","p":0}""")

        val result = TransferQrCode.parse(text)

        assertThat(result).isNull()
    }

    @Test
    fun `route with port above range is not parsed`() {
        val text = withRoute("""{"t":"lan","h":"192.168.1.23","p":65536}""")

        val result = TransferQrCode.parse(text)

        assertThat(result).isNull()
    }

    @Test
    fun `route with SSID that is too long is not parsed`() {
        val text = withRoute("""{"t":"hs","s":"${"s".repeat(33)}","k":"passphrase","h":"192.168.1.23","p":48213}""")

        val result = TransferQrCode.parse(text)

        assertThat(result).isNull()
    }

    @Test
    fun `route with passphrase that is too short is not parsed`() {
        val text = withRoute("""{"t":"hs","s":"Thunderbird","k":"short","h":"192.168.1.23","p":48213}""")

        val result = TransferQrCode.parse(text)

        assertThat(result).isNull()
    }

    @Test
    fun `route with unknown type is not parsed`() {
        val text = withRoute("""{"t":"ftp","h":"192.168.1.23","p":48213}""")

        val result = TransferQrCode.parse(text)

        assertThat(result).isNull()
    }

    @Test
    fun `route with frame count below range is not parsed`() {
        val text = withRoute("""{"t":"seq","n":0}""")

        val result = TransferQrCode.parse(text)

        assertThat(result).isNull()
    }

    @Test
    fun `route with frame count above range is not parsed`() {
        val text = withRoute("""{"t":"seq","n":201}""")

        val result = TransferQrCode.parse(text)

        assertThat(result).isNull()
    }

    @Test
    fun `route that is not valid JSON is not parsed`() {
        val text = withRoute("""{"t":"lan"""")

        val result = TransferQrCode.parse(text)

        assertThat(result).isNull()
    }

    @Test
    fun `route with several hosts is parsed with all of them`() {
        val text = withRoute("""{"t":"lan","h":"192.168.178.20","hs":["192.168.178.20","192.168.1.24"],"p":53111}""")

        val result = TransferQrCode.parse(text)

        val route = checkNotNull(result?.route as? TransferRoute.Lan) { "The route is invalid" }
        assertThat(route.hosts).isEqualTo(listOf("192.168.178.20", "192.168.1.24"))
        assertThat(route.host).isEqualTo("192.168.178.20")
    }

    @Test
    fun `route without hosts is parsed with the primary host only`() {
        val text = withRoute("""{"t":"lan","h":"192.168.1.23","p":48213}""")

        val result = TransferQrCode.parse(text)

        val route = checkNotNull(result?.route as? TransferRoute.Lan) { "The route is invalid" }
        assertThat(route.hosts).isEqualTo(listOf("192.168.1.23"))
    }

    @Test
    fun `route with invalid hosts is parsed with the valid ones only`() {
        val text = withRoute("""{"t":"lan","h":"192.168.1.23","hs":["desktop.local","192.168.1.24",7],"p":48213}""")

        val result = TransferQrCode.parse(text)

        val route = checkNotNull(result?.route as? TransferRoute.Lan) { "The route is invalid" }
        assertThat(route.hosts).isEqualTo(listOf("192.168.1.23", "192.168.1.24"))
    }

    @Test
    fun `route with duplicate hosts is parsed with the first one only`() {
        val text = withRoute("""{"t":"lan","h":"192.168.1.23","hs":["192.168.1.24","192.168.1.23"],"p":48213}""")

        val result = TransferQrCode.parse(text)

        val route = checkNotNull(result?.route as? TransferRoute.Lan) { "The route is invalid" }
        assertThat(route.hosts).isEqualTo(listOf("192.168.1.23", "192.168.1.24"))
    }

    @Test
    fun `route with more than four hosts is parsed with four of them`() {
        val text = withRoute(
            """{"t":"lan","h":"10.0.0.1","hs":["10.0.0.1","10.0.0.2","10.0.0.3","10.0.0.4"],"p":48213}""",
        )

        val result = TransferQrCode.parse(text)

        val route = checkNotNull(result?.route as? TransferRoute.Lan) { "The route is invalid" }
        assertThat(route.hosts).isEqualTo(listOf("10.0.0.1", "10.0.0.2", "10.0.0.3", "10.0.0.4"))
    }

    @Test
    fun `route with several hosts is written with all of them`() {
        val testSubject = TransferQrCode(
            secrets = secrets,
            route = TransferRoute.Lan(hosts = listOf("192.168.178.20", "192.168.1.24"), port = 53111),
        )

        val routeJson = routeJsonOf(testSubject.format())

        assertThat(routeJson)
            .isEqualTo("""{"t":"lan","h":"192.168.178.20","hs":["192.168.178.20","192.168.1.24"],"p":53111}""")
    }

    @Test
    fun `route with one host is written without the hosts`() {
        val testSubject = TransferQrCode(
            secrets = secrets,
            route = TransferRoute.Lan(host = "192.168.1.23", port = 48213),
        )

        val routeJson = routeJsonOf(testSubject.format())

        assertThat(routeJson).isEqualTo("""{"t":"lan","h":"192.168.1.23","p":48213}""")
    }

    private fun withRoute(routeJson: String): String {
        val routeText = routeJson.encodeToByteArray().toByteString().base64Url().trimEnd('=')

        return "TBX1:$transferIdText.$keyText.$routeText"
    }

    private fun routeJsonOf(qrText: String): String {
        val routeText = qrText.split(".")[ROUTE_PART_INDEX]

        return checkNotNull(routeText.decodeBase64()) { "The route is invalid" }.utf8()
    }

    private companion object {
        private const val ROUTE_PART_INDEX = 2
    }
}
