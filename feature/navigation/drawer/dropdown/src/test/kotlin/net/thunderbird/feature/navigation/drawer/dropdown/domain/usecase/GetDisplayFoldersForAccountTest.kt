package net.thunderbird.feature.navigation.drawer.dropdown.domain.usecase

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import net.thunderbird.account.fake.FakeAccountData.ACCOUNT_ID
import net.thunderbird.core.preference.display.inboxSettings.UnifiedFoldersLayout
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.UnifiedAccountId
import net.thunderbird.feature.mail.folder.FolderType
import net.thunderbird.feature.navigation.drawer.dropdown.FakeLegacyAccountManager
import net.thunderbird.feature.navigation.drawer.dropdown.createLegacyAccount
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.DisplayFolder
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.MailDisplayFolder
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.UnifiedAccountFolder
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.UnifiedDisplayFolder
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.UnifiedDisplayFolderType
import net.thunderbird.feature.navigation.drawer.dropdown.ui.FakeData
import app.k9mail.legacy.ui.folder.DisplayFolder as LegacyDisplayFolder

internal class GetDisplayFoldersForAccountTest {

    @Test
    fun `should return account folders when account id is regular`() = runTest {
        val accountId = ACCOUNT_ID
        val legacyDisplayFolderFlow = MutableStateFlow(LEGACY_DISPLAY_FOLDERS)
        val displayFolderRepository = FakeDisplayFolderRepository(legacyDisplayFolderFlow)
        val unifiedFolderFlow = MutableStateFlow(DISPLAY_UNIFIED_FOLDER)
        val unifiedFolderRepository = FakeUnifiedFolderRepository(unifiedFolderFlow)
        val testSubject = GetDisplayFoldersForAccount(
            displayFolderRepository = displayFolderRepository,
            unifiedFolderRepository = unifiedFolderRepository,
            accountManager = FakeLegacyAccountManager(),
        )

        val result = testSubject(accountId, UnifiedFoldersLayout.Flat).first()

        assertThat(result).isEqualTo(DISPLAY_ACCOUNT_FOLDERS)
    }

    @Test
    fun `should return unifed account folders when account id is unified`() = runTest {
        val accountId = UnifiedAccountId
        val legacyDisplayFolderFlow = MutableStateFlow(LEGACY_DISPLAY_FOLDERS)
        val displayFolderRepository = FakeDisplayFolderRepository(legacyDisplayFolderFlow)
        val unifiedFolderFlow = MutableStateFlow(DISPLAY_UNIFIED_FOLDER)
        val unifiedFolderRepository = FakeUnifiedFolderRepository(unifiedFolderFlow)
        val testSubject = GetDisplayFoldersForAccount(
            displayFolderRepository = displayFolderRepository,
            unifiedFolderRepository = unifiedFolderRepository,
            accountManager = FakeLegacyAccountManager(),
        )

        val result = testSubject(accountId, UnifiedFoldersLayout.Flat).first()

        assertThat(result).isEqualTo(DISPLAY_UNIFIED_FOLDERS)
    }

    @Test
    fun `should only emit new list when account folders emit new items`() = runTest {
        val accountId = ACCOUNT_ID
        val legacyDisplayFolderFlow = MutableStateFlow(LEGACY_DISPLAY_FOLDERS)
        val displayFolderRepository = FakeDisplayFolderRepository(legacyDisplayFolderFlow)
        val unifiedFolderFlow = MutableStateFlow(DISPLAY_UNIFIED_FOLDER)
        val unifiedFolderRepository = FakeUnifiedFolderRepository(unifiedFolderFlow)
        val testSubject = GetDisplayFoldersForAccount(
            displayFolderRepository = displayFolderRepository,
            unifiedFolderRepository = unifiedFolderRepository,
            accountManager = FakeLegacyAccountManager(),
        )

        testSubject(accountId, UnifiedFoldersLayout.Flat).test {
            assertThat(awaitItem()).isEqualTo(DISPLAY_ACCOUNT_FOLDERS)

            legacyDisplayFolderFlow.emit(LEGACY_DISPLAY_FOLDERS_2)

            assertThat(awaitItem()).isEqualTo(DISPLAY_ACCOUNT_FOLDERS_2)

            unifiedFolderFlow.emit(DISPLAY_UNIFIED_FOLDER_2)
        }
    }

