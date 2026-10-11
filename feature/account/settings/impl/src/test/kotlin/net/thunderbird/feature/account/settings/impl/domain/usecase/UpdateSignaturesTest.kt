package net.thunderbird.feature.account.settings.impl.domain.usecase

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.fsck.k9.mail.AuthType
import com.fsck.k9.mail.ConnectionSecurity
import com.fsck.k9.mail.ServerSettings
import kotlin.test.Test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import net.thunderbird.components.core.outcome.Outcome
import net.thunderbird.core.android.account.AccountSignature
import net.thunderbird.core.android.account.Identity
import net.thunderbird.core.android.account.LegacyAccount
import net.thunderbird.core.android.account.LegacyAccountRepository
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.AccountIdFactory
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract.UpdateSignaturesCommand
import net.thunderbird.feature.account.settings.impl.domain.AccountSignatures
import net.thunderbird.feature.account.storage.profile.AvatarDto
import net.thunderbird.feature.account.storage.profile.AvatarTypeDto
import net.thunderbird.feature.account.storage.profile.ProfileDto

internal class UpdateSignaturesTest {

    @Test
    fun `should return NotFound when account does not exist`() = runTest {
        // Arrange
        val repository = FakeLegacyAccountRepository(account = null)
        val testSubject = UpdateSignatures(repository)

        // Act
        val result = testSubject(AccountIdFactory.create(), UpdateSignaturesCommand.Save(WORK_SIGNATURE))

        // Assert
        assertThat(result).isInstanceOf(Outcome.Failure::class)
        assertThat(repository.updatedAccount).isNull()
    }

    @Test
    fun `save should append a signature with a new id`() = runTest {
        // Arrange
        val repository = FakeLegacyAccountRepository(createLegacyAccount(signatures = listOf(WORK_SIGNATURE)))
        val testSubject = UpdateSignatures(repository)

        // Act
        val result = testSubject(repository.accountId, UpdateSignaturesCommand.Save(PRIVATE_SIGNATURE))

        // Assert
        assertThat(result).isEqualTo(
            Outcome.success(AccountSignatures(listOf(WORK_SIGNATURE, PRIVATE_SIGNATURE), defaultSignatureId = null)),
        )
        assertThat(repository.updatedSignatures).containsExactly(WORK_SIGNATURE, PRIVATE_SIGNATURE)
    }

    @Test
    fun `save should replace the signature with the same id and keep its position`() = runTest {
        // Arrange
        val repository = FakeLegacyAccountRepository(
            createLegacyAccount(signatures = listOf(WORK_SIGNATURE, PRIVATE_SIGNATURE)),
        )
        val testSubject = UpdateSignatures(repository)
        val changedSignature = WORK_SIGNATURE.copy(name = "Office", text = "Jane Doe\nExample Ltd.", isHtml = false)

        // Act
        testSubject(repository.accountId, UpdateSignaturesCommand.Save(changedSignature))

        // Assert
        assertThat(repository.updatedSignatures).containsExactly(changedSignature, PRIVATE_SIGNATURE)
    }

    @Test
    fun `delete should remove only the signature with the given id`() = runTest {
        // Arrange
        val repository = FakeLegacyAccountRepository(
            createLegacyAccount(signatures = listOf(WORK_SIGNATURE, PRIVATE_SIGNATURE)),
        )
        val testSubject = UpdateSignatures(repository)

        // Act
        val result = testSubject(repository.accountId, UpdateSignaturesCommand.Delete(WORK_SIGNATURE.id))

        // Assert
        assertThat(result).isEqualTo(
            Outcome.success(AccountSignatures(listOf(PRIVATE_SIGNATURE), defaultSignatureId = null)),
        )
        assertThat(repository.updatedSignatures).containsExactly(PRIVATE_SIGNATURE)
    }

    @Test
    fun `set default should make the signature the one of the account`() = runTest {
        // Arrange
        val repository = FakeLegacyAccountRepository(
            createLegacyAccount(signatures = listOf(WORK_SIGNATURE, PRIVATE_SIGNATURE)),
        )
        val testSubject = UpdateSignatures(repository)

        // Act
        val result = testSubject(repository.accountId, UpdateSignaturesCommand.SetDefault(WORK_SIGNATURE.id))

        // Assert
        assertThat(result).isEqualTo(
            Outcome.success(AccountSignatures(listOf(WORK_SIGNATURE, PRIVATE_SIGNATURE), WORK_SIGNATURE.id)),
        )
        val updated = repository.updatedAccount ?: error("Account was not updated")
        assertThat(updated.signatureUse).isTrue()
        assertThat(updated.signature).isEqualTo(WORK_SIGNATURE.text)
        assertThat(updated.signatureIsHtml).isTrue()
    }

    @Test
    fun `set default should fail for an unknown signature`() = runTest {
        // Arrange
        val repository = FakeLegacyAccountRepository(createLegacyAccount(signatures = listOf(WORK_SIGNATURE)))
        val testSubject = UpdateSignatures(repository)

        // Act
        val result = testSubject(repository.accountId, UpdateSignaturesCommand.SetDefault("unknown"))

        // Assert
        assertThat(result).isInstanceOf(Outcome.Failure::class)
        assertThat(repository.updatedAccount).isNull()
    }

    @Test
    fun `set default to none should switch the account signature off`() = runTest {
        // Arrange
        val repository = FakeLegacyAccountRepository(
            createLegacyAccount(signatures = listOf(WORK_SIGNATURE), defaultSignature = WORK_SIGNATURE),
        )
        val testSubject = UpdateSignatures(repository)

        // Act
        val result = testSubject(repository.accountId, UpdateSignaturesCommand.SetDefault(null))

        // Assert
        assertThat(result).isEqualTo(Outcome.success(AccountSignatures(listOf(WORK_SIGNATURE), null)))
        assertThat(repository.updatedAccount?.signatureUse).isEqualTo(false)
    }

