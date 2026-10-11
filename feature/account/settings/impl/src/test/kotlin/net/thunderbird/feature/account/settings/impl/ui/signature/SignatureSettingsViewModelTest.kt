package net.thunderbird.feature.account.settings.impl.ui.signature

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.fsck.k9.mail.AuthType
import com.fsck.k9.mail.ConnectionSecurity
import com.fsck.k9.mail.ServerSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.thunderbird.components.core.outcome.Outcome
import net.thunderbird.components.ui.testing.coroutines.MainDispatcherHelper
import net.thunderbird.core.android.account.AccountSignature
import net.thunderbird.core.android.account.Identity
import net.thunderbird.core.android.account.LegacyAccount
import net.thunderbird.core.logging.testing.TestLogger
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.AccountIdFactory
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract.AccountSettingError
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract.UpdateSignaturesCommand
import net.thunderbird.feature.account.settings.impl.domain.AccountSettingsDomainContract.UseCase
import net.thunderbird.feature.account.settings.impl.domain.AccountSignatures
import net.thunderbird.feature.account.settings.impl.ui.signature.SignatureSettingsContract.Effect
import net.thunderbird.feature.account.settings.impl.ui.signature.SignatureSettingsContract.Error
import net.thunderbird.feature.account.settings.impl.ui.signature.SignatureSettingsContract.Event
import net.thunderbird.feature.account.storage.profile.AvatarDto
import net.thunderbird.feature.account.storage.profile.AvatarTypeDto
import net.thunderbird.feature.account.storage.profile.ProfileDto

@OptIn(ExperimentalCoroutinesApi::class)
class SignatureSettingsViewModelTest {

    private val mainDispatcher = MainDispatcherHelper()
    private val commands = mutableListOf<UpdateSignaturesCommand>()

    @BeforeTest
    fun setUp() {
        mainDispatcher.setUp()
    }

    @AfterTest
    fun tearDown() {
        mainDispatcher.tearDown()
    }

    @Test
    fun `should load account name and signatures`() = runTest {
        // Arrange
        val testSubject = createTestSubject(signatures = listOf(WORK_SIGNATURE))

        // Act
        advanceUntilIdle()

        // Assert
        assertThat(testSubject.state.value.subtitle).isEqualTo(ACCOUNT_NAME)
        assertThat(testSubject.state.value.signatures).containsExactly(WORK_SIGNATURE)
    }

    @Test
    fun `should show load error when account is missing`() = runTest {
        // Arrange
        val testSubject = createTestSubject(
            getLegacyAccount = { Outcome.failure(AccountSettingError.NotFound("Account not found")) },
        )

        // Act
        advanceUntilIdle()

        // Assert
        assertThat(testSubject.state.value.error).isEqualTo(Error.LoadFailed)
    }

    @Test
    fun `should save a new signature with a generated id and close the editor`() = runTest {
        // Arrange
        val testSubject = createTestSubject()
        advanceUntilIdle()

        // Act
        testSubject.event(Event.OnAddClick)
        testSubject.event(Event.OnNameChange("  Work "))
        testSubject.event(Event.OnTextChange(WORK_SIGNATURE.text))
        testSubject.event(Event.OnIsHtmlChange(true))
        testSubject.event(Event.OnSaveClick)
        advanceUntilIdle()

        // Assert
        val expected = WORK_SIGNATURE.copy(id = GENERATED_ID)
        assertThat(commands).containsExactly(UpdateSignaturesCommand.Save(expected))
        assertThat(testSubject.state.value.signatures).containsExactly(expected)
        assertThat(testSubject.state.value.editor).isNull()
    }

    @Test
    fun `should keep the id when saving an edited signature`() = runTest {
        // Arrange
        val testSubject = createTestSubject(signatures = listOf(WORK_SIGNATURE))
        advanceUntilIdle()

        // Act
        testSubject.event(Event.OnSignatureClick(WORK_SIGNATURE.id))
        testSubject.event(Event.OnNameChange("Office"))
        testSubject.event(Event.OnSaveClick)
        advanceUntilIdle()

        // Assert
        assertThat(commands).containsExactly(UpdateSignaturesCommand.Save(WORK_SIGNATURE.copy(name = "Office")))
    }

