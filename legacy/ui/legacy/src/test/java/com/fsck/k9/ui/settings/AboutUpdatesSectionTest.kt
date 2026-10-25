package com.fsck.k9.ui.settings

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Test

class AboutUpdatesSectionTest {

    @Test
    fun `download percent is calculated from the downloaded and the total number of bytes`() {
        assertThat(calculateDownloadPercent(downloadedBytes = 50, totalBytes = 200)).isEqualTo(25)
    }

    @Test
    fun `download percent is null when the total size is unknown`() {
        assertThat(calculateDownloadPercent(downloadedBytes = 50, totalBytes = 0)).isNull()
        assertThat(calculateDownloadPercent(downloadedBytes = 50, totalBytes = -1)).isNull()
    }

    @Test
    fun `download percent never exceeds 100 percent`() {
        assertThat(calculateDownloadPercent(downloadedBytes = 300, totalBytes = 200)).isEqualTo(100)
    }

    @Test
    fun `release notes don't contain markdown headings`() {
        val releaseNotes = "# Release notes\nSome notes"

        assertThat(formatReleaseNotes(releaseNotes)).isEqualTo("Some notes")
    }

    @Test
    fun `long release notes are shortened`() {
        val releaseNotes = "a".repeat(400)

        val result = formatReleaseNotes(releaseNotes)

        assertThat(result.length).isEqualTo(301)
    }
}
