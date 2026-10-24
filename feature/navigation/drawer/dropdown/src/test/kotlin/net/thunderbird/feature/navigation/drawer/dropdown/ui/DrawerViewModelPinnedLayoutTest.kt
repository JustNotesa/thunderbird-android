package net.thunderbird.feature.navigation.drawer.dropdown.ui

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.thunderbird.components.ui.testing.coroutines.MainDispatcherHelper
import net.thunderbird.core.preference.display.inboxSettings.UnifiedFoldersLayout
import net.thunderbird.feature.account.UnifiedAccountId
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.UnifiedDisplayAccount
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.UnifiedDisplayFolder
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.UnifiedDisplayFolderType
import net.thunderbird.feature.navigation.drawer.dropdown.ui.DrawerContract.Event
import net.thunderbird.feature.navigation.drawer.dropdown.ui.DrawerContract.State

@OptIn(ExperimentalCoroutinesApi::class)
internal class DrawerViewModelPinnedLayoutTest {

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
    fun `should fill pinnedFolders when a regular account is selected and the layout is pinned`() = runTest {
        val testSubject = createPinnedTestSubject(UnifiedFoldersLayout.Pinned)

        advanceUntilIdle()

        val pinnedFolders = testSubject.state.value.pinnedFolders
        assertThat(pinnedFolders.size).isEqualTo(2)
        assertThat(pinnedFolders.map { it.displayFolder?.folderId }).containsExactly(
            UnifiedDisplayFolderType.INBOX.id,
            UnifiedDisplayFolderType.SENT.id,
        )
    }

    @Test
    fun `should leave pinnedFolders empty when the layout is flat`() = runTest {
        val testSubject = createPinnedTestSubject(UnifiedFoldersLayout.Flat)

        advanceUntilIdle()

        assertThat(testSubject.state.value.pinnedFolders).isEmpty()
    }

    @Test
    fun `should keep the selected account when the unified account is selected and a pinned folder is selected`() =
        runTest {
            val testSubject = createPinnedTestSubject(UnifiedFoldersLayout.Pinned)

            advanceUntilIdle()

            val accountId = testSubject.state.value.selectedAccountId
            testSubject.event(Event.SelectFolder(UnifiedDisplayFolderType.SENT.id))
            testSubject.event(Event.SelectAccount(UnifiedAccountId))

            advanceUntilIdle()

            assertThat(testSubject.state.value.selectedAccountId).isEqualTo(accountId)
            assertThat(testSubject.state.value.selectedFolderId).isEqualTo(UnifiedDisplayFolderType.SENT.id)
        }

    @Test
    fun `should select the unified account when the selected folder is not a pinned folder`() = runTest {
        val testSubject = createPinnedTestSubject(UnifiedFoldersLayout.Pinned)

        advanceUntilIdle()

        testSubject.event(Event.SelectAccount(UnifiedAccountId))

        advanceUntilIdle()

        assertThat(testSubject.state.value.selectedAccountId).isEqualTo(UnifiedAccountId)
        assertThat(testSubject.state.value.pinnedFolders).isEmpty()
    }

    @Test
    fun `should select the unified account when the unified account is clicked and a pinned folder is selected`() =
        runTest {
            val testSubject = createPinnedTestSubject(UnifiedFoldersLayout.Pinned)

            advanceUntilIdle()

            testSubject.event(Event.SelectFolder(UnifiedDisplayFolderType.SENT.id))
            testSubject.event(Event.OnAccountClick(createUnifiedDisplayAccount()))

            advanceUntilIdle()

            assertThat(testSubject.state.value.selectedAccountId).isEqualTo(UnifiedAccountId)

            testSubject.event(Event.SelectAccount(UnifiedAccountId))
            testSubject.event(Event.SelectFolder(UnifiedDisplayFolderType.INBOX.id))

            advanceUntilIdle()

            assertThat(testSubject.state.value.selectedAccountId).isEqualTo(UnifiedAccountId)
            assertThat(testSubject.state.value.selectedFolderId).isEqualTo(UnifiedDisplayFolderType.INBOX.id)
        }

    @Test
    fun `should show the unified folders when the unified account is clicked`() = runTest {
        val testSubject = createPinnedTestSubject(
            unifiedFoldersLayout = UnifiedFoldersLayout.Pinned,
            initialState = State(showAccountSelection = true),
        )

        advanceUntilIdle()

        testSubject.event(Event.OnAccountClick(createUnifiedDisplayAccount()))

        advanceUntilIdle()

        assertThat(testSubject.state.value.selectedAccountId).isEqualTo(UnifiedAccountId)
        assertThat(testSubject.state.value.showAccountSelection).isEqualTo(false)
    }

    @Test
    fun `should select the pinned unified folder when SelectFolder event is received`() = runTest {
        val trashFolder = createDisplayUnifiedFolder(
            id = UnifiedDisplayFolderType.TRASH.id,
            unifiedType = UnifiedDisplayFolderType.TRASH,
        )
        val testSubject = createPinnedTestSubject(
            unifiedFoldersLayout = UnifiedFoldersLayout.Pinned,
            unifiedFolders = listOf(trashFolder),
        )

        advanceUntilIdle()

        testSubject.event(Event.SelectFolder(UnifiedDisplayFolderType.TRASH.id))

        advanceUntilIdle()

        assertThat(testSubject.state.value.selectedFolder).isEqualTo(trashFolder)
    }

    private fun createUnifiedDisplayAccount(
        unreadCount: Int = 0,
        starredCount: Int = 0,
    ): UnifiedDisplayAccount {
        return UnifiedDisplayAccount(
            unreadMessageCount = unreadCount,
            starredMessageCount = starredCount,
            hasError = false,
        )
    }

    private fun createPinnedTestSubject(
        unifiedFoldersLayout: UnifiedFoldersLayout,
        unifiedFolders: List<UnifiedDisplayFolder> = listOf(
            createDisplayUnifiedFolder(),
            createDisplayUnifiedFolder(
                id = UnifiedDisplayFolderType.SENT.id,
                unifiedType = UnifiedDisplayFolderType.SENT,
            ),
        ),
        initialState: State = State(),
    ): DrawerViewModel {
        val displayAccounts = createDisplayAccountList(1)
        val displayFoldersMap = mapOf(
            displayAccounts[0].id to createDisplayFolderList(2),
            UnifiedAccountId to unifiedFolders,
        )

        return createTestSubject(
            initialState = initialState,
            drawerConfigFlow = MutableStateFlow(
                createDrawerConfig(
                    showUnifiedInbox = true,
                    unifiedFoldersLayout = unifiedFoldersLayout,
                ),
            ),
            displayAccountsFlow = MutableStateFlow(displayAccounts),
            displayFoldersFlow = MutableStateFlow(displayFoldersMap),
            displayTreeFolder = ::createDisplayTreeFolder,
        )
    }
}
