package com.fsck.k9.controller

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import org.junit.Test

class FolderSearchQueueTest {
    @Test
    fun `should return all folders in order when there is only one worker`() {
        // Arrange
        val folders = listOf("Inbox", "Archive", "Trash")
        val testSubject = FolderSearchQueue(folders, workerCount = 1)

        // Act
        val handedOutFolders = generateSequence { testSubject.next(0) }.toList()

        // Assert
        assertThat(handedOutFolders).containsExactly("Inbox", "Archive", "Trash")
    }

    @Test
    fun `should give every worker the beginning of its own part`() {
        // Arrange
        val folders = (0 until 10).toList()
        val testSubject = FolderSearchQueue(folders, workerCount = 3)

        // Act
        val firstFolderOfWorker0 = testSubject.next(0)
        val firstFolderOfWorker1 = testSubject.next(1)
        val firstFolderOfWorker2 = testSubject.next(2)

        // Assert
        assertThat(firstFolderOfWorker0).isEqualTo(0)
        assertThat(firstFolderOfWorker1).isEqualTo(4)
        assertThat(firstFolderOfWorker2).isEqualTo(7)
    }

    @Test
    fun `should give the start folder to the first worker`() {
        // Arrange
        val folders = listOf("Inbox", "Archive", "Trash")
        val testSubject = FolderSearchQueue(folders, workerCount = 2)

        // Act
        val folder = testSubject.next(0)

        // Assert
        assertThat(folder).isEqualTo("Inbox")
    }

    @Test
    fun `should take the last folder of the part with the most folders left`() {
        // Arrange
        val folders = listOf("a", "b", "c", "d", "e", "f")
        val testSubject = FolderSearchQueue(folders, workerCount = 3)
        testSubject.next(0)
        testSubject.next(0)

        // Act
        val folder = testSubject.next(0)

        // Assert
        assertThat(folder).isEqualTo("d")
    }

    @Test
    fun `should hand out a put back folder before the folders of the worker's own part`() {
        // Arrange
        val folders = (0 until 6).toList()
        val testSubject = FolderSearchQueue(folders, workerCount = 2)
        val folder = testSubject.next(0)
        assertThat(folder).isEqualTo(0)
        testSubject.putBack(0)

        // Act
        val nextFolder = testSubject.next(1)

        // Assert
        assertThat(nextFolder).isEqualTo(0)
    }

    @Test
    fun `should hand out every folder exactly once when the workers take turns`() {
        // Arrange
        val folders = (0 until 10).toList()
        val testSubject = FolderSearchQueue(folders, workerCount = 3)

        // Act
        val handedOutFolders = mutableListOf<Int>()
        var worker = 0
        while (true) {
            val folder = testSubject.next(worker)
            if (folder == null) {
                break
            }
            handedOutFolders.add(folder)
            worker = (worker + 1) % 3
        }

        // Assert
        assertThat(handedOutFolders).containsExactlyInAnyOrder(*folders.toTypedArray())
        assertThat(handedOutFolders).hasSize(folders.size)
    }

    @Test
    fun `should clamp the worker count to the number of folders`() {
        // Arrange
        val folders = listOf("Inbox", "Archive")

        // Act
        val testSubject = FolderSearchQueue(folders, workerCount = 5)

        // Assert
        assertThat(testSubject.workerCount).isEqualTo(2)
    }

    @Test
    fun `should have a single worker and no folders when there are no folders`() {
        // Arrange
        val folders = emptyList<String>()

        // Act
        val testSubject = FolderSearchQueue(folders, workerCount = 3)

        // Assert
        assertThat(testSubject.workerCount).isEqualTo(1)
        assertThat(testSubject.next(0)).isNull()
    }

    @Test
    fun `should throw an exception when the worker index is out of range`() {
        // Arrange
        val folders = listOf("Inbox")
        val testSubject = FolderSearchQueue(folders, workerCount = 1)

        // Act & Assert
        assertFailure { testSubject.next(1) }.isInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `should count down the number of remaining folders`() {
        // Arrange
        val folders = (0 until 10).toList()
        val testSubject = FolderSearchQueue(folders, workerCount = 3)

        // Act
        testSubject.next(0)
        testSubject.next(1)

        // Assert
        assertThat(testSubject.remaining).isEqualTo(8)
    }
}
