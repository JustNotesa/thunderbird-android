package com.fsck.k9.ui.settings.search

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsSearchIndexCacheTest {
    private var loadCount = 0
    private val indexes = ArrayDeque<CompletableDeferred<SettingsSearchIndex>>()
    private val testSubject = SettingsSearchIndexCache {
        loadCount++
        indexes.removeFirst().await()
    }

    @Test
    fun `reads the index only once`() = runTest {
        val index = SettingsSearchIndex.EMPTY
        indexes.add(CompletableDeferred(index))

        val first = testSubject.get()
        val second = testSubject.get()

        assertThat(first).isSameInstanceAs(index)
        assertThat(second).isSameInstanceAs(index)
        assertThat(loadCount).isEqualTo(1)
        assertThat(testSubject.peek()).isSameInstanceAs(index)
    }

    @Test
    fun `reads the index again after it was invalidated`() = runTest {
        indexes.add(CompletableDeferred(SettingsSearchIndex.EMPTY))
        indexes.add(CompletableDeferred(SettingsSearchIndex.EMPTY))
        testSubject.get()

        testSubject.invalidate()
        testSubject.get()

        assertThat(loadCount).isEqualTo(2)
    }

    @Test
    fun `has no index before the first read`() {
        assertThat(testSubject.peek()).isNull()
    }

    @Test
    fun `does not keep an index that was invalidated while it was being read`() = runTest {
        val slow = CompletableDeferred<SettingsSearchIndex>()
        indexes.add(slow)
        val job = launch { testSubject.get() }
        runCurrent()

        testSubject.invalidate()
        slow.complete(SettingsSearchIndex.EMPTY)
        job.join()

        assertThat(testSubject.peek()).isNull()
    }
}
