package net.thunderbird.feature.navigation.drawer.dropdown.ui

import app.k9mail.core.ui.compose.testing.mvi.advanceUntilIdle
import app.k9mail.core.ui.compose.testing.mvi.assertThatAndEffectTurbineConsumed
import app.k9mail.core.ui.compose.testing.mvi.runMviTest
import app.k9mail.core.ui.compose.testing.mvi.turbinesWithInitialStateCheck
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.thunderbird.components.ui.testing.coroutines.MainDispatcherHelper
import net.thunderbird.core.preference.display.inboxSettings.UnifiedFoldersLayout
import net.thunderbird.feature.account.AccountIdFactory
import net.thunderbird.feature.account.UnifiedAccountId
import net.thunderbird.feature.mail.folder.FolderType
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.MailDisplayAccount
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.UnifiedDisplayAccount
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.UnifiedDisplayFolderType
import net.thunderbird.feature.navigation.drawer.dropdown.ui.DrawerContract.Effect
import net.thunderbird.feature.navigation.drawer.dropdown.ui.DrawerContract.Event
import net.thunderbird.feature.navigation.drawer.dropdown.ui.DrawerContract.State
import net.thunderbird.feature.navigation.drawer.dropdown.ui.FakeData.MAIL_DISPLAY_ACCOUNT