    @Test
    fun `should only emit new list when unified account folders emit new items`() = runTest {
        val accountId = UnifiedAccountId
        val legacyDisplayFolderFlow = MutableStateFlow(LEGACY_DISPLAY_FOLDERS)
        val displayFolderRepository = FakeDisplayFolderRepository(legacyDisplayFolderFlow)
        val unifiedFolderFlow = MutableStateFlow(DISPLAY_UNIFIED_FOLDER)
        val unifiedFolderRepository = FakeUnifiedFolderRepository(unifiedFolderFlow)
        val testSubject = GetDisplayFoldersForAccount(
            displayFolderRepository = displayFolderRepository,
            unifiedFolderRepository = unifiedFolderRepository,
            accountManager = FakeLegacyAccountManager(),
        )

        testSubject(accountId, UnifiedFoldersLayout.Flat).test {
            assertThat(awaitItem()).isEqualTo(DISPLAY_UNIFIED_FOLDERS)

            legacyDisplayFolderFlow.emit(LEGACY_DISPLAY_FOLDERS_2)
            unifiedFolderFlow.emit(DISPLAY_UNIFIED_FOLDER_2)

            assertThat(awaitItem()).isEqualTo(listOf(DISPLAY_UNIFIED_FOLDER_2))
        }
    }

    @Test
    fun `should return all available unified folders in order when layout is flat`() = runTest {
        // arrange
        val displayFolderRepository = FakeDisplayFolderRepository(MutableStateFlow(LEGACY_DISPLAY_FOLDERS))
        val unifiedFolderRepository = FakeUnifiedFolderRepository(
            displayUnifiedFolderFlow = MutableStateFlow(DISPLAY_UNIFIED_FOLDER),
            unifiedFolderTypes = flowOf(
                listOf(
                    UnifiedDisplayFolderType.INBOX,
                    UnifiedDisplayFolderType.DRAFTS,
                    UnifiedDisplayFolderType.TRASH,
                ),
            ),
            displayUnifiedFolderFlows = mapOf(
                UnifiedDisplayFolderType.INBOX to MutableStateFlow(DISPLAY_UNIFIED_FOLDER),
                UnifiedDisplayFolderType.DRAFTS to MutableStateFlow(DISPLAY_UNIFIED_DRAFTS_FOLDER),
                UnifiedDisplayFolderType.TRASH to MutableStateFlow(DISPLAY_UNIFIED_TRASH_FOLDER),
            ),
        )
        val testSubject = GetDisplayFoldersForAccount(
            displayFolderRepository = displayFolderRepository,
            unifiedFolderRepository = unifiedFolderRepository,
            accountManager = FakeLegacyAccountManager(),
        )

        // act
        val result = testSubject(UnifiedAccountId, UnifiedFoldersLayout.Flat).first()

        // assert
        assertThat(result).isEqualTo(
            listOf(
                DISPLAY_UNIFIED_FOLDER,
                DISPLAY_UNIFIED_DRAFTS_FOLDER,
                DISPLAY_UNIFIED_TRASH_FOLDER,
            ),
        )
    }

    @Test
    fun `should emit empty list when no unified folder type is available`() = runTest {
        // arrange
        val displayFolderRepository = FakeDisplayFolderRepository(MutableStateFlow(LEGACY_DISPLAY_FOLDERS))
        val unifiedFolderRepository = FakeUnifiedFolderRepository(
            displayUnifiedFolderFlow = MutableStateFlow(DISPLAY_UNIFIED_FOLDER),
            unifiedFolderTypes = flowOf(emptyList()),
        )
        val testSubject = GetDisplayFoldersForAccount(
            displayFolderRepository = displayFolderRepository,
            unifiedFolderRepository = unifiedFolderRepository,
            accountManager = FakeLegacyAccountManager(),
        )

        // act
        val result = testSubject(UnifiedAccountId, UnifiedFoldersLayout.Flat).first()

        // assert
        assertThat(result).isEmpty()
    }

