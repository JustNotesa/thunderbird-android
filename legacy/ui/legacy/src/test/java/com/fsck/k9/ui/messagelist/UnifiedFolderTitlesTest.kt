package com.fsck.k9.ui.messagelist

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.fsck.k9.ui.R
import net.thunderbird.feature.mail.folder.FolderType
import org.junit.Test

class UnifiedFolderTitlesTest {

    @Test
    fun `unifiedFolderTitle() with INBOX should return the unified inbox title`() {
        assertThat(unifiedFolderTitle(FolderType.INBOX)).isEqualTo(R.string.integrated_inbox_title)
    }

    @Test
    fun `unifiedFolderTitle() with a special folder should return its unified title`() {
        assertThat(unifiedFolderTitle(FolderType.DRAFTS)).isEqualTo(R.string.unified_drafts_title)
        assertThat(unifiedFolderTitle(FolderType.SENT)).isEqualTo(R.string.unified_sent_title)
        assertThat(unifiedFolderTitle(FolderType.ARCHIVE)).isEqualTo(R.string.unified_archive_title)
        assertThat(unifiedFolderTitle(FolderType.SPAM)).isEqualTo(R.string.unified_spam_title)
        assertThat(unifiedFolderTitle(FolderType.TRASH)).isEqualTo(R.string.unified_trash_title)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unifiedFolderTitle() with REGULAR should throw`() {
        unifiedFolderTitle(FolderType.REGULAR)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unifiedFolderTitle() with OUTBOX should throw`() {
        unifiedFolderTitle(FolderType.OUTBOX)
    }
}
