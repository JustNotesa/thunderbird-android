package com.fsck.k9.ui.messageview

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import net.thunderbird.feature.account.AccountIdFactory
import org.junit.Test

class LastSelectedFolderStoreTest {
    private val testSubject = LastSelectedFolderStore()

    @Test
    fun `get should return null when no folder was chosen`() {
        assertThat(testSubject.get(AccountIdFactory.create())).isNull()
    }

    @Test
    fun `get should return the folder last chosen for each account`() {
        // Arrange
        val accountId = AccountIdFactory.create()
        val otherAccountId = AccountIdFactory.create()

        // Act
        testSubject.set(accountId, FIRST_FOLDER_ID)
        testSubject.set(accountId, SECOND_FOLDER_ID)
        testSubject.set(otherAccountId, FIRST_FOLDER_ID)

        // Assert
        assertThat(testSubject.get(accountId)).isEqualTo(SECOND_FOLDER_ID)
        assertThat(testSubject.get(otherAccountId)).isEqualTo(FIRST_FOLDER_ID)
    }

    @Test
    fun `getRecent should return the folders chosen with the latest first and each only once`() {
        // Arrange
        val accountId = AccountIdFactory.create()

        // Act
        testSubject.set(accountId, FIRST_FOLDER_ID)
        testSubject.set(accountId, SECOND_FOLDER_ID)
        testSubject.set(accountId, FIRST_FOLDER_ID)

        // Assert
        assertThat(testSubject.getRecent(accountId)).containsExactly(FIRST_FOLDER_ID, SECOND_FOLDER_ID)
    }

    @Test
    fun `getRecent should only keep the latest folders`() {
        // Arrange
        val accountId = AccountIdFactory.create()

        // Act
        (1L..7L).forEach { testSubject.set(accountId, it) }

        // Assert
        assertThat(testSubject.getRecent(accountId)).containsExactly(7L, 6L, 5L, 4L, 3L)
    }

    @Test
    fun `getRecent should be empty when no folder was chosen`() {
        assertThat(testSubject.getRecent(AccountIdFactory.create())).isEmpty()
    }

    private companion object {
        const val FIRST_FOLDER_ID = 1L
        const val SECOND_FOLDER_ID = 2L
    }
}