    @Test
    fun `should not save while name or text is blank`() = runTest {
        // Arrange
        val testSubject = createTestSubject()
        advanceUntilIdle()

        // Act
        testSubject.event(Event.OnAddClick)
        testSubject.event(Event.OnNameChange("Work"))
        testSubject.event(Event.OnSaveClick)
        advanceUntilIdle()

        // Assert
        assertThat(testSubject.state.value.editor).isNotNull().transform { it.canSave }.isFalse()
        assertThat(commands).containsExactly()
    }

    @Test
    fun `should keep the editor open and show an error when saving fails`() = runTest {
        // Arrange
        val testSubject = createTestSubject(
            updateSignatures = { _, _ -> Outcome.failure(AccountSettingError.StorageError("Failed")) },
        )
        advanceUntilIdle()

        // Act
        testSubject.event(Event.OnAddClick)
        testSubject.event(Event.OnNameChange("Work"))
        testSubject.event(Event.OnTextChange("Jane"))
        testSubject.event(Event.OnSaveClick)
        advanceUntilIdle()

        // Assert
        assertThat(testSubject.state.value.error).isEqualTo(Error.SaveFailed)
        assertThat(testSubject.state.value.editor).isNotNull().transform { it.canSave }.isTrue()
    }

    @Test
    fun `should delete a signature only after confirmation`() = runTest {
        // Arrange
        val testSubject = createTestSubject(signatures = listOf(WORK_SIGNATURE, PRIVATE_SIGNATURE))
        advanceUntilIdle()

        // Act
        testSubject.event(Event.OnDeleteClick(WORK_SIGNATURE.id))
        advanceUntilIdle()
        val pendingDeletion = testSubject.state.value.signaturePendingDeletion
        val commandsBeforeConfirmation = commands.toList()
        testSubject.event(Event.OnDeleteConfirm)
        advanceUntilIdle()

        // Assert
        assertThat(pendingDeletion).isEqualTo(WORK_SIGNATURE)
        assertThat(commandsBeforeConfirmation).containsExactly()
        assertThat(commands).containsExactly(UpdateSignaturesCommand.Delete(WORK_SIGNATURE.id))
        assertThat(testSubject.state.value.signatures).containsExactly(PRIVATE_SIGNATURE)
        assertThat(testSubject.state.value.signaturePendingDeletion).isNull()
    }

    @Test
    fun `should mark the saved signature matching the account signature as default`() = runTest {
        // Arrange
        val testSubject = createTestSubject(
            signatures = listOf(WORK_SIGNATURE, PRIVATE_SIGNATURE),
            defaultSignature = PRIVATE_SIGNATURE,
        )

        // Act
        advanceUntilIdle()

        // Assert
        assertThat(testSubject.state.value.defaultSignatureId).isEqualTo(PRIVATE_SIGNATURE.id)
    }

    @Test
    fun `default click should set the default and a second click should remove it`() = runTest {
        // Arrange
        val testSubject = createTestSubject(signatures = listOf(WORK_SIGNATURE, PRIVATE_SIGNATURE))
        advanceUntilIdle()

        // Act
        testSubject.event(Event.OnDefaultClick(WORK_SIGNATURE.id))
        advanceUntilIdle()
        val defaultAfterFirstClick = testSubject.state.value.defaultSignatureId
        testSubject.event(Event.OnDefaultClick(WORK_SIGNATURE.id))
        advanceUntilIdle()

        // Assert
        assertThat(defaultAfterFirstClick).isEqualTo(WORK_SIGNATURE.id)
        assertThat(testSubject.state.value.defaultSignatureId).isNull()
        assertThat(commands).containsExactly(
            UpdateSignaturesCommand.SetDefault(WORK_SIGNATURE.id),
            UpdateSignaturesCommand.SetDefault(null),
        )
    }

