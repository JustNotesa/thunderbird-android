package com.fsck.k9.storage.messages

import android.database.sqlite.SQLiteDatabase
import app.k9mail.legacy.mailstore.CreateFolderInfo
import app.k9mail.legacy.mailstore.FolderSettings
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import com.fsck.k9.mail.FolderType
import com.fsck.k9.storage.RobolectricTest
import net.thunderbird.feature.account.AccountIdFactory
import org.junit.After
import org.junit.Before
import org.junit.Test

class CreateFolderOperationsTest : RobolectricTest() {

    private val accountId = AccountIdFactory.create()
    private lateinit var sqliteDatabase: SQLiteDatabase
    private lateinit var createFolderOperations: CreateFolderOperations

    @Before
    fun setUp() {
        sqliteDatabase = createDatabase()
        val lockableDatabase = createLockableDatabaseMock(sqliteDatabase)
        createFolderOperations = CreateFolderOperations(lockableDatabase, accountId)
    }

    @After
    fun tearDown() {
        sqliteDatabase.close()
    }

    @Test
    fun `create single folder`() {
        createFolderOperations.createFolders(
            listOf(
                CreateFolderInfo(
                    serverId = "archived_messages",
                    name = "Archive",
                    type = FolderType.ARCHIVE,
                    settings = FolderSettings(
                        visibleLimit = 10,
                        isVisible = true,
                        isSyncEnabled = false,
                        isNotificationsEnabled = true,
                        isPushEnabled = false,
                        inTopGroup = true,
                        integrate = false,
                    ),
                ),
            ),
        )

        val folders = sqliteDatabase.readFolders()
        assertThat(folders).hasSize(1)
        val folder = folders.first()
        assertThat(folder.serverId).isEqualTo("archived_messages")
        assertThat(folder.name).isEqualTo("Archive")
        assertThat(folder.type).isEqualTo("archive")
        assertThat(folder.visibleLimit).isEqualTo(10)
        assertThat(folder.visible).isEqualTo(1)
        assertThat(folder.syncEnabled).isEqualTo(0)
        assertThat(folder.notificationsEnabled).isEqualTo(1)
        assertThat(folder.pushEnabled).isEqualTo(0)
        assertThat(folder.inTopGroup).isEqualTo(1)
        assertThat(folder.integrate).isEqualTo(0)
    }

    @Test
    fun `create multiple folders`() {
        createFolderOperations.createFolders(
            listOf(
                createCreateFolderInfo(serverId = "folder1", name = "Inbox"),
                createCreateFolderInfo(serverId = "folder2", name = "Sent"),
                createCreateFolderInfo(serverId = "folder3", name = "Trash"),
            ),
        )

        val folders = sqliteDatabase.readFolders()
        assertThat(folders).hasSize(3)
        assertThat(folders.map { it.serverId to it.name }.toSet()).isEqualTo(
            setOf(
                "folder1" to "Inbox",
                "folder2" to "Sent",
                "folder3" to "Trash",
            ),
        )
    }

    @Test
    fun `create folder with existing server id does not add a second row`() {
        createFolderOperations.createFolders(listOf(createCreateFolderInfo(serverId = "folder1", name = "Inbox")))

        createFolderOperations.createFolders(listOf(createCreateFolderInfo(serverId = "folder1", name = "Inbox")))

        val folders = sqliteDatabase.readFolders()
        assertThat(folders.filter { it.serverId == "folder1" }).hasSize(1)
        assertThat(folders).hasSize(1)
    }

    @Test
    fun `create folder with existing server id returns id of existing folder`() {
        val firstIds = createFolderOperations.createFolders(
            listOf(createCreateFolderInfo(serverId = "folder1", name = "Inbox")),
        )

        val secondIds = createFolderOperations.createFolders(
            listOf(createCreateFolderInfo(serverId = "folder1", name = "Inbox")),
        )

        assertThat(firstIds).hasSize(1)
        assertThat(secondIds).isEqualTo(firstIds)
    }

    @Test
    fun `create new folders next to an existing one only adds the new ones`() {
        createFolderOperations.createFolders(listOf(createCreateFolderInfo(serverId = "folder1", name = "Inbox")))

        val ids = createFolderOperations.createFolders(
            listOf(
                createCreateFolderInfo(serverId = "folder1", name = "Inbox"),
                createCreateFolderInfo(serverId = "folder2", name = "Sent"),
            ),
        )

        assertThat(ids).hasSize(2)
        assertThat(sqliteDatabase.readFolders().mapNotNull { it.serverId }.sorted())
            .isEqualTo(listOf("folder1", "folder2"))
    }

    fun createCreateFolderInfo(serverId: String, name: String): CreateFolderInfo {
        return CreateFolderInfo(
            serverId = serverId,
            name = name,
            type = FolderType.REGULAR,
            settings = FolderSettings(
                visibleLimit = 25,
                isVisible = true,
                isSyncEnabled = false,
                isNotificationsEnabled = false,
                isPushEnabled = false,
                inTopGroup = false,
                integrate = false,
            ),
        )
    }
}
