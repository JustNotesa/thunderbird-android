package app.k9mail.feature.migration.qrcode.transfer

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import java.security.SecureRandom
import kotlin.test.Test
import net.thunderbird.core.logging.testing.TestLogger
import net.thunderbird.legacy.logging.Log
import okio.ByteString.Companion.toByteString
import org.junit.Before

class TransferContainerTest {
    private val testSubject = TransferContainer
    private val random = SecureRandom()
    private val secrets = TransferSecrets.generate(random)
    private val nonce = ByteArray(NONCE_LENGTH) { it.toByte() }
    private val plaintext = """{"type":"thunderbird-account-transfer"}""".encodeToByteArray()

    @Before
    fun setUp() {
        Log.logger = TestLogger()
    }

    @Test
    fun `decrypting an encrypted container returns the original plaintext`() {
        val container = testSubject.encrypt(plaintext, secrets)

        val result = testSubject.decrypt(container, secrets)

        assertThat(result).isInstanceOf<TransferContainerResult.Success>()
        assertThat((result as TransferContainerResult.Success).plaintext.toByteString())
            .isEqualTo(plaintext.toByteString())
    }

    @Test
    fun `encrypted container has the expected layout`() {
        val container = testSubject.encrypt(plaintext, secrets, nonce)

        assertThat(container.copyOfRange(0, MAGIC_LENGTH).decodeToString()).isEqualTo("TBX1")
        assertThat(container[4].toInt()).isEqualTo(1)
        assertThat(container[5].toInt()).isEqualTo(0)
        assertThat(container.copyOfRange(6, 22).toByteString()).isEqualTo(secrets.transferId.toByteString())
        assertThat(container.copyOfRange(22, 34).toByteString()).isEqualTo(nonce.toByteString())
        assertThat(container.size).isEqualTo(34 + plaintext.size + TAG_LENGTH)
    }

    @Test
    fun `encrypting the same plaintext with the same nonce gives the same container`() {
        val first = testSubject.encrypt(plaintext, secrets, nonce)
        val second = testSubject.encrypt(plaintext, secrets, nonce)

        assertThat(second.toByteString()).isEqualTo(first.toByteString())
    }

    @Test
    fun `encrypting with a random nonce gives a different container`() {
        val first = testSubject.encrypt(plaintext, secrets)
        val second = testSubject.encrypt(plaintext, secrets)

        assertThat(second.toByteString()).isNotEqualTo(first.toByteString())
    }

    @Test
    fun `decrypting a container with a modified ciphertext fails`() {
        val container = testSubject.encrypt(plaintext, secrets, nonce)
        container[container.size - 1] = (container.last() + 1).toByte()

        val result = testSubject.decrypt(container, secrets)

        assertThat(result).isEqualTo(TransferContainerResult.Corrupted)
    }

    @Test
    fun `decrypting a container with a modified header fails`() {
        val container = testSubject.encrypt(plaintext, secrets, nonce)
        container[22] = (container[22] + 1).toByte()

        val result = testSubject.decrypt(container, secrets)

        assertThat(result).isEqualTo(TransferContainerResult.Corrupted)
    }

    @Test
    fun `decrypting a container with a different key fails`() {
        val container = testSubject.encrypt(plaintext, secrets, nonce)
        val otherSecrets = TransferSecrets(
            transferId = secrets.transferId,
            key = ByteArray(KEY_LENGTH) { (it + 1).toByte() },
        )

        val result = testSubject.decrypt(container, otherSecrets)

        assertThat(result).isEqualTo(TransferContainerResult.Corrupted)
    }

    @Test
    fun `decrypting a container of another transfer fails`() {
        val container = testSubject.encrypt(plaintext, secrets, nonce)
        val otherSecrets = TransferSecrets(
            transferId = ByteArray(TRANSFER_ID_LENGTH) { (it + 1).toByte() },
            key = secrets.key,
        )

        val result = testSubject.decrypt(container, otherSecrets)

        assertThat(result).isEqualTo(TransferContainerResult.TransferIdMismatch)
    }

    @Test
    fun `decrypting a container that is too short fails`() {
        val container = testSubject.encrypt(plaintext, secrets, nonce)

        val result = testSubject.decrypt(container.copyOfRange(0, 40), secrets)

        assertThat(result).isEqualTo(TransferContainerResult.UnsupportedFormat)
    }

    @Test
    fun `decrypting a container with unknown magic fails`() {
        val container = testSubject.encrypt(plaintext, secrets, nonce)
        "XXXX".encodeToByteArray().copyInto(container, destinationOffset = 0)

        val result = testSubject.decrypt(container, secrets)

        assertThat(result).isEqualTo(TransferContainerResult.UnsupportedFormat)
    }

    @Test
    fun `decrypting a container with unsupported version fails`() {
        val container = testSubject.encrypt(plaintext, secrets, nonce)
        container[4] = 2

        val result = testSubject.decrypt(container, secrets)

        assertThat(result).isEqualTo(TransferContainerResult.UnsupportedFormat)
    }

    @Test
    fun `decrypting a container with unsupported flags fails`() {
        val container = testSubject.encrypt(plaintext, secrets, nonce)
        container[5] = 1

        val result = testSubject.decrypt(container, secrets)

        assertThat(result).isEqualTo(TransferContainerResult.UnsupportedFormat)
    }

    private companion object {
        private const val MAGIC_LENGTH = 4
        private const val NONCE_LENGTH = 12
        private const val TAG_LENGTH = 16
        private const val TRANSFER_ID_LENGTH = 16
        private const val KEY_LENGTH = 32
    }
}
