package com.fsck.k9.preferences

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test

class AccountColorParserTest {
    @Test
    fun `signed integer should be parsed`() {
        val result = parseAccountColor("-16776961")

        assertThat(result).isEqualTo(0xFF0000FF.toInt())
    }

    @Test
    fun `pretty hex string should be parsed as opaque color`() {
        val result = parseAccountColor("#ff8800")

        assertThat(result).isEqualTo(0xFFFF8800.toInt())
    }

    @Test
    fun `null value should return null`() {
        val result = parseAccountColor(null)

        assertThat(result).isNull()
    }

    @Test
    fun `blank value should return null`() {
        val result = parseAccountColor("  ")

        assertThat(result).isNull()
    }

    @Test
    fun `invalid value should return null`() {
        val result = parseAccountColor("red")

        assertThat(result).isNull()
    }

    @Test
    fun `invalid hex string should return null`() {
        val result = parseAccountColor("#zzzzzz")

        assertThat(result).isNull()
    }
}
