package app.k9mail.feature.settings.import.ui

import android.app.Application
import android.content.ContentResolver
import androidx.core.net.toUri
import app.k9mail.feature.settings.import.AccountConnectionChecker
import app.k9mail.feature.settings.import.ConnectionCheckResult
import app.k9mail.feature.settings.import.ExistingAccount
import app.k9mail.feature.settings.import.ExistingAccountMatcher
import app.k9mail.feature.settings.import.ExistingAccountProvider
import app.k9mail.feature.settings.import.SettingsImportExternalContract.AccountActivator
import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import assertk.assertions.prop
import com.fsck.k9.preferences.AccountDescription
import com.fsck.k9.preferences.AccountDescriptionPair
import com.fsck.k9.preferences.ImportContents
import com.fsck.k9.preferences.ImportResults
import com.fsck.k9.preferences.SettingsImportExportException
import com.fsck.k9.preferences.SettingsImporter
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestScope
import net.thunderbird.components.core.logging.testing.TestLogger
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SettingsImportViewModelTest {
    private val testScope = TestScope()
    private val contentResolver = mock<ContentResolver>()
    private val settingsImporter = mock<SettingsImporter>()
    private val accountActivator = mock<AccountActivator>()
    private val migrationManager = FakeMigrationManager(featureIncluded = true)
    private val importAppFetcher = mock<ImportAppFetcher>()
    private val existingAccountProvider = FakeExistingAccountProvider()
    private val connectionChecker = FakeAccountConnectionChecker()
    private val viewModel = SettingsImportViewModel(
        contentResolver = contentResolver,
        settingsImporter = settingsImporter,
        accountActivator = accountActivator,
        migrationManager = migrationManager,
        importAppFetcher = importAppFetcher,
        existingAccountMatcher = ExistingAccountMatcher(existingAccountProvider),
        connectionChecker = connectionChecker,
        backgroundDispatcher = Dispatchers.Unconfined,
        viewModelScope = testScope,
        logger = TestLogger(),
    )

    @Test
    fun `scanQrCodeButton and pickAppButton should be hidden when migration feature is disabled`() {
        migrationManager.featureIncluded = false

        viewModel.initialize()

        val uiModel = viewModel.getUiModel().value!!
        assertThat(uiModel.isScanQrCodeButtonVisible).isFalse()
        assertThat(uiModel.isPickAppButtonEnabled).isFalse()
    }

    @Test
    fun `pickAppButton should only be enabled after app from which we can import has been found`() {
        importAppFetcher.stub {
            on { isAtLeastOneAppInstalled() } doReturn true
        }
        viewModel.initialize()
        val uiModel = viewModel.getUiModel().value!!

        assertThat(uiModel.isPickAppButtonVisible).isTrue()
        assertThat(uiModel.isPickAppButtonEnabled).isFalse()

        // Check for apps we can import from
        testScope.testScheduler.advanceUntilIdle()

        assertThat(uiModel.isPickAppButtonVisible).isTrue()
        assertThat(uiModel.isPickAppButtonEnabled).isTrue()
    }

    @Test
    fun `pickAppButton should remain disabled when no app from which we can import has been found`() {
        importAppFetcher.stub {
            on { isAtLeastOneAppInstalled() } doReturn false
        }
        viewModel.initialize()
        val uiModelLiveData = viewModel.getUiModel()

        assertThat(uiModelLiveData.value!!.isPickAppButtonVisible).isTrue()
        assertThat(uiModelLiveData.value!!.isPickAppButtonEnabled).isFalse()

        // Check for apps we can import from
        testScope.testScheduler.advanceUntilIdle()

        assertThat(uiModelLiveData.value!!.isPickAppButtonVisible).isTrue()
        assertThat(uiModelLiveData.value!!.isPickAppButtonEnabled).isFalse()
    }

    @Test
    fun `clicking pickAppButton should disable buttons and emit PickApp event`() {
        importAppFetcher.stub {
            on { isAtLeastOneAppInstalled() } doReturn true
        }
        viewModel.initialize()
        val uiModelLiveData = viewModel.getUiModel()
        val actionEventsLiveData = viewModel.getActionEvents()

        // Check for apps we can import from
        testScope.testScheduler.advanceUntilIdle()

        viewModel.onPickAppButtonClicked()

        assertThat(uiModelLiveData.value!!.isPickDocumentButtonEnabled).isFalse()
        assertThat(uiModelLiveData.value!!.isPickAppButtonEnabled).isFalse()
        assertThat(actionEventsLiveData.value).isEqualTo(Action.PickApp)
    }

    @Test
    fun `setting ScanQrCode action should emit ScanQrCode event`() {
        viewModel.initialize()

        viewModel.setAction(SettingsImportAction.ScanQrCode)

        assertThat(viewModel.getActionEvents().value).isEqualTo(Action.ScanQrCode)
    }

    @Test
    fun `setting PasteAccounts action should emit PasteAccounts event`() {
        viewModel.initialize()

        viewModel.setAction(SettingsImportAction.PasteAccounts)

        assertThat(viewModel.getActionEvents().value).isEqualTo(Action.PasteAccounts)
    }

    @Test
    fun `canceling picking an app should enable buttons`() {
        importAppFetcher.stub {
            on { isAtLeastOneAppInstalled() } doReturn true
        }
        viewModel.initialize()
        val uiModelLiveData = viewModel.getUiModel()

        // Check for apps we can import from
        testScope.testScheduler.advanceUntilIdle()

        viewModel.onPickAppButtonClicked()
        viewModel.onAppPickCanceled()

        assertThat(uiModelLiveData.value!!.isPickDocumentButtonEnabled).isTrue()
        assertThat(uiModelLiveData.value!!.isPickAppButtonEnabled).isTrue()
    }

    @Test
    fun `picking an app should show loading progress, then settings list`() {
        importAppFetcher.stub {
            on { isAtLeastOneAppInstalled() } doReturn true
        }
        viewModel.initialize()
        val uiModelLiveData = viewModel.getUiModel()

        // Check for apps we can import from
        testScope.testScheduler.advanceUntilIdle()

        viewModel.onPickAppButtonClicked()
        viewModel.onAppPicked("net.thunderbird.android")

        assertThat(uiModelLiveData.value!!.isLoadingProgressVisible).isTrue()
        assertThat(uiModelLiveData.value!!.isPickDocumentButtonVisible).isFalse()
        assertThat(uiModelLiveData.value!!.isPickAppButtonVisible).isFalse()
        assertThat(uiModelLiveData.value!!.isSettingsListVisible).isFalse()

        val inputStream = "".byteInputStream()
        contentResolver.stub {
            on { openInputStream("content://net.thunderbird.android.settings/".toUri()) } doReturn inputStream
        }
        settingsImporter.stub {
            on { getImportStreamContents(inputStream) } doReturn ImportContents(
                globalSettings = false,
                accounts = listOf(
                    AccountDescription(name = "Account 1", uuid = "uuid-1"),
                    AccountDescription(name = "Account 2", uuid = "uuid-2"),
                ),
            )
        }

        // Read settings data from content provider
        testScope.testScheduler.advanceUntilIdle()

        assertThat(uiModelLiveData.value!!.isLoadingProgressVisible).isFalse()
        assertThat(uiModelLiveData.value!!.isPickDocumentButtonVisible).isFalse()
        assertThat(uiModelLiveData.value!!.isPickAppButtonVisible).isFalse()
        assertThat(uiModelLiveData.value!!.isSettingsListVisible).isTrue()
        assertThat(uiModelLiveData.value!!.settingsList).all {
            hasSize(2)
            index(0).isInstanceOf<SettingsListItem.Account>()
                .prop(SettingsListItem.Account::displayName).isEqualTo("Account 1")
            index(1).isInstanceOf<SettingsListItem.Account>()
                .prop(SettingsListItem.Account::displayName).isEqualTo("Account 2")
        }
    }

    @Test
    fun `failure to import settings from an app should show error message`() {
        importAppFetcher.stub {
            on { isAtLeastOneAppInstalled() } doReturn true
        }
        viewModel.initialize()
        val uiModelLiveData = viewModel.getUiModel()

        // Check for apps we can import from
        testScope.testScheduler.advanceUntilIdle()

        viewModel.onPickAppButtonClicked()
        viewModel.onAppPicked("net.thunderbird.android")

        val inputStream = "".byteInputStream()
        contentResolver.stub {
            on { openInputStream("content://net.thunderbird.android.settings/".toUri()) } doReturn inputStream
        }
        settingsImporter.stub {
            on { getImportStreamContents(inputStream) } doThrow SettingsImportExportException()
        }

        // Read settings data from content provider
        testScope.testScheduler.advanceUntilIdle()

        assertThat(uiModelLiveData.value!!.isLoadingProgressVisible).isFalse()
        assertThat(uiModelLiveData.value!!.isPickDocumentButtonVisible).isTrue()
        assertThat(uiModelLiveData.value!!.isPickAppButtonVisible).isTrue()
        assertThat(uiModelLiveData.value!!.isSettingsListVisible).isFalse()
        assertThat(uiModelLiveData.value!!.statusText).isEqualTo(StatusText.IMPORT_READ_FAILURE)
    }

    @Test
    fun `successfully importing settings from an app should show success message`() {
        importAppFetcher.stub {
            on { isAtLeastOneAppInstalled() } doReturn true
        }
        viewModel.initialize()
        val uiModelLiveData = viewModel.getUiModel()

        // Check for apps we can import from
        testScope.testScheduler.advanceUntilIdle()

        viewModel.onPickAppButtonClicked()
        viewModel.onAppPicked("net.thunderbird.android")

        val inputStream = "".byteInputStream()
        contentResolver.stub {
            on { openInputStream("content://net.thunderbird.android.settings/".toUri()) } doReturn inputStream
        }
        val accountOne = AccountDescription(name = "Account 1", uuid = "uuid-1")
        val accountTwo = AccountDescription(name = "Account 2", uuid = "uuid-2")
        settingsImporter.stub {
            on { getImportStreamContents(inputStream) } doReturn ImportContents(
                globalSettings = false,
                accounts = listOf(
                    accountOne,
                    accountTwo,
                ),
            )
        }

        // Read settings data from content provider
        testScope.testScheduler.advanceUntilIdle()

        // Deselect second account
        viewModel.onSettingsListItemClicked(1)

        viewModel.onImportButtonClicked()

        assertThat(uiModelLiveData.value!!.isImportProgressVisible).isTrue()
        assertThat(uiModelLiveData.value!!.isSettingsListEnabled).isFalse()
        assertThat(uiModelLiveData.value!!.importButton).isEqualTo(ButtonState.INVISIBLE)
        assertThat(uiModelLiveData.value!!.statusText).isEqualTo(StatusText.IMPORTING_PROGRESS)

        settingsImporter.stub {
            on { importSettings(inputStream, false, listOf("uuid-1")) } doReturn ImportResults(
                globalSettings = false,
                importedAccounts = listOf(
                    AccountDescriptionPair(
                        original = accountOne,
                        imported = accountOne,
                        authorizationNeeded = false,
                        incomingPasswordNeeded = false,
                        outgoingPasswordNeeded = false,
                        incomingServerName = "irrelevant",
                        outgoingServerName = "irrelevant",
                    ),
                ),
                erroneousAccounts = emptyList(),
            )
        }

        // Import settings
        testScope.testScheduler.advanceUntilIdle()

        assertThat(uiModelLiveData.value!!.isImportProgressVisible).isFalse()
        assertThat(uiModelLiveData.value!!.isSettingsListEnabled).isTrue()
        assertThat(uiModelLiveData.value!!.importButton).isEqualTo(ButtonState.GONE)
        assertThat(uiModelLiveData.value!!.closeButton).isEqualTo(ButtonState.ENABLED)
        assertThat(uiModelLiveData.value!!.closeButtonLabel).isEqualTo(CloseButtonLabel.OK)
        assertThat(uiModelLiveData.value!!.statusText).isEqualTo(StatusText.IMPORT_SUCCESS)
    }

    @Test
    fun `account that is already set up should be unselected and not imported`() {
        existingAccountProvider.accounts = listOf(
            ExistingAccount(
                email = "user@example.com",
                incomingServerHost = "imap.example.com",
                incomingServerUsername = "user@example.com",
            ),
        )
        viewModel.initialize()
        val uiModelLiveData = viewModel.getUiModel()

        viewModel.onDocumentPicked("content://settings".toUri())

        val existingAccount = EXISTING_ACCOUNT
        val newAccount = NEW_ACCOUNT
        val inputStream = "".byteInputStream()
        contentResolver.stub {
            on { openInputStream("content://settings".toUri()) } doReturn inputStream
        }
        settingsImporter.stub {
            on { getImportStreamContents(inputStream) } doReturn ImportContents(
                globalSettings = false,
                accounts = listOf(existingAccount, newAccount),
            )
        }

        // Read settings data from the picked file
        testScope.testScheduler.advanceUntilIdle()

        assertThat(uiModelLiveData.value!!.settingsList).all {
            hasSize(2)
            index(0).isInstanceOf<SettingsListItem.Account>()
                .prop(SettingsListItem.Account::isAlreadySetUp).isTrue()
            index(0).prop(SettingsListItem::selected).isFalse()
            index(1).isInstanceOf<SettingsListItem.Account>()
                .prop(SettingsListItem.Account::isAlreadySetUp).isFalse()
            index(1).prop(SettingsListItem::selected).isTrue()
        }

        settingsImporter.stub {
            on { importSettings(inputStream, false, listOf("uuid-2")) } doReturn ImportResults(
                globalSettings = false,
                importedAccounts = listOf(
                    AccountDescriptionPair(
                        original = newAccount,
                        imported = newAccount,
                        authorizationNeeded = false,
                        incomingPasswordNeeded = false,
                        outgoingPasswordNeeded = false,
                        incomingServerName = "irrelevant",
                        outgoingServerName = "irrelevant",
                    ),
                ),
                erroneousAccounts = emptyList(),
            )
        }

        viewModel.onImportButtonClicked()

        // Import settings
        testScope.testScheduler.advanceUntilIdle()

        assertThat(uiModelLiveData.value!!.statusText).isEqualTo(StatusText.IMPORT_SUCCESS)
    }

    @Test
    fun `importing two accounts should check the connection of both accounts`() {
        val accountOne = AccountDescription(name = "Account 1", uuid = "uuid-1")
        val accountTwo = AccountDescription(name = "Account 2", uuid = "uuid-2")
        prepareImport(
            accounts = listOf(accountOne, accountTwo),
            importedAccounts = listOf(
                importedAccountPair(accountOne, "imported-uuid-1"),
                importedAccountPair(accountTwo, "imported-uuid-2"),
            ),
        )

        connectionChecker.gate = CompletableDeferred()
        viewModel.onImportButtonClicked()
        testScope.testScheduler.advanceUntilIdle()

        // The connection of both accounts is being checked
        assertThat(viewModel.getUiModel().value!!.settingsList).all {
            index(0).isInstanceOf<SettingsListItem.Account>()
                .prop(SettingsListItem.Account::connectionStatus).isEqualTo(ConnectionStatus.CHECKING)
            index(1).isInstanceOf<SettingsListItem.Account>()
                .prop(SettingsListItem.Account::connectionStatus).isEqualTo(ConnectionStatus.CHECKING)
        }
        assertThat(connectionChecker.checkedAccountUuids)
            .isEqualTo(listOf("imported-uuid-1", "imported-uuid-2"))

        connectionChecker.results = mapOf("imported-uuid-1" to ConnectionCheckResult.Connected)
        connectionChecker.defaultResult = ConnectionCheckResult.Failed
        connectionChecker.gate?.complete(Unit)
        testScope.testScheduler.advanceUntilIdle()

        assertThat(viewModel.getUiModel().value!!.settingsList).all {
            index(0).isInstanceOf<SettingsListItem.Account>()
                .prop(SettingsListItem.Account::connectionStatus).isEqualTo(ConnectionStatus.CONNECTED)
            index(1).isInstanceOf<SettingsListItem.Account>()
                .prop(SettingsListItem.Account::connectionStatus).isEqualTo(ConnectionStatus.FAILED)
        }
    }

    @Test
    fun `account that requires a password should not be checked`() {
        val account = AccountDescription(name = "Account 1", uuid = "uuid-1")
        prepareImport(
            accounts = listOf(account),
            importedAccounts = listOf(
                importedAccountPair(account, "imported-uuid-1").copy(incomingPasswordNeeded = true),
            ),
        )

        viewModel.onImportButtonClicked()
        testScope.testScheduler.advanceUntilIdle()

        assertThat(connectionChecker.checkedAccountUuids).isEmpty()
        assertThat(viewModel.getUiModel().value!!.settingsList).all {
            index(0).isInstanceOf<SettingsListItem.Account>()
                .prop(SettingsListItem.Account::connectionStatus).isEqualTo(ConnectionStatus.NOT_CHECKED)
        }
        assertThat(viewModel.getUiModel().value!!.statusText).isEqualTo(StatusText.IMPORT_SUCCESS_PASSWORD_REQUIRED)
    }

    @Test
    fun `status text should contain the number of imported accounts`() {
        val accountOne = AccountDescription(name = "Account 1", uuid = "uuid-1")
        val accountTwo = AccountDescription(name = "Account 2", uuid = "uuid-2")
        prepareImport(
            accounts = listOf(accountOne, accountTwo),
            importedAccounts = listOf(
                importedAccountPair(accountOne, "imported-uuid-1"),
                importedAccountPair(accountTwo, "imported-uuid-2"),
            ),
        )

        viewModel.onImportButtonClicked()
        testScope.testScheduler.advanceUntilIdle()

        assertThat(viewModel.getUiModel().value!!.statusText).isEqualTo(StatusText.IMPORT_SUCCESS)
        assertThat(viewModel.getUiModel().value!!.importedAccountCount).isEqualTo(2)
    }

    @Test
    fun `reminder to delete the note should be shown after importing accounts from the password manager`() {
        viewModel.onPasteAccountsButtonClicked()
        val account = AccountDescription(name = "Account 1", uuid = "uuid-1")
        prepareImport(
            accounts = listOf(account),
            importedAccounts = listOf(importedAccountPair(account, "imported-uuid-1")),
        )

        viewModel.onImportButtonClicked()
        testScope.testScheduler.advanceUntilIdle()

        assertThat(viewModel.getUiModel().value!!.statusText).isEqualTo(StatusText.IMPORT_SUCCESS)
        assertThat(viewModel.getUiModel().value!!.isPasswordManagerReminderVisible).isTrue()
    }

    @Test
    fun `reminder to delete the note should not be shown after importing accounts from a file`() {
        viewModel.onPickDocumentButtonClicked()
        val account = AccountDescription(name = "Account 1", uuid = "uuid-1")
        prepareImport(
            accounts = listOf(account),
            importedAccounts = listOf(importedAccountPair(account, "imported-uuid-1")),
        )

        viewModel.onImportButtonClicked()
        testScope.testScheduler.advanceUntilIdle()

        assertThat(viewModel.getUiModel().value!!.statusText).isEqualTo(StatusText.IMPORT_SUCCESS)
        assertThat(viewModel.getUiModel().value!!.isPasswordManagerReminderVisible).isFalse()
    }

    private fun prepareImport(
        accounts: List<AccountDescription>,
        importedAccounts: List<AccountDescriptionPair>,
    ) {
        viewModel.initialize()

        val contentUri = "content://settings".toUri()
        val inputStream = "".byteInputStream()
        contentResolver.stub {
            on { openInputStream(contentUri) } doReturn inputStream
        }
        settingsImporter.stub {
            on { getImportStreamContents(inputStream) } doReturn ImportContents(
                globalSettings = false,
                accounts = accounts,
            )
        }

        // Read settings data from the picked file
        viewModel.onDocumentPicked(contentUri)
        testScope.testScheduler.advanceUntilIdle()

        settingsImporter.stub {
            on { importSettings(inputStream, false, accounts.map { it.uuid }) } doReturn ImportResults(
                globalSettings = false,
                importedAccounts = importedAccounts,
                erroneousAccounts = emptyList(),
            )
        }
    }

    private fun importedAccountPair(original: AccountDescription, importedUuid: String): AccountDescriptionPair {
        return AccountDescriptionPair(
            original = original,
            imported = AccountDescription(name = original.name, uuid = importedUuid),
            authorizationNeeded = false,
            incomingPasswordNeeded = false,
            outgoingPasswordNeeded = false,
            incomingServerName = "irrelevant",
            outgoingServerName = "irrelevant",
        )
    }
}

private class FakeExistingAccountProvider : ExistingAccountProvider {
    var accounts: List<ExistingAccount> = emptyList()

    override fun getExistingAccounts(): List<ExistingAccount> = accounts
}

private class FakeAccountConnectionChecker : AccountConnectionChecker {
    var results: Map<String, ConnectionCheckResult> = emptyMap()
    var defaultResult: ConnectionCheckResult = ConnectionCheckResult.Connected
    var gate: CompletableDeferred<Unit>? = null
    val checkedAccountUuids = mutableListOf<String>()

    override suspend fun check(accountUuid: String): ConnectionCheckResult {
        checkedAccountUuids += accountUuid
        gate?.await()

        return results[accountUuid] ?: defaultResult
    }
}

private val EXISTING_ACCOUNT = AccountDescription(
    name = "Account 1",
    uuid = "uuid-1",
    email = "user@example.com",
    incomingServerHost = "imap.example.com",
    incomingServerUsername = "user@example.com",
)

private val NEW_ACCOUNT = AccountDescription(
    name = "Account 2",
    uuid = "uuid-2",
    email = "other@example.com",
    incomingServerHost = "imap.other.example.com",
    incomingServerUsername = "other@example.com",
)
