package app.k9mail.feature.migration.qrcode.transfer

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import assertk.assertions.isTrue
import java.security.SecureRandom
import kotlin.test.Test

class TransferSecretsTest {
    private val random = SecureRandom()

    @Test
    fun `generate creates transfer ID and key of the expected length`() {
        val testSubject = TransferSecrets.generate(random)

        assertThat(testSubject.transferId.size).isEqualTo(16)
        assertThat(testSubject.key.size).isEqualTo(32)
    }

    @Test
    fun `generate creates different secrets on every call`() {
        val first = TransferSecrets.generate(random)
        val second = TransferSecrets.generate(random)

        assertThat(second).isNotEqualTo(first)
    }

    @Test
    fun `secrets with the same content are equal`() {
        val testSubject = TransferSecrets(transferId = ByteArray(16) { 1 }, key = ByteArray(32) { 2 })
        val sameContent = TransferSecrets(transferId = ByteArray(16) { 1 }, key = ByteArray(32) { 2 })

        assertThat(sameContent).isEqualTo(testSubject)
        assertThat(sameContent.hashCode()).isEqualTo(testSubject.hashCode())
    }

    @Test
    fun `secrets with different content are not equal`() {
        val testSubject = TransferSecrets(transferId = ByteArray(16) { 1 }, key = ByteArray(32) { 2 })
        val differentKey = TransferSecrets(transferId = ByteArray(16) { 1 }, key = ByteArray(32) { 3 })

        assertThat(differentKey).isNotEqualTo(testSubject)
    }

    @Test
    fun `clear zeroes transfer ID and key`() {
        val testSubject = TransferSecrets.generate(random)

        testSubject.clear()

        assertThat(testSubject.transferId.all { it == ZERO_BYTE }).isTrue()
        assertThat(testSubject.key.all { it == ZERO_BYTE }).isTrue()
    }

    @Test
    fun `clear does not affect secrets created before`() {
        val first = TransferSecrets.generate(random)
        val second = TransferSecrets.generate(random)

        second.clear()

        assertThat(second).isNotEqualTo(first)
        assertThat(first.key.all { it == ZERO_BYTE }).isFalse()
    }

    private companion object {
        private val ZERO_BYTE = 0.toByte()
    }
}
