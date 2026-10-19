package app.k9mail.feature.migration.qrcode.transfer.hotspot

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import java.net.Inet4Address
import java.net.InetAddress
import kotlin.test.Test

class HotspotAddressTest {
    @Test
    fun `address that appeared while the hotspot started is found`() {
        val before = setOf(address("192.168.1.23"))
        val after = setOf(address("192.168.1.23"), address("192.168.49.1"))

        val result = findNewAddress(before = before, after = after)

        assertThat(result).isEqualTo(address("192.168.49.1"))
    }

    @Test
    fun `no address is found when none appeared`() {
        val addresses = setOf(address("192.168.1.23"), address("10.0.0.5"))

        val result = findNewAddress(before = addresses, after = addresses)

        assertThat(result).isNull()
    }

    @Test
    fun `loopback address that appeared is ignored`() {
        val before = setOf(address("192.168.1.23"))
        val after = setOf(address("192.168.1.23"), address("127.0.0.1"))

        val result = findNewAddress(before = before, after = after)

        assertThat(result).isNull()
    }

    @Test
    fun `public address that appeared is ignored`() {
        val before = setOf(address("10.0.0.5"))
        val after = setOf(address("10.0.0.5"), address("8.8.8.8"))

        val result = findNewAddress(before = before, after = after)

        assertThat(result).isNull()
    }

    @Test
    fun `first new site-local address is found`() {
        val before = setOf(address("10.0.0.5"))
        val after = setOf(
            address("10.0.0.5"),
            address("8.8.8.8"),
            address("172.20.0.1"),
            address("192.168.49.1"),
        )

        val result = findNewAddress(before = before, after = after)

        assertThat(result).isEqualTo(address("172.20.0.1"))
    }

    private fun address(literal: String): Inet4Address = InetAddress.getByName(literal) as Inet4Address
}