    @Test
    fun `should add matching folder of every account as child when layout is tree`() = runTest {
        // arrange
        val accountA = createLegacyAccount(name = ACCOUNT_NAME_A)
        val accountB = createLegacyAccount(name = ACCOUNT_NAME_B)
        val displayFolderRepository = FakeDisplayFolderRepository(
            foldersFlow = MutableStateFlow(emptyList<LegacyDisplayFolder>()),
            foldersByAccount = mapOf(
                accountA.id to flowOf(LEGACY_DISPLAY_FOLDERS_A),
                accountB.id to flowOf(LEGACY_DISPLAY_FOLDERS_B),
            ),
        )
        val unifiedFolderRepository = FakeUnifiedFolderRepository(
            displayUnifiedFolderFlow = MutableStateFlow(DISPLAY_UNIFIED_FOLDER),
            unifiedFolderTypes = flowOf(listOf(UnifiedDisplayFolderType.INBOX, UnifiedDisplayFolderType.DRAFTS)),
            displayUnifiedFolderFlows = mapOf(
                UnifiedDisplayFolderType.INBOX to MutableStateFlow(DISPLAY_UNIFIED_FOLDER),
                UnifiedDisplayFolderType.DRAFTS to MutableStateFlow(DISPLAY_UNIFIED_DRAFTS_FOLDER),
            ),
        )
        val testSubject = GetDisplayFoldersForAccount(
            displayFolderRepository = displayFolderRepository,
            unifiedFolderRepository = unifiedFolderRepository,
            accountManager = FakeLegacyAccountManager(listOf(accountA, accountB)),
        )

        // act
        val result = testSubject(UnifiedAccountId, UnifiedFoldersLayout.Tree).first()

        // assert
        val unifiedInboxFolder = DISPLAY_UNIFIED_FOLDER.copy(
            children = listOf(
                UnifiedAccountFolder(
                    accountName = ACCOUNT_NAME_A,
                    folder = toMailDisplayFolder(accountA.id, LEGACY_INBOX_FOLDER_A),
                ),
                UnifiedAccountFolder(
                    accountName = ACCOUNT_NAME_B,
                    folder = toMailDisplayFolder(accountB.id, LEGACY_INBOX_FOLDER_B),
                ),
            ).toImmutableList(),
        )
        val unifiedDraftsFolder = DISPLAY_UNIFIED_DRAFTS_FOLDER.copy(
            children = listOf(
                UnifiedAccountFolder(
                    accountName = ACCOUNT_NAME_A,
                    folder = toMailDisplayFolder(accountA.id, LEGACY_DRAFTS_FOLDER_A),
                ),
            ).toImmutableList(),
        )
        assertThat(result).isEqualTo(listOf(unifiedInboxFolder, unifiedDraftsFolder))
    }

    private fun toMailDisplayFolder(accountId: AccountId, folder: LegacyDisplayFolder): MailDisplayFolder {
        return MailDisplayFolder(
            accountId = accountId,
            folder = folder.folder,
            isInTopGroup = folder.isInTopGroup,
            unreadMessageCount = folder.unreadMessageCount,
            starredMessageCount = folder.starredMessageCount,
            pathDelimiter = folder.pathDelimiter,
        )
    }