    @Test
    fun `back should close the editor before leaving the screen`() = runTest {
        // Arrange
        val testSubject = createTestSubject()
        advanceUntilIdle()
        testSubject.event(Event.OnAddClick)

        testSubject.effect.test {
            // Act
            testSubject.event(Event.OnBackPressed)
            val editorAfterFirstBack = testSubject.state.value.editor
            testSubject.event(Event.OnBackPressed)

            // Assert
            assertThat(editorAfterFirstBack).isNull()
            assertThat(awaitItem()).isEqualTo(Effect.NavigateBack)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should open the editor for the signature the screen was opened for and leave on back`() = runTest {
        // Arrange
        val testSubject = createTestSubject(
            signatures = listOf(WORK_SIGNATURE, PRIVATE_SIGNATURE),
            initialSignatureId = PRIVATE_SIGNATURE.id,
        )
        advanceUntilIdle()
        val editor = testSubject.state.value.editor

        testSubject.effect.test {
            // Act
            testSubject.event(Event.OnBackPressed)

            // Assert
            assertThat(editor).isNotNull().transform { it.signatureId }.isEqualTo(PRIVATE_SIGNATURE.id)
            assertThat(awaitItem()).isEqualTo(Effect.NavigateBack)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should leave after saving the signature the screen was opened for`() = runTest {
        // Arrange
        val testSubject = createTestSubject(
            signatures = listOf(WORK_SIGNATURE),
            initialSignatureId = WORK_SIGNATURE.id,
        )
        advanceUntilIdle()

        testSubject.effect.test {
            // Act
            testSubject.event(Event.OnTextChange("Jane"))
            testSubject.event(Event.OnSaveClick)
            advanceUntilIdle()

            // Assert
            assertThat(commands).containsExactly(UpdateSignaturesCommand.Save(WORK_SIGNATURE.copy(text = "Jane")))
            assertThat(awaitItem()).isEqualTo(Effect.NavigateBack)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `should show the list when the signature the screen was opened for is gone`() = runTest {
        // Arrange
        val testSubject = createTestSubject(signatures = listOf(WORK_SIGNATURE), initialSignatureId = "unknown")

        // Act
        advanceUntilIdle()

        // Assert
        assertThat(testSubject.state.value.editor).isNull()
        assertThat(testSubject.state.value.signatures).containsExactly(WORK_SIGNATURE)
    }

    private fun createTestSubject(
        signatures: List<AccountSignature> = emptyList(),
        initialSignatureId: String? = null,
        defaultSignature: AccountSignature? = null,
        getLegacyAccount: UseCase.GetLegacyAccount = UseCase.GetLegacyAccount { accountId ->
            Outcome.success(createLegacyAccount(accountId, signatures, defaultSignature))
        },
        updateSignatures: UseCase.UpdateSignatures = fakeUpdateSignatures(signatures),
    ): SignatureSettingsViewModel {
        return SignatureSettingsViewModel(
            accountId = AccountIdFactory.create(),
            getAccountName = { flowOf(Outcome.success(ACCOUNT_NAME)) },
            getLegacyAccount = getLegacyAccount,
            updateSignatures = { accountId, command ->
                commands.add(command)
                updateSignatures(accountId, command)
            },
            logger = TestLogger(),
            initialSignatureId = initialSignatureId,
            dispatcher = mainDispatcher.testDispatcher,
            signatureIdGenerator = { GENERATED_ID },
        )
    }

    private fun fakeUpdateSignatures(initial: List<AccountSignature>) = UseCase.UpdateSignatures { _, command ->
        Outcome.success(
            when (command) {
                is UpdateSignaturesCommand.Save -> AccountSignatures(
                    signatures = initial.filterNot { it.id == command.signature.id } + command.signature,
                    defaultSignatureId = null,
                )

                is UpdateSignaturesCommand.Delete -> AccountSignatures(
                    signatures = initial.filterNot { it.id == command.signatureId },
                    defaultSignatureId = null,
                )

                is UpdateSignaturesCommand.SetDefault -> AccountSignatures(
                    signatures = initial,
                    defaultSignatureId = command.signatureId,
                )
            },
        )
    }

    private companion object {
        const val ACCOUNT_NAME = "My Account"
        const val GENERATED_ID = "generated-id"

        val WORK_SIGNATURE = AccountSignature(id = "work", name = "Work", text = "<b>Jane Doe</b>", isHtml = true)
        val PRIVATE_SIGNATURE = AccountSignature(id = "private", name = "Private", text = "Cheers\nJane")

        fun createLegacyAccount(
            accountId: AccountId,
            signatures: List<AccountSignature>,
            defaultSignature: AccountSignature?,
        ) = LegacyAccount(
            id = accountId,
            name = ACCOUNT_NAME,
            email = "demo@example.com",
            profile = ProfileDto(
                id = accountId,
                name = ACCOUNT_NAME,
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
