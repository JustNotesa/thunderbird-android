package net.thunderbird.feature.navigation.drawer.dropdown.data

import app.k9mail.legacy.message.controller.MessageCounts
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.account.UnifiedAccountId
import net.thunderbird.feature.mail.folder.FolderType
import net.thunderbird.feature.navigation.drawer.dropdown.FakeLegacyAccountManager
import net.thunderbird.feature.navigation.drawer.dropdown.createLegacyAccount
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.UnifiedDisplayFolder
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.UnifiedDisplayFolderType
import net.thunderbird.feature.search.legacy.LocalMessageSearchType
import net.thunderbird.feature.search.legacy.UnifiedFolderSelection
import net.thunderbird.feature.search.legacy.api.MessageSearchField
import net.thunderbird.feature.search.legacy.api.SearchAttribute

internal class UnifiedFolderRepositoryTest {

    @Test
    fun `should return DisplayUnifiedFolder for unified inbox`() = runTest {
        // arrange
        val messageCountsProvider = FakeMessageCountsProvider(
            messageCounts = MessageCounts(
                unread = 2,
                starred = 2,
            ),
        )
        val testSubject = UnifiedFolderRepository(
            messageCountsProvider = messageCountsProvider,
            accountManager = FakeLegacyAccountManager(),
        )
        val folderType = UnifiedDisplayFolderType.INBOX

        // act
        val result = testSubject.getUnifiedDisplayFolderFlow(folderType).first()

        // assert
        assertThat(result).isEqualTo(
            UnifiedDisplayFolder(
                folderId = "unified_inbox",
                unifiedType = folderType,
                unreadMessageCount = 2,
                starredMessageCount = 2,
            ),
        )

        val search = messageCountsProvider.recordedSearch
        assertThat(search.id).isEqualTo(UnifiedAccountId)
        assertThat(search.type).isEqualTo(
            LocalMessageSearchType.Unified(UnifiedFolderSelection.Special(FolderType.INBOX)),
        )
        val condition = search.conditions.condition
        assertThat(condition?.value).isEqualTo("1")
        assertThat(condition?.attribute).isEqualTo(SearchAttribute.EQUALS)
        assertThat(condition?.field).isEqualTo(MessageSearchField.INTEGRATE)
    }

    @Test
    fun `should return id and unified type of every folder type`() = runTest {
        // arrange
        val messageCountsProvider = FakeMessageCountsProvider(
            messageCounts = MessageCounts(
                unread = 2,
                starred = 2,
            ),
        )
        val testSubject = UnifiedFolderRepository(
            messageCountsProvider = messageCountsProvider,
            accountManager = FakeLegacyAccountManager(),
        )

        // act + assert
        UnifiedDisplayFolderType.entries.forEach { folderType ->
            val result = testSubject.getUnifiedDisplayFolderFlow(folderType).first()

            assertThat(result).isEqualTo(
                UnifiedDisplayFolder(
                    folderId = folderType.id,
                    unifiedType = folderType,
                    unreadMessageCount = 2,
                    starredMessageCount = 2,
                ),
            )
            assertThat(messageCountsProvider.recordedSearch.id).isEqualTo(UnifiedAccountId)
            assertThat(messageCountsProvider.recordedSearch.type).isEqualTo(
                LocalMessageSearchType.Unified(UnifiedFolderSelection.Special(folderType.folderType)),
            )
        }
    }

    @Test
    fun `should return unified inbox when no account is present`() = runTest {
        // arrange
        val testSubject = createTestSubject(FakeLegacyAccountManager())

        // act
        val result = testSubject.getUnifiedFolderTypesFlow().first()

        // assert
        assertThat(result).isEqualTo(listOf(UnifiedDisplayFolderType.INBOX))
    }

    @Test
    fun `should return folder types of which an account has the special folder`() = runTest {
        // arrange
        val account = createLegacyAccount(
            inboxFolderId = 1,
            draftsFolderId = 2,
            trashFolderId = 3,
        )
        val accountWithoutSpecialFolders = createLegacyAccount()
        val testSubject = createTestSubject(FakeLegacyAccountManager(listOf(account, accountWithoutSpecialFolders)))

        // act
        val result = testSubject.getUnifiedFolderTypesFlow().first()

        // assert
        assertThat(result).isEqualTo(
            listOf(
                UnifiedDisplayFolderType.INBOX,
                UnifiedDisplayFolderType.DRAFTS,
                UnifiedDisplayFolderType.TRASH,
            ),
        )
    }

    @Test
    fun `should return folder type of the folder the account manager adds later`() = runTest {
        // arrange
        val accountManager = FakeLegacyAccountManager(listOf(createLegacyAccount()))
        val testSubject = createTestSubject(accountManager = accountManager)

        // act
        accountManager.replace(createLegacyAccount(name = "with spam", spamFolderId = 4))

        // assert
        assertThat(testSubject.getUnifiedFolderTypesFlow().first()).isEqualTo(
            listOf(UnifiedDisplayFolderType.INBOX, UnifiedDisplayFolderType.SPAM),
        )
    }

    private fun createTestSubject(accountManager: FakeLegacyAccountManager): UnifiedFolderRepository {
        return UnifiedFolderRepository(
            messageCountsProvider = FakeMessageCountsProvider(messageCounts = MessageCounts(unread = 0, starred = 0)),
            accountManager = accountManager,
        )
    }
}
