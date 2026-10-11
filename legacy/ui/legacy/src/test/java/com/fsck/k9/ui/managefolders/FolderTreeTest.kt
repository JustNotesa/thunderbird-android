package com.fsck.k9.ui.managefolders

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import net.thunderbird.feature.mail.folder.FolderType
import net.thunderbird.feature.mail.folder.api.Folder
import org.junit.Test

class FolderTreeTest {
    private val inbox = createFolder(1, "INBOX")
    private val projects = createFolder(2, "Projects")
    private val alpha = createFolder(3, "Projects.Alpha")
    private val alphaDocuments = createFolder(4, "Projects.Alpha.Documents")
    private val beta = createFolder(5, "Projects.Beta")
    private val orphan = createFolder(6, "Missing.Child")
    private val folders = listOf(inbox, projects, alpha, alphaDocuments, beta, orphan)

    @Test
    fun `subfolders of collapsed folders should not be displayed`() {
        val result = FolderTree.build(folders, pathDelimiter = ".", expandedFolderIds = emptySet())

        assertThat(result.map { it.folder }).containsExactly(inbox, projects, orphan)
        assertThat(result[1].hasSubfolders).isTrue()
        assertThat(result[1].isExpanded).isFalse()
        assertThat(result[0].hasSubfolders).isFalse()
    }

    @Test
    fun `subfolders of expanded folders should follow their folder`() {
        val result = FolderTree.build(folders, pathDelimiter = ".", expandedFolderIds = setOf(projects.id))

        assertThat(result.map { it.folder }).containsExactly(inbox, projects, alpha, beta, orphan)
        assertThat(result.map { it.depth }).containsExactly(0, 0, 1, 1, 0)
        assertThat(result[2].parent).isEqualTo(projects)
    }

    @Test
    fun `nested subfolders should be displayed one level deeper`() {
        val result = FolderTree.build(folders, pathDelimiter = ".", expandedFolderIds = setOf(projects.id, alpha.id))

        assertThat(result.map { it.folder }).containsExactly(inbox, projects, alpha, alphaDocuments, beta, orphan)
        assertThat(result[3].depth).isEqualTo(2)
    }

    @Test
    fun `folders should not be nested without path delimiter`() {
        val result = FolderTree.build(folders, pathDelimiter = "", expandedFolderIds = emptySet())

        assertThat(result.map { it.folder }).isEqualTo(folders)
    }

    private fun createFolder(id: Long, name: String): Folder {
        return Folder(id = id, name = name, type = FolderType.REGULAR, isLocalOnly = false)
    }
}
