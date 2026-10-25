package net.thunderbird.feature.updater.internal.install

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.After
import org.junit.Before
import org.junit.Test

class ApkVerifierTest {
    private lateinit var tempDir: File
    private lateinit var testSubject: ApkVerifier

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("apk-verifier-test").toFile()
        testSubject = ApkVerifier()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `file with matching checksum and size is valid`() {
        // Arrange
        val file = createFile(CONTENT)

        // Act
        val result = testSubject.verify(file, sha256(CONTENT), CONTENT.length.toLong())

        // Assert
        assertThat(result).isEqualTo(ApkVerification.Valid)
    }

    @Test
    fun `file with mismatching checksum is not valid`() {
        // Arrange
        val file = createFile(CONTENT)

        // Act
        val result = testSubject.verify(file, sha256(OTHER_CONTENT), CONTENT.length.toLong())

        // Assert
        assertThat(result).isEqualTo(ApkVerification.ChecksumMismatch)
    }

    @Test
    fun `checksum in upper case is accepted`() {
        // Arrange
        val file = createFile(CONTENT)

        // Act
        val result = testSubject.verify(file, sha256(CONTENT).uppercase(), CONTENT.length.toLong())

        // Assert
        assertThat(result).isEqualTo(ApkVerification.Valid)
    }

    @Test
    fun `file with unexpected size is not valid`() {
        // Arrange
        val file = createFile(CONTENT)

        // Act
        val result = testSubject.verify(file, sha256(CONTENT), CONTENT.length.toLong() + 1L)

        // Assert
        assertThat(result).isEqualTo(ApkVerification.SizeMismatch)
    }

    @Test
    fun `size is not checked when the size is unknown`() {
        // Arrange
        val file = createFile(CONTENT)

        // Act
        val result = testSubject.verify(file, sha256(CONTENT), UNKNOWN_SIZE)

        // Assert
        assertThat(result).isEqualTo(ApkVerification.Valid)
    }

    @Test
    fun `missing file can't be verified`() {
        // Arrange
        val file = File(tempDir, "missing.apk")

        // Act
        val result = testSubject.verify(file, sha256(CONTENT), CONTENT.length.toLong())

        // Assert
        assertThat(result).isEqualTo(ApkVerification.Unreadable)
    }

    @Test
    fun `file is copied into the private directory`() {
        // Arrange
        val source = createFile(CONTENT)
        val targetDir = File(tempDir, "private")

        // Act
        val copied = testSubject.copyToPrivateDir(source, targetDir, FILE_NAME)

        // Assert
        assertThat(copied).isNotNull()
        assertThat(copied?.readText()).isEqualTo(CONTENT)
        assertThat(targetDir.isDirectory).isTrue()
    }

    @Test
    fun `copying overwrites a file with the same name`() {
        // Arrange
        val source = createFile(CONTENT)
        val targetDir = File(tempDir, "private")
        val existing = File(targetDir, FILE_NAME).apply {
            parentFile?.mkdirs()
            writeText(OTHER_CONTENT)
        }

        // Act
        val copied = testSubject.copyToPrivateDir(source, targetDir, FILE_NAME)

        // Assert
        assertThat(copied?.readText()).isEqualTo(CONTENT)
        assertThat(existing.readText()).isEqualTo(CONTENT)
    }

    @Test
    fun `copying a missing file fails`() {
        // Arrange
        val source = File(tempDir, "missing.apk")
        val targetDir = File(tempDir, "private")

        // Act
        val copied = testSubject.copyToPrivateDir(source, targetDir, FILE_NAME)

        // Assert
        assertThat(copied).isNull()
    }

    private fun createFile(content: String): File {
        return File(tempDir, FILE_NAME).apply { writeText(content) }
    }

    private fun sha256(content: String): String {
        val digest = MessageDigest.getInstance(SHA_256_ALGORITHM).digest(content.toByteArray())
        return digest.joinToString(separator = "") { byte ->
            val value = byte.toInt() and BYTE_MASK
            HEX_DIGITS[value shr NIBBLE_SHIFT].toString() + HEX_DIGITS[value and NIBBLE_MASK]
        }
    }

    private companion object {
        const val CONTENT = "apk content"
        const val OTHER_CONTENT = "other content"
        const val FILE_NAME = "app.apk"
        const val SHA_256_ALGORITHM = "SHA-256"
        const val UNKNOWN_SIZE = 0L
        const val HEX_DIGITS = "0123456789abcdef"
        const val BYTE_MASK = 0xFF
        const val NIBBLE_MASK = 0x0F
        const val NIBBLE_SHIFT = 4
    }
}
