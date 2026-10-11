package net.thunderbird.feature.account.storage.legacy

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test
import net.thunderbird.account.fake.FakeAccountData
import net.thunderbird.core.android.account.AccountSignature
import net.thunderbird.core.android.account.LegacyAccountDto
import net.thunderbird.feature.account.storage.legacy.fake.FakeStorage
import net.thunderbird.feature.account.storage.legacy.fake.FakeStorageEditor

class LegacySignatureDtoStorageHandlerTest {
    private val testSubject = LegacySignatureDtoStorageHandler()

    @Test
    fun `load should populate signatures in stored order`() {
        // Arrange
        val account = LegacyAccountDto(accountId)
        val storage = createStorage()

        // Act
        testSubject.load(account, storage)

        // Assert
        assertThat(account.signatures).containsExactly(WORK_SIGNATURE, PRIVATE_SIGNATURE)
    }

    @Test
    fun `load should leave signatures empty when none are stored`() {
        // Arrange
        val account = LegacyAccountDto(accountId)

        // Act
        testSubject.load(account, FakeStorage())

        // Assert
        assertThat(account.signatures).isEmpty()
    }

    @Test
    fun `save should store signatures as indexed list`() {
        // Arrange
        val account = LegacyAccountDto(accountId).apply {
            signatures = listOf(WORK_SIGNATURE, PRIVATE_SIGNATURE)
        }
        val editor = FakeStorageEditor()

        // Act
        testSubject.save(account, FakeStorage(), editor)

        // Assert
        assertThat(editor.values["$accountId.savedSignatureId.0"]).isEqualTo(WORK_SIGNATURE.id)
        assertThat(editor.values["$accountId.savedSignatureName.0"]).isEqualTo(WORK_SIGNATURE.name)
        assertThat(editor.values["$accountId.savedSignatureText.0"]).isEqualTo(WORK_SIGNATURE.text)
        assertThat(editor.values["$accountId.savedSignatureIsHtml.0"]).isEqualTo("true")
        assertThat(editor.values["$accountId.savedSignatureId.1"]).isEqualTo(PRIVATE_SIGNATURE.id)
        assertThat(editor.values["$accountId.savedSignatureIsHtml.1"]).isEqualTo("false")
    }

    @Test
    fun `save should remove stored signatures that are no longer part of the account`() {
        // Arrange
        val account = LegacyAccountDto(accountId).apply {
            signatures = listOf(PRIVATE_SIGNATURE)
        }
        val editor = FakeStorageEditor()

        // Act
        testSubject.save(account, createStorage(), editor)

        // Assert
        assertThat(editor.values["$accountId.savedSignatureId.0"]).isEqualTo(PRIVATE_SIGNATURE.id)
        assertThat(editor.values["$accountId.savedSignatureId.1"]).isNull()
        assertThat(editor.values["$accountId.savedSignatureText.1"]).isNull()
    }

    @Test
    fun `delete should remove all stored signatures`() {
        // Arrange
        val account = LegacyAccountDto(accountId)
        val editor = FakeStorageEditor()

        // Act
        testSubject.delete(account, createStorage(), editor)

        // Assert
        assertThat(editor.removedKeys).containsExactly(
            "$accountId.savedSignatureId.0",
            "$accountId.savedSignatureName.0",
            "$accountId.savedSignatureText.0",
            "$accountId.savedSignatureIsHtml.0",
            "$accountId.savedSignatureId.1",
            "$accountId.savedSignatureName.1",
            "$accountId.savedSignatureText.1",
            "$accountId.savedSignatureIsHtml.1",
        )
    }

    private fun createStorage(): FakeStorage {
        return FakeStorage(
            mapOf(
                "$accountId.savedSignatureId.0" to WORK_SIGNATURE.id,
                "$accountId.savedSignatureName.0" to WORK_SIGNATURE.name,
                "$accountId.savedSignatureText.0" to WORK_SIGNATURE.text,
                "$accountId.savedSignatureIsHtml.0" to "true",
                "$accountId.savedSignatureId.1" to PRIVATE_SIGNATURE.id,
                "$accountId.savedSignatureName.1" to PRIVATE_SIGNATURE.name,
                "$accountId.savedSignatureText.1" to PRIVATE_SIGNATURE.text,
                "$accountId.savedSignatureIsHtml.1" to "false",
            ),
        )
    }

    private companion object {
        val accountId = FakeAccountData.ACCOUNT_ID

        val WORK_SIGNATURE = AccountSignature(
            id = "work",
            name = "Work",
            text = "<b>Jane Doe</b><br>Example Ltd.",
            isHtml = true,
        )
        val PRIVATE_SIGNATURE = AccountSignature(
            id = "private",
            name = "Private",
            text = "Cheers\nJane",
            isHtml = false,
        )
    }
}
