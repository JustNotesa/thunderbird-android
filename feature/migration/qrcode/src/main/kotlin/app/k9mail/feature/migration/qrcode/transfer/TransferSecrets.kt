package app.k9mail.feature.migration.qrcode.transfer

import java.security.SecureRandom

/**
 * Key material of a single account transfer.
 *
 * @param transferId Random ID that links the QR code to the container. Not a secret.
 * @param key Random AES-256-GCM key. Secret.
 */
internal data class TransferSecrets(
    val transferId: ByteArray,
    val key: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true

        if (other !is TransferSecrets) return false

        return transferId.contentEquals(other.transferId) && key.contentEquals(other.key)
    }

    override fun hashCode(): Int {
        return 31 * transferId.contentHashCode() + key.contentHashCode()
    }

    /**
     * Overwrites the key material with zeros. To be called when the transfer has finished.
     */
    fun clear() {
        transferId.fill(0)
        key.fill(0)
    }

    companion object {
        private const val TRANSFER_ID_LENGTH = 16
        private const val KEY_LENGTH = 32

        fun generate(random: SecureRandom): TransferSecrets {
            val transferId = ByteArray(TRANSFER_ID_LENGTH)
            val key = ByteArray(KEY_LENGTH)
            random.nextBytes(transferId)
            random.nextBytes(key)

            return TransferSecrets(transferId = transferId, key = key)
        }
    }
}