    @Test
    fun `save should carry changes of the default signature over to the account`() = runTest {
        // Arrange
        val repository = FakeLegacyAccountRepository(
            createLegacyAccount(signatures = listOf(WORK_SIGNATURE), defaultSignature = WORK_SIGNATURE),
        )
        val testSubject = UpdateSignatures(repository)
        val changedSignature = WORK_SIGNATURE.copy(text = "Jane Doe", isHtml = false)

        // Act
        val result = testSubject(repository.accountId, UpdateSignaturesCommand.Save(changedSignature))

        // Assert
        assertThat(result).isEqualTo(Outcome.success(AccountSignatures(listOf(changedSignature), WORK_SIGNATURE.id)))
        val updated = repository.updatedAccount ?: error("Account was not updated")
        assertThat(updated.signature).isEqualTo("Jane Doe")
        assertThat(updated.signatureIsHtml).isEqualTo(false)
    }

    @Test
    fun `save should leave the account signature alone when another signature is the default`() = runTest {
        // Arrange
        val repository = FakeLegacyAccountRepository(
            createLegacyAccount(
                signatures = listOf(WORK_SIGNATURE, PRIVATE_SIGNATURE),
                defaultSignature = WORK_SIGNATURE,
            ),
        )
        val testSubject = UpdateSignatures(repository)

        // Act
        testSubject(repository.accountId, UpdateSignaturesCommand.Save(PRIVATE_SIGNATURE.copy(text = "Bye")))

        // Assert
        assertThat(repository.updatedAccount?.signature).isEqualTo(WORK_SIGNATURE.text)
    }

    @Test
    fun `delete should switch the account signature off when the default signature is deleted`() = runTest {
        // Arrange
        val repository = FakeLegacyAccountRepository(
            createLegacyAccount(
                signatures = listOf(WORK_SIGNATURE, PRIVATE_SIGNATURE),
                defaultSignature = WORK_SIGNATURE,
            ),
        )
        val testSubject = UpdateSignatures(repository)

        // Act
        val result = testSubject(repository.accountId, UpdateSignaturesCommand.Delete(WORK_SIGNATURE.id))

        // Assert
        assertThat(result).isEqualTo(Outcome.success(AccountSignatures(listOf(PRIVATE_SIGNATURE), null)))
        assertThat(repository.updatedAccount?.signatureUse).isEqualTo(false)
    }

    @Test
    fun `delete should keep the account signature when another signature is deleted`() = runTest {
        // Arrange
        val repository = FakeLegacyAccountRepository(
            createLegacyAccount(
                signatures = listOf(WORK_SIGNATURE, PRIVATE_SIGNATURE),
                defaultSignature = WORK_SIGNATURE,
            ),
        )
        val testSubject = UpdateSignatures(repository)

        // Act
        val result = testSubject(repository.accountId, UpdateSignaturesCommand.Delete(PRIVATE_SIGNATURE.id))

        // Assert
        assertThat(result).isEqualTo(Outcome.success(AccountSignatures(listOf(WORK_SIGNATURE), WORK_SIGNATURE.id)))
        assertThat(repository.updatedAccount?.signatureUse).isEqualTo(true)
    }

    private class FakeLegacyAccountRepository(
        private val account: LegacyAccount?,
    ) : LegacyAccountRepository {
        var updatedAccount: LegacyAccount? = null
            private set

        val accountId: AccountId
            get() = account?.id ?: error("No account")

        val updatedSignatures: List<AccountSignature>
            get() = updatedAccount?.signatures ?: error("Account was not updated")

        override fun observeById(accountId: AccountId): Flow<LegacyAccount?> = flowOf(account)

        override suspend fun update(account: LegacyAccount) {
            updatedAccount = account
        }
    }

    private companion object {
        val WORK_SIGNATURE = AccountSignature(id = "work", name = "Work", text = "<b>Jane Doe</b>", isHtml = true)
        val PRIVATE_SIGNATURE = AccountSignature(id = "private", name = "Private", text = "Cheers\nJane")

        fun createLegacyAccount(
            signatures: List<AccountSignature>,
            defaultSignature: AccountSignature? = null,
            id: AccountId = AccountIdFactory.create(),
        ) = LegacyAccount(
            id = id,
            name = "AccountProfileName",
            email = "demo@example.com",
            profile = ProfileDto(
                id = id,
                name = "AccountProfileName",
                color = 0xFF0000,
                avatar = AvatarDto(
                    avatarType = AvatarTypeDto.ICON,
                    avatarMonogram = null,
                    avatarImageUri = null,
                    avatarIconName = "star",
                ),
            ),
            identities = listOf(
                Identity(
                    description = "Demo User",
                    signatureUse = defaultSignature != null,
                    signature = defaultSignature?.text,
                    signatureIsHtml = defaultSignature?.isHtml == true,
                ),
            ),
            incomingServerSettings = ServerSettings(
                type = "imap",
                host = "imap.example.com",
                port = 993,
                connectionSecurity = ConnectionSecurity.SSL_TLS_REQUIRED,
                authenticationType = AuthType.PLAIN,
                username = "test",
                password = "password",
                clientCertificateAlias = null,
            ),
            outgoingServerSettings = ServerSettings(
                type = "smtp",
                host = "smtp.example.com",
                port = 465,
                connectionSecurity = ConnectionSecurity.SSL_TLS_REQUIRED,
                authenticationType = AuthType.PLAIN,
                username = "test",
                password = "password",
                clientCertificateAlias = null,
            ),
            signatures = signatures,
        )
    }
}