@OptIn(ExperimentalCoroutinesApi::class)
internal class DrawerViewModelTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val mainDispatcher = MainDispatcherHelper(UnconfinedTestDispatcher())

    @BeforeTest
    fun setUp() {
        mainDispatcher.setUp()
    }

    @AfterTest
    fun tearDown() {
        mainDispatcher.tearDown()
    }

    @Test
    fun `should collect drawer config`() = runTest {
        val drawerConfig = createDrawerConfig()
        val getDrawerConfigFlow = MutableStateFlow(drawerConfig)
        val testSubject = createTestSubject(
            drawerConfigFlow = getDrawerConfigFlow,
        )

        advanceUntilIdle()

        assertThat(testSubject.state.value.config).isEqualTo(drawerConfig)

        val newDrawerConfig = createDrawerConfig(showUnifiedInbox = true)

        getDrawerConfigFlow.emit(newDrawerConfig)

        advanceUntilIdle()

        assertThat(testSubject.state.value.config).isEqualTo(newDrawerConfig)
    }

    @Test
    fun `should change loading state when OnSyncAccount event is received`() = runMviTest {
        val initialState = State(
            accounts = listOf(MAIL_DISPLAY_ACCOUNT).toImmutableList(),
            selectedAccountId = MAIL_DISPLAY_ACCOUNT.id,
        )
        val testSubject = createTestSubject(
            initialState = initialState,
            displayAccountsFlow = flow { emit(listOf(MAIL_DISPLAY_ACCOUNT)) },
            syncAccountFlow = flow {
                delay(25)
                emit(Result.success(Unit))
            },
        )
        val turbines = turbinesWithInitialStateCheck(testSubject, initialState)

        testSubject.event(Event.OnSyncAccount)

        assertThat(turbines.stateTurbine.awaitItem()).isEqualTo(initialState.copy(isLoading = true))
        assertThat(turbines.stateTurbine.awaitItem()).isEqualTo(initialState.copy(isLoading = false))
    }

    @Test
    fun `should skip loading when no account is selected and OnSyncAccount event is received`() = runMviTest {
        val initialState = State(selectedAccountId = null)
        var counter = 0
        val testSubject = createTestSubject(
            initialState = initialState,
            syncAccountFlow = flow {
                delay(25)
                counter++
                emit(Result.success(Unit))
            },
        )

        val turbines = turbinesWithInitialStateCheck(testSubject, initialState)

        testSubject.event(Event.OnSyncAccount)

        advanceUntilIdle()

        assertThat(testSubject.state.value.isLoading).isEqualTo(false)
        assertThat(counter).isEqualTo(0)

        turbines.stateTurbine.ensureAllEventsConsumed()
        turbines.effectTurbine.ensureAllEventsConsumed()
    }

    @Test
    fun `should skip loading when already loading and OnSyncAccount event received`() = runMviTest {
        val initialState = State(isLoading = true)
        var counter = 0
        val testSubject = createTestSubject(
            initialState = initialState,
            syncAccountFlow = flow {
                counter++
                delay(25)
                emit(Result.success(Unit))
            },
        )
        val turbines = turbinesWithInitialStateCheck(testSubject, initialState)

        testSubject.event(Event.OnSyncAccount)

        advanceUntilIdle()

        assertThat(testSubject.state.value.isLoading).isEqualTo(true)
        assertThat(counter).isEqualTo(0)

        turbines.stateTurbine.ensureAllEventsConsumed()
        turbines.effectTurbine.ensureAllEventsConsumed()
    }

    @Test
    fun `should change loading state when OnSyncAllAccounts event is received`() = runMviTest {
        val testSubject = createTestSubject(
            syncAllAccounts = flow {
                delay(25)
                emit(Result.success(Unit))
            },
        )
        val initialState = State(isLoading = false)
        val turbines = turbinesWithInitialStateCheck(testSubject, initialState)

        testSubject.event(Event.OnSyncAllAccounts)

        assertThat(turbines.stateTurbine.awaitItem()).isEqualTo(State(isLoading = true))
        assertThat(turbines.stateTurbine.awaitItem()).isEqualTo(State(isLoading = false))
    }

    @Test
    fun `should skip loading when already loading and OnSyncAllAccounts event received`() = runMviTest {
        val initialState = State(isLoading = true)
        var counter = 0
        val testSubject = createTestSubject(
            initialState = initialState,
            syncAccountFlow = flow {
                counter++
                delay(25)
                emit(Result.success(Unit))
            },
        )
        val turbines = turbinesWithInitialStateCheck(testSubject, initialState)

        testSubject.event(Event.OnSyncAllAccounts)

        advanceUntilIdle()

        assertThat(testSubject.state.value.isLoading).isEqualTo(true)
        assertThat(counter).isEqualTo(0)

        turbines.stateTurbine.ensureAllEventsConsumed()
        turbines.effectTurbine.ensureAllEventsConsumed()
    }

    @Test
    fun `should collect display accounts when created and select first as selected`() = runTest {
        val displayAccounts = createDisplayAccountList(2)
        val getDisplayAccountsFlow = MutableStateFlow(displayAccounts)
        val testSubject = createTestSubject(
            displayAccountsFlow = getDisplayAccountsFlow,
        )

        advanceUntilIdle()

        assertThat(testSubject.state.value.accounts.size).isEqualTo(displayAccounts.size)
        assertThat(testSubject.state.value.accounts).isEqualTo(displayAccounts)
        assertThat(testSubject.state.value.selectedAccountId).isEqualTo(displayAccounts.first().id)
    }

    @Test
    fun `should reselect selected account when old not present anymore`() = runTest {
        val displayAccounts = createDisplayAccountList(3)
        val getDisplayAccountsFlow = MutableStateFlow(displayAccounts)
        val testSubject = createTestSubject(
            displayAccountsFlow = getDisplayAccountsFlow,
        )

        advanceUntilIdle()

        val newDisplayAccounts = displayAccounts.drop(1)
        getDisplayAccountsFlow.emit(newDisplayAccounts)

        advanceUntilIdle()

        assertThat(testSubject.state.value.accounts.size).isEqualTo(newDisplayAccounts.size)
        assertThat(testSubject.state.value.accounts).isEqualTo(newDisplayAccounts)
        assertThat(testSubject.state.value.selectedAccountId).isEqualTo(newDisplayAccounts.first().id)
    }

    @Test
    fun `should set selected account to null when no accounts are present`() = runTest {
        val getDisplayAccountsFlow = MutableStateFlow(emptyList<MailDisplayAccount>())
        val testSubject = createTestSubject(
            displayAccountsFlow = getDisplayAccountsFlow,
        )

        advanceUntilIdle()

        assertThat(testSubject.state.value.accounts.size).isEqualTo(0)
        assertThat(testSubject.state.value.selectedAccountId).isEqualTo(null)
    }

    @Test
    fun `should select the account and show its folders when OnAccountClick event is received`() = runMviTest {
        val displayAccounts = createDisplayAccountList(3)
        val getDisplayAccountsFlow = MutableStateFlow(displayAccounts)
        val testSubject = createTestSubject(
            initialState = State(showAccountSelection = true),
            displayAccountsFlow = getDisplayAccountsFlow,
        )
        val turbines = turbinesWithInitialStateCheck(
            testSubject,
            State(
                accounts = displayAccounts.toImmutableList(),
                selectedAccountId = displayAccounts.first().id,
                showAccountSelection = true,
            ),
        )

        advanceUntilIdle()

        testSubject.event(Event.OnAccountClick(displayAccounts[1]))

        advanceUntilIdle()

        assertThat(turbines.awaitStateItem().selectedAccountId).isEqualTo(displayAccounts[1].id)
        assertThat(turbines.awaitStateItem().showAccountSelection).isEqualTo(false)

        turbines.stateTurbine.ensureAllEventsConsumed()
        turbines.effectTurbine.expectNoEvents()
    }

    @Test
    fun `should not emit any effect when OnAccountClick event is received for an account with auto expand folder`() =
        runMviTest {
            val displayAccounts = createDisplayAccountList(1) + createDisplayAccount(
                id = AccountIdFactory.create(),
                hasAutoExpandFolder = true,
            )
            val getDisplayAccountsFlow = MutableStateFlow(displayAccounts)
            val testSubject = createTestSubject(
                initialState = State(showAccountSelection = true),
                displayAccountsFlow = getDisplayAccountsFlow,
            )
            val turbines = turbinesWithInitialStateCheck(
                testSubject,
                State(
                    accounts = displayAccounts.toImmutableList(),
                    selectedAccountId = displayAccounts.first().id,
                    showAccountSelection = true,
                ),
            )

            advanceUntilIdle()

            testSubject.event(Event.OnAccountClick(displayAccounts[1]))

            advanceUntilIdle()

            assertThat(turbines.awaitStateItem().selectedAccountId).isEqualTo(displayAccounts[1].id)
            assertThat(turbines.awaitStateItem().showAccountSelection).isEqualTo(false)

            turbines.stateTurbine.ensureAllEventsConsumed()
            turbines.effectTurbine.expectNoEvents()
        }

    @Test
    fun `should select the unified account and show its folders when the unified account is clicked`() = runMviTest {
        val displayAccounts = createDisplayAccountList(1)
        val getDisplayAccountsFlow = MutableStateFlow(displayAccounts)
        val testSubject = createTestSubject(
            initialState = State(showAccountSelection = true),
            displayAccountsFlow = getDisplayAccountsFlow,
        )
        val turbines = turbinesWithInitialStateCheck(
            testSubject,
            State(
                accounts = displayAccounts.toImmutableList(),
                selectedAccountId = displayAccounts.first().id,
                showAccountSelection = true,
            ),
        )

        advanceUntilIdle()

        testSubject.event(Event.OnAccountClick(createUnifiedDisplayAccount()))

        advanceUntilIdle()

        assertThat(turbines.awaitStateItem().selectedAccountId).isEqualTo(UnifiedAccountId)
        assertThat(turbines.awaitStateItem().showAccountSelection).isEqualTo(false)

        turbines.stateTurbine.ensureAllEventsConsumed()
        turbines.effectTurbine.expectNoEvents()
    }

    @Test
    fun `should collect display folders for selected account`() = runTest {
        val displayAccounts = createDisplayAccountList(3)
        val getDisplayAccountsFlow = MutableStateFlow(displayAccounts)
        val displayFoldersMap = mapOf(
            displayAccounts[0].id to createDisplayFolderList(3),
        )
        val displayFoldersFlow = MutableStateFlow(displayFoldersMap)
        val testSubject = createTestSubject(
            displayAccountsFlow = getDisplayAccountsFlow,
            displayFoldersFlow = displayFoldersFlow,
        )

        advanceUntilIdle()

        val displayFolders = displayFoldersMap[displayAccounts[0].id] ?: emptyList()
        assertThat(testSubject.state.value.folders.size).isEqualTo(displayFolders.size)
        assertThat(testSubject.state.value.folders).isEqualTo(displayFolders)
    }

    @Test
    fun `should collect display folders when selected account is changed`() = runTest {
        val displayAccounts = createDisplayAccountList(3)
        val getDisplayAccountsFlow = MutableStateFlow(displayAccounts)
        val displayFoldersMap = mapOf(
            displayAccounts[0].id to createDisplayFolderList(1),
            displayAccounts[1].id to createDisplayFolderList(5),
            displayAccounts[2].id to createDisplayFolderList(10),
        )
        val displayFoldersFlow = MutableStateFlow(displayFoldersMap)
        val testSubject = createTestSubject(
            displayAccountsFlow = getDisplayAccountsFlow,
            displayFoldersFlow = displayFoldersFlow,
        )

        advanceUntilIdle()

        testSubject.event(Event.SelectAccount(displayAccounts[1].id))

        advanceUntilIdle()

        val displayFolders = displayFoldersMap[displayAccounts[1].id] ?: emptyList()
        assertThat(testSubject.state.value.folders.size).isEqualTo(displayFolders.size)
        assertThat(testSubject.state.value.folders).isEqualTo(displayFolders)
    }

    @Test
    fun `should reload display folders when unified folders layout changes`() = runTest {
        val displayAccounts = createDisplayAccountList(1)
        val getDisplayAccountsFlow = MutableStateFlow(displayAccounts)
        val displayFoldersFlow = MutableStateFlow(mapOf(displayAccounts[0].id to createDisplayFolderList(1)))
        val getDrawerConfigFlow = MutableStateFlow(createDrawerConfig())
        val requestedLayouts = mutableListOf<UnifiedFoldersLayout>()
        val testSubject = createTestSubject(
            drawerConfigFlow = getDrawerConfigFlow,
            displayAccountsFlow = getDisplayAccountsFlow,
            displayFoldersFlow = displayFoldersFlow,
            requestedLayouts = requestedLayouts,
        )

        advanceUntilIdle()

        getDrawerConfigFlow.emit(createDrawerConfig(unifiedFoldersLayout = UnifiedFoldersLayout.Tree))

        advanceUntilIdle()

        assertThat(requestedLayouts).containsExactly(UnifiedFoldersLayout.Flat, UnifiedFoldersLayout.Tree)
    }

    @Test
    fun `should emit OpenFolder effect when OnFolderClick event is received`() = runMviTest {
        val displayAccounts = createDisplayAccountList(3)
        val getDisplayAccountsFlow = MutableStateFlow(displayAccounts)
        val displayFoldersMap = mapOf(
            displayAccounts[0].id to createDisplayFolderList(3),
        )
        val displayFoldersFlow = MutableStateFlow(displayFoldersMap)
        val initialState = State(
            accounts = displayAccounts.toImmutableList(),
            selectedAccountId = displayAccounts[0].id,
            folders = displayFoldersMap[displayAccounts[0].id]!!.toImmutableList(),
            selectedFolderId = displayFoldersMap[displayAccounts[0].id]!![0].folderId,
            selectedFolder = displayFoldersMap[displayAccounts[0].id]!![0],
        )
        val testSubject = createTestSubject(
            displayAccountsFlow = getDisplayAccountsFlow,
            displayFoldersFlow = displayFoldersFlow,
        )
        val turbines = turbinesWithInitialStateCheck(testSubject, initialState)

        advanceUntilIdle()

        val displayFolders = displayFoldersMap[displayAccounts[0].id] ?: emptyList()
        testSubject.event(Event.OnFolderClick(displayFolders[1]))

        // Consume the state update
        turbines.awaitStateItem()

        assertThat(turbines.awaitEffectItem()).isEqualTo(
            Effect.OpenFolder(
                accountId = requireNotNull(displayFolders[1].accountId),
                folderId = displayFolders[1].folder.id,
            ),
        )

        turbines.assertThatAndEffectTurbineConsumed {
            isEqualTo(Effect.CloseDrawer)
        }
    }

    @Test
    fun `should emit OpenUnifiedFolder when OnFolderClick event for unified folder is received`() =
        runMviTest {
            val displayAccounts = createDisplayAccountList(1)
            val getDisplayAccountsFlow = MutableStateFlow(displayAccounts)
            val unifiedFolder = createDisplayUnifiedFolder(
                id = UnifiedDisplayFolderType.SENT.id,
                unifiedType = UnifiedDisplayFolderType.SENT,
            )
            val displayFoldersMap = mapOf(
                displayAccounts[0].id to createDisplayFolderList(1) + listOf(unifiedFolder),
            )
            val displayFoldersFlow = MutableStateFlow(displayFoldersMap)
            val initialState = State(
                accounts = displayAccounts.toImmutableList(),
                selectedAccountId = displayAccounts[0].id,
                folders = displayFoldersMap[displayAccounts[0].id]!!.toImmutableList(),
                selectedFolderId = displayFoldersMap[displayAccounts[0].id]!![0].folderId,
                selectedFolder = displayFoldersMap[displayAccounts[0].id]!![0],
            )
            val testSubject = createTestSubject(
                displayAccountsFlow = getDisplayAccountsFlow,
                displayFoldersFlow = displayFoldersFlow,
            )
            val turbines = turbinesWithInitialStateCheck(testSubject, initialState)

            advanceUntilIdle()

            val displayFolders = displayFoldersMap[displayAccounts[0].id] ?: emptyList()
            testSubject.event(Event.OnFolderClick(displayFolders[1]))

            // Consume the state update
            turbines.awaitStateItem()

            assertThat(turbines.awaitEffectItem()).isEqualTo(Effect.OpenUnifiedFolder(FolderType.SENT))

            turbines.assertThatAndEffectTurbineConsumed {
                isEqualTo(Effect.CloseDrawer)
            }
        }

    @Test
    fun `should emit OpenSettings effect when OnSettingsClick event is received`() = runMviTest {
        val testSubject = createTestSubject()
        val turbines = turbinesWithInitialStateCheck(testSubject, State())

        testSubject.event(Event.OnSettingsClick)

        turbines.assertThatAndEffectTurbineConsumed {
            isEqualTo(Effect.OpenSettings)
        }
    }

    private fun createUnifiedDisplayAccount(): UnifiedDisplayAccount {
        return UnifiedDisplayAccount(
            unreadMessageCount = 0,
            starredMessageCount = 0,
            hasError = false,
        )
    }
}
