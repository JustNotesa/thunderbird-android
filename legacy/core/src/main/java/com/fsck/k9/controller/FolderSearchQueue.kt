package com.fsck.k9.controller

/**
 * Hands out the folders of one account to several connections that search the server at the same time.
 *
 * The folders are split into [workerCount] parts of the same size (sizes differ by at most one), in the order of
 * [folders]. Each connection starts at the beginning of its own part, so the connections search different areas of
 * the folder list. A connection that is done with its part takes over the last folder of the part with the most
 * folders left. The first folder of [folders] (the folder the search was started from) is always handed out first.
 */
internal class FolderSearchQueue<T>(folders: List<T>, workerCount: Int) {
    val workerCount: Int = workerCount.coerceIn(1, maxOf(1, folders.size))

    private val parts: List<ArrayDeque<T>> = splitIntoParts(folders, this.workerCount)
    private val putBackFolders = ArrayDeque<T>()
    private var remainingFolderCount: Int = folders.size

    /** The next folder [worker] (0 until workerCount) should search, or `null` when all folders were handed out. */
    @Synchronized
    fun next(worker: Int): T? {
        require(worker in 0 until workerCount) { "Invalid worker index: $worker" }

        val folder = putBackFolders.removeFirstOrNull()
            ?: parts[worker].removeFirstOrNull()
            ?: takeLastFolderOfRichestPart()

        if (folder != null) {
            remainingFolderCount--
        }

        return folder
    }

    /**
     * Puts back a folder a connection could not search, e.g. because the server refused another connection. It is
     * handed out again before any other folder.
     */
    @Synchronized
    fun putBack(folder: T) {
        putBackFolders.addLast(folder)
        remainingFolderCount++
    }

    /** The number of folders not handed out yet. */
    @get:Synchronized
    val remaining: Int
        get() = remainingFolderCount

    private fun splitIntoParts(folders: List<T>, partCount: Int): List<ArrayDeque<T>> {
        val partSize = folders.size / partCount
        val longerPartCount = folders.size % partCount
        val parts = mutableListOf<ArrayDeque<T>>()
        var startIndex = 0

        for (partIndex in 0 until partCount) {
            val size = partSize + if (partIndex < longerPartCount) 1 else 0
            parts.add(ArrayDeque(folders.subList(startIndex, startIndex + size)))
            startIndex += size
        }

        return parts
    }

    private fun takeLastFolderOfRichestPart(): T? {
        val richestPart = parts.filter { it.isNotEmpty() }.maxByOrNull { it.size } ?: return null

        return richestPart.removeLast()
    }
}
