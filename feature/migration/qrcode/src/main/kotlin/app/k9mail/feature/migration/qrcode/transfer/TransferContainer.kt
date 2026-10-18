package app.k9mail.feature.migration.qrcode.transfer

import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import net.thunderbird.legacy.logging.Log

/**
 * Encrypted container of an account transfer.
 *
 * Layout: 4 bytes magic "TBX1", 1 byte format version, 1 byte flags, 16 bytes transfer ID, 12 bytes nonce followed by
 * the ciphertext including the 16 byte GCM tag. The header is passed to the cipher as additional authenticated data,
 * so modifying the header breaks decryption.
 */
internal object TransferContainer {
    private const val VERSION = 1
    private const val FLAGS = 0
    private const val KEY_ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_LENGTH = 128
    private const val MAGIC_LENGTH = 4
    private const val VERSION_OFFSET = 4
    private const val FLAGS_OFFSET = 5
    private const val TRANSFER_ID_OFFSET = 6
    private const val NONCE_OFFSET = 22
    private const val NONCE_LENGTH = 12
    private const val HEADER_LENGTH = 34
    private const val TAG_LENGTH_BYTES = 16
    private const val MINIMUM_LENGTH = HEADER_LENGTH + TAG_LENGTH_BYTES

    private val MAGIC = "TBX1".encodeToByteArray()
    private val random = SecureRandom()

    /**
     * Encrypts [plaintext] into a container that can only be read with [secrets].
     *
     * @param nonce Random nonce, only to be supplied for testing to get a deterministic result.
     */
    fun encrypt(plaintext: ByteArray, secrets: TransferSecrets, nonce: ByteArray = randomNonce()): ByteArray {
        require(nonce.size == NONCE_LENGTH) { "Nonce must be $NONCE_LENGTH bytes long" }

        val header = createHeader(secrets = secrets, nonce = nonce)
        val cipher = createCipher(mode = Cipher.ENCRYPT_MODE, secrets = secrets, nonce = nonce)
        cipher.updateAAD(header)

        return header + cipher.doFinal(plaintext)
    }

    fun decrypt(container: ByteArray, secrets: TransferSecrets): TransferContainerResult {
        val header = readHeader(container) ?: return TransferContainerResult.UnsupportedFormat

        return if (MessageDigest.isEqual(header.transferId, secrets.transferId)) {
            decryptContent(header = header, secrets = secrets)
        } else {
            TransferContainerResult.TransferIdMismatch
        }
    }

    private fun decryptContent(header: Header, secrets: TransferSecrets): TransferContainerResult {
        return try {
            val cipher = createCipher(mode = Cipher.DECRYPT_MODE, secrets = secrets, nonce = header.nonce)
            cipher.updateAAD(header.authenticatedData)
            TransferContainerResult.Success(cipher.doFinal(header.ciphertext))
        } catch (e: GeneralSecurityException) {
            Log.d(e, "Failed to decrypt transfer container")
            TransferContainerResult.Corrupted
        }
    }

    private fun createCipher(mode: Int, secrets: TransferSecrets, nonce: ByteArray): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(mode, SecretKeySpec(secrets.key, KEY_ALGORITHM), GCMParameterSpec(TAG_LENGTH, nonce))

        return cipher
    }

    private fun createHeader(secrets: TransferSecrets, nonce: ByteArray): ByteArray {
        val header = ByteArray(HEADER_LENGTH)
        MAGIC.copyInto(header, destinationOffset = 0)
        header[VERSION_OFFSET] = VERSION.toByte()
        header[FLAGS_OFFSET] = FLAGS.toByte()
        secrets.transferId.copyInto(header, destinationOffset = TRANSFER_ID_OFFSET)
        nonce.copyInto(header, destinationOffset = NONCE_OFFSET)

        return header
    }

    private fun readHeader(container: ByteArray): Header? {
        return if (isSupportedFormat(container)) {
            Header(
                authenticatedData = container.copyOfRange(0, HEADER_LENGTH),
                transferId = container.copyOfRange(TRANSFER_ID_OFFSET, NONCE_OFFSET),
                nonce = container.copyOfRange(NONCE_OFFSET, HEADER_LENGTH),
                ciphertext = container.copyOfRange(HEADER_LENGTH, container.size),
            )
        } else {
            null
        }
    }

    private fun isSupportedFormat(container: ByteArray): Boolean {
        val isSupported = when {
            container.size < MINIMUM_LENGTH -> false
            !MAGIC.contentEquals(container.copyOfRange(0, MAGIC_LENGTH)) -> false
            container[VERSION_OFFSET].toInt() != VERSION -> false
            else -> container[FLAGS_OFFSET].toInt() == FLAGS
        }

        if (!isSupported) {
            Log.d("Unsupported transfer container format")
        }

        return isSupported
    }

    private fun randomNonce(): ByteArray {
        val nonce = ByteArray(NONCE_LENGTH)
        random.nextBytes(nonce)

        return nonce
    }

    private data class Header(
        val authenticatedData: ByteArray,
        val transferId: ByteArray,
        val nonce: ByteArray,
        val ciphertext: ByteArray,
    )
}

internal sealed interface TransferContainerResult {
    data class Success(val plaintext: ByteArray) : TransferContainerResult

    data object UnsupportedFormat : TransferContainerResult

    data object TransferIdMismatch : TransferContainerResult

    data object Corrupted : TransferContainerResult
}
