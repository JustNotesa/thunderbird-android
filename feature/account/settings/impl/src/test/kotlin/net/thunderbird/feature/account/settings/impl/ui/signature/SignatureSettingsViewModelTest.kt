package net.thunderbird.feature.account.settings.impl.ui.signature

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
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
import net.thunderbird.feature.account.settings.impl.ui.signature.SignatureSettingsContract.EditorMode
import net.thunderbird.feature.account.settings.impl.ui.signature.SignatureSettingsContract.Effect
import net.thunderbird.feature.account.settings.impl.ui.signature.SignatureSettingsContract.Error
import net.thunderbird.feature.account.settings.impl.ui.signature.SignatureSettingsContract.Event
import net.thunderbird.feature.account.storage.profile.AvatarDto
import net.thunderbird.feature.account.storage.profile.AvatarTypeDto
import net.thunderbird.feature.account.storage.profile.ProfileDto
import net.thunderbird.feature.mail.message.composer.richtext.FormattedRange
import net.thunderbird.feature.mail.message.composer.richtext.FormattedText
import net.thunderbird.feature.mail.message.composer.richtext.RichTextHtmlConverter
import net.thunderbird.feature.mail.message.composer.richtext.TextFormat

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
        testSubject.event(Event.OnFormattedTextChange("Jane Doe", emptyList()))
        testSubject.event(Event.OnSaveClick)
        advanceUntilIdle()

        // Assert
        val expected = AccountSignature(id = GENERATED_ID, name = "Work", text = "Jane Doe", isHtml = false)
        assertThat(commands).containsExactly(UpdateSignaturesCommand.Save(expected))
        assertThat(testSubject.state.value.signatures).containsExactly(expected)
        assertThat(testSubject.state.value.editor).isNull()
    }

    @Test
    fun `should keep the id when saving an edited signature`() = runTest {
        // Arrange
        val testSubject = createTestSubject(signatures = listOf(PRIVATE_SIGNATURE))
        advanceUntilIdle()

        // Act
        testSubject.event(Event.OnSignatureClick(PRIVATE_SIGNATURE.id))
        testSubject.event(Event.OnNameChange("Office"))
        testSubject.event(Event.OnSaveClick)
        advanceUntilIdle()

        // Assert
        assertThat(commands).containsExactly(UpdateSignaturesCommand.Save(PRIVATE_SIGNATURE.copy(name = "Office")))
    }

    @Test
    fun `should save text the user formatted as HTML`() = runTest {
        // Arrange
        val testSubject = createTestSubject()
        advanceUntilIdle()

        // Act
        testSubject.event(Event.OnAddClick)
        testSubject.event(Event.OnNameChange("Work"))
        testSubject.event(Event.OnFormattedTextChange("Jane Doe", listOf(FormattedRange(0, 4, TextFormat.Bold))))
        testSubject.event(Event.OnSaveClick)
        advanceUntilIdle()

        // Assert
        assertThat(commands).containsExactly(
            UpdateSignaturesCommand.Save(
                AccountSignature(id = GENERATED_ID, name = "Work", text = "html[Jane Doe|Bold:0-4]", isHtml = true),
            ),
        )
    }

    @Test
    fun `should open an HTML signature the formatted editor can show as formatted text`() = runTest {
        // Arrange
        val testSubject = createTestSubject(signatures = listOf(WORK_SIGNATURE))
        advanceUntilIdle()

        // Act
        testSubject.event(Event.OnSignatureClick(WORK_SIGNATURE.id))

        // Assert
        val editor = testSubject.state.value.editor!!
        assertThat(editor.mode).isEqualTo(EditorMode.Formatted)
        assertThat(editor.text).isEqualTo("text of <b>Jane Doe</b>")
        assertThat(editor.ranges).containsExactly(FormattedRange(0, 4, TextFormat.Bold))
        assertThat(editor.previewHtml).isNull()
    }

    @Test
    fun `should open an HTML signature the formatted editor can not show as HTML code with a preview`() = runTest {
        // Arrange
        val testSubject = createTestSubject(signatures = listOf(LINK_SIGNATURE))
        advanceUntilIdle()

        // Act
        testSubject.event(Event.OnSignatureClick(LINK_SIGNATURE.id))
        testSubject.event(Event.OnSaveClick)
        advanceUntilIdle()

        // Assert
        assertThat(commands).containsExactly(UpdateSignaturesCommand.Save(LINK_SIGNATURE))
    }

    @Test
    fun `should show a sanitized preview while editing HTML code`() = runTest {
        // Arrange
        val testSubject = createTestSubject(signatures = listOf(LINK_SIGNATURE))
        advanceUntilIdle()

        // Act
        testSubject.event(Event.OnSignatureClick(LINK_SIGNATURE.id))
        val modeAfterOpening = testSubject.state.value.editor?.mode
        testSubject.event(Event.OnTextChange("<i>Jane</i>"))

        // Assert
        assertThat(modeAfterOpening).isEqualTo(EditorMode.Code)
        assertThat(testSubject.state.value.editor?.previewHtml).isEqualTo("sanitized:<i>Jane</i>")
    }

    @Test
    fun `switching to HTML code should turn the formatted text into HTML`() = runTest {
        // Arrange
        val testSubject = createTestSubject()
        advanceUntilIdle()
        testSubject.event(Event.OnAddClick)
        testSubject.event(Event.OnFormattedTextChange("Jane Doe", listOf(FormattedRange(0, 4, TextFormat.Bold))))

        // Act
        testSubject.event(Event.OnModeChange(EditorMode.Code))

        // Assert
        val editor = testSubject.state.value.editor!!
        assertThat(editor.mode).isEqualTo(EditorMode.Code)
        assertThat(editor.text).isEqualTo("html[Jane Doe|Bold:0-4]")
        assertThat(editor.ranges).isEmpty()
        assertThat(editor.previewHtml).isEqualTo("sanitized:html[Jane Doe|Bold:0-4]")
    }

    @Test
    fun `switching to formatted text should ask first when it would remove HTML`() = runTest {
        // Arrange
        val testSubject = createTestSubject(signatures = listOf(LINK_SIGNATURE))
        advanceUntilIdle()
        testSubject.event(Event.OnSignatureClick(LINK_SIGNATURE.id))

        // Act
        testSubject.event(Event.OnModeChange(EditorMode.Formatted))
        val editorWhileAsking = testSubject.state.value.editor!!
        testSubject.event(Event.OnFormattedModeDismiss)
        val editorAfterDismissing = testSubject.state.value.editor!!
        testSubject.event(Event.OnModeChange(EditorMode.Formatted))
        testSubject.event(Event.OnFormattedModeConfirm)

        // Assert
        assertThat(editorWhileAsking.isConfirmingFormattedMode).isTrue()
        assertThat(editorWhileAsking.mode).isEqualTo(EditorMode.Code)
        assertThat(editorAfterDismissing.isConfirmingFormattedMode).isFalse()
        assertThat(editorAfterDismissing.text).isEqualTo(LINK_SIGNATURE.text)
        val editor = testSubject.state.value.editor!!
        assertThat(editor.mode).isEqualTo(EditorMode.Formatted)
        assertThat(editor.text).isEqualTo("text of ${LINK_SIGNATURE.text}")
        assertThat(editor.isConfirmingFormattedMode).isFalse()
    }

    @Test
    fun `switching to formatted text should not ask when nothing would be removed`() = runTest {
        // Arrange
        val testSubject = createTestSubject()
        advanceUntilIdle()
        testSubject.event(Event.OnAddClick)
        testSubject.event(Event.OnModeChange(EditorMode.Code))
        testSubject.event(Event.OnTextChange("<b>Jane</b>"))

        // Act
        testSubject.event(Event.OnModeChange(EditorMode.Formatted))

        // Assert
        val editor = testSubject.state.value.editor!!
        assertThat(editor.mode).isEqualTo(EditorMode.Formatted)
        assertThat(editor.isConfirmingFormattedMode).isFalse()
        assertThat(editor.text).isEqualTo("text of <b>Jane</b>")
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
            signatures = listOf(PRIVATE_SIGNATURE),
            initialSignatureId = PRIVATE_SIGNATURE.id,
        )
        advanceUntilIdle()

        testSubject.effect.test {
            // Act
            testSubject.event(Event.OnFormattedTextChange("Jane", emptyList()))
            testSubject.event(Event.OnSaveClick)
            advanceUntilIdle()

            // Assert
            assertThat(commands).containsExactly(UpdateSignaturesCommand.Save(PRIVATE_SIGNATURE.copy(text = "Jane")))
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
            htmlSignatureSanitizer = { html -> "sanitized:$html" },
            richTextHtmlConverter = FakeRichTextHtmlConverter(),
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
        val LINK_SIGNATURE = AccountSignature(
            id = "link",
            name = "Link",
            text = """<a href="https://example.com">Jane</a>""",
            isHtml = true,
        )

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

/**
 * Writes HTML that shows what it was made from, reads every HTML as text with the first four characters in bold, and
 * can't represent links.
 */
private class FakeRichTextHtmlConverter : RichTextHtmlConverter {
    override fun toHtmlFragment(text: String, ranges: List<FormattedRange>): String {
        return "html[$text|${ranges.joinToString { "${it.format}:${it.start}-${it.end}" }}]"
    }

    override fun fromHtmlFragment(html: String): FormattedText {
        return FormattedText(text = "text of $html", ranges = listOf(FormattedRange(0, 4, TextFormat.Bold)))
    }

    override fun canRepresent(html: String): Boolean = "<a " !in html
}
