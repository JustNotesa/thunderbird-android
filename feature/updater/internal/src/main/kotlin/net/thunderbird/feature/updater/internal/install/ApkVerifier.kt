package net.thunderbird.feature.updater.internal.install

import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Verifies the checksum and the size of a downloaded APK.
 */
internal class ApkVerifier {

    /**
     * Verifies [file] against the checksum [expectedSha256] and the size [expectedSizeBytes].
     *
     * The size isn't checked when [expectedSizeBytes] isn't greater than zero, because the size of the download
     * isn't known then.
     */
    fun verify(file: File, expectedSha256: String, expectedSizeBytes: Long): ApkVerification {
        return when {
            !file.isFile || !file.canRead() -> ApkVerification.Unreadable
            !hasExpectedSize(file, expectedSizeBytes) -> ApkVerification.SizeMismatch
            else -> verifyChecksum(file, expectedSha256)
        }
    }

    /**
     * Copies [source] into the private directory [targetDir] under the name [fileName].
     *
     * The installer verifies the private copy, so that no other app can replace the file between the verification
     * and the installation.
     *
     * @return the copied file, `null` when the file couldn't be copied.
     */
    fun copyToPrivateDir(source: File, targetDir: File, fileName: String): File? {
        return try {
            copyFile(source, targetDir, fileName)
        } catch (_: IOException) {
            // The file couldn't be copied, the caller has to report that to the user.
            null
        }
    }

    private fun copyFile(source: File, targetDir: File, fileName: String): File {
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        val target = File(targetDir, fileName)
        source.copyTo(target, overwrite = true)
        return target
    }

    private fun hasExpectedSize(file: File, expectedSizeBytes: Long): Boolean {
        return expectedSizeBytes <= 0L || file.length() == expectedSizeBytes
    }

    private fun verifyChecksum(file: File, expectedSha256: String): ApkVerification {
        val actualChecksum = checksum(file) ?: return ApkVerification.Unreadable
        return if (expectedSha256.equals(actualChecksum, ignoreCase = true)) {
            ApkVerification.Valid
        } else {
            ApkVerification.ChecksumMismatch
        }
    }

    private fun checksum(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance(SHA_256_ALGORITHM)
            file.inputStream().use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            hexDigest(digest.digest())
        } catch (_: Exception) {
            // The file can't be read, so its checksum can't be verified.
            null
        }
    }

    private fun hexDigest(digest: ByteArray): String {
        val builder = StringBuilder(digest.size * HEX_CHARACTERS_PER_BYTE)
        for (byte in digest) {
            val value = byte.toInt() and BYTE_MASK
            builder.append(HEX_DIGITS[value shr NIBBLE_SHIFT])
            builder.append(HEX_DIGITS[value and NIBBLE_MASK])
        }
        return builder.toString()
    }

    private companion object {
        const val SHA_256_ALGORITHM = "SHA-256"
        const val BUFFER_SIZE = 8 * 1024
        const val HEX_DIGITS = "0123456789abcdef"
        const val HEX_CHARACTERS_PER_BYTE = 2
        const val BYTE_MASK = 0xFF
        const val NIBBLE_MASK = 0x0F
        const val NIBBLE_SHIFT = 4
    }
}

/** The result of the verification of a downloaded APK. */
internal sealed interface ApkVerification {
    /** The APK matches the expected checksum and size. */
    data object Valid : ApkVerification

    /** The APK doesn't match the expected checksum. */
    data object ChecksumMismatch : ApkVerification

    /** The APK doesn't match the expected size. */
    data object SizeMismatch : ApkVerification

    /** The APK can't be read, so it can't be verified. */
    data object Unreadable : ApkVerification
}