    private companion object {
        const val ACCOUNT_NAME_A = "Account A"
        const val ACCOUNT_NAME_B = "Account B"

        val LEGACY_DISPLAY_FOLDERS = listOf(
            LegacyDisplayFolder(
                folder = FakeData.FOLDER,
                isInTopGroup = false,
                unreadMessageCount = 0,
                starredMessageCount = 0,
                pathDelimiter = "/",
            ),
            LegacyDisplayFolder(
                folder = FakeData.FOLDER.copy(
                    id = 2,
                    name = "Folder 2",
                ),
                isInTopGroup = false,
                unreadMessageCount = 1,
                starredMessageCount = 0,
                pathDelimiter = "/",
            ),
        )

        val LEGACY_DISPLAY_FOLDERS_2 = LEGACY_DISPLAY_FOLDERS + LegacyDisplayFolder(
            folder = FakeData.FOLDER.copy(
                id = 3,
                name = "Folder 3",
            ),
            isInTopGroup = false,
            unreadMessageCount = 0,
            starredMessageCount = 0,
            pathDelimiter = "/",
        )

        val LEGACY_INBOX_FOLDER_A = LegacyDisplayFolder(
            folder = FakeData.FOLDER.copy(id = 11, name = "Inbox", type = FolderType.INBOX),
            isInTopGroup = true,
            unreadMessageCount = 4,
            starredMessageCount = 1,
            pathDelimiter = "/",
        )

        val LEGACY_DRAFTS_FOLDER_A = LegacyDisplayFolder(
            folder = FakeData.FOLDER.copy(id = 12, name = "Drafts", type = FolderType.DRAFTS),
            isInTopGroup = true,
            unreadMessageCount = 2,
            starredMessageCount = 0,
            pathDelimiter = "/",
        )

        val LEGACY_INBOX_FOLDER_B = LegacyDisplayFolder(
            folder = FakeData.FOLDER.copy(id = 21, name = "Inbox", type = FolderType.INBOX),
            isInTopGroup = true,
            unreadMessageCount = 5,
            starredMessageCount = 2,
            pathDelimiter = "/",
        )

        val LEGACY_DISPLAY_FOLDERS_A = listOf(LEGACY_INBOX_FOLDER_A, LEGACY_DRAFTS_FOLDER_A)

        val LEGACY_DISPLAY_FOLDERS_B = listOf(LEGACY_INBOX_FOLDER_B)

        val DISPLAY_UNIFIED_FOLDER = UnifiedDisplayFolder(
            folderId = "unified_inbox",
            unifiedType = UnifiedDisplayFolderType.INBOX,
            unreadMessageCount = 2,
            starredMessageCount = 2,
        )

        val DISPLAY_UNIFIED_FOLDER_2 = UnifiedDisplayFolder(
            folderId = "unified_inbox",
            unifiedType = UnifiedDisplayFolderType.INBOX,
            unreadMessageCount = 3,
            starredMessageCount = 3,
        )

        val DISPLAY_UNIFIED_DRAFTS_FOLDER = UnifiedDisplayFolder(
            folderId = UnifiedDisplayFolderType.DRAFTS.id,
            unifiedType = UnifiedDisplayFolderType.DRAFTS,
            unreadMessageCount = 6,
            starredMessageCount = 1,
        )

        val DISPLAY_UNIFIED_TRASH_FOLDER = UnifiedDisplayFolder(
            folderId = UnifiedDisplayFolderType.TRASH.id,
            unifiedType = UnifiedDisplayFolderType.TRASH,
            unreadMessageCount = 7,
            starredMessageCount = 3,
        )

        val DISPLAY_UNIFIED_FOLDERS = listOf(DISPLAY_UNIFIED_FOLDER)

        val DISPLAY_ACCOUNT_FOLDERS = listOf<DisplayFolder>(
            MailDisplayFolder(
                accountId = ACCOUNT_ID,
                folder = FakeData.FOLDER,
                isInTopGroup = false,
                unreadMessageCount = 0,
                starredMessageCount = 0,
                pathDelimiter = "/",
            ),
            MailDisplayFolder(
                accountId = ACCOUNT_ID,
                folder = FakeData.FOLDER.copy(
                    id = 2,
                    name = "Folder 2",
                ),
                isInTopGroup = false,
                unreadMessageCount = 1,
                starredMessageCount = 0,
                pathDelimiter = "/",
            ),
        )

        val DISPLAY_ACCOUNT_FOLDERS_2 = DISPLAY_ACCOUNT_FOLDERS + MailDisplayFolder(
            accountId = ACCOUNT_ID,
            folder = FakeData.FOLDER.copy(
                id = 3,
                name = "Folder 3",
            ),
            isInTopGroup = false,
            unreadMessageCount = 0,
            starredMessageCount = 0,
            pathDelimiter = "/",
        )
    }
}
