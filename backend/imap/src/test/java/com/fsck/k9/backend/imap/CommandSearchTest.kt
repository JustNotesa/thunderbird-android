package com.fsck.k9.backend.imap

import assertk.assertThat
import assertk.assertions.containsExactly
import com.fsck.k9.backend.api.SearchStep
import com.fsck.k9.mail.store.imap.ImapMessage
import com.fsck.k9.mail.store.imap.OpenMode
import net.thunderbird.core.common.mail.Flag
import net.thunderbird.core.common.mail.ServerSearchCriteria
import org.junit.Test

class CommandSearchTest {
    private val events = mutableListOf<String>()
    private val imapStore = TestImapStore().apply {
        addFolder(RecordingImapFolder("folder", events))
    }
    private val testSubject = CommandSearch(imapStore)

    @Test
    fun `search should report what it is doing before each step`() {
        testSubject.search(
            folderServerId = "folder",
            query = "query",
            requiredFlags = null,
            forbiddenFlags = null,
            performFullTextSearch = false,
            listener = { step -> events.add(step.name) },
        )

        assertThat(events).containsExactly(
            SearchStep.CONNECTING.name,
            "open",
            SearchStep.WAITING_FOR_RESPONSE.name,
            "search",
            "close",
        )
    }

    @Test
    fun `search should work without listener`() {
        testSubject.search(
            folderServerId = "folder",
            query = "query",
            requiredFlags = null,
            forbiddenFlags = null,
            performFullTextSearch = false,
        )

        assertThat(events).containsExactly("open", "search", "close")
    }

    @Test
    fun `search with criteria should return the uids in reverse order`() {
        val uids = testSubject.search(
            folderServerId = "folder",
            criteria = ServerSearchCriteria(text = "query"),
        )

        assertThat(uids).containsExactly("3", "2", "1")
    }
}

private class RecordingImapFolder(serverId: String, private val events: MutableList<String>) :
    TestImapFolder(serverId) {

    override fun open(mode: OpenMode) {
        events.add("open")
    }

    override fun search(
        queryString: String?,
        requiredFlags: Set<Flag>?,
        forbiddenFlags: Set<Flag>?,
        performFullTextSearch: Boolean,
    ): List<ImapMessage> {
        events.add("search")
        return emptyList()
    }

    override fun search(criteria: ServerSearchCriteria): List<ImapMessage> {
        events.add("search")
        return listOf(ImapMessage("1"), ImapMessage("2"), ImapMessage("3"))
    }

    override fun close() {
        events.add("close")
    }
}
