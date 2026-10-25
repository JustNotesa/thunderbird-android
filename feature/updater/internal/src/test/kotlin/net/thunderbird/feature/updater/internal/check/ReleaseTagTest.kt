package net.thunderbird.feature.updater.internal.check

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isNull
import kotlin.test.Test

private const val PREFIX = "mein-thunderbird-"

class ReleaseTagTest {

    private val testSubject = ReleaseTag

    @Test
    fun `should parse release tag with date only`() {
        val result = testSubject.parse("mein-thunderbird-2026-10-25", PREFIX)

        assertThat(result).isEqualTo(ReleaseTag(year = 2026, month = 10, day = 25, buildNumber = 1))
    }

    @Test
    fun `should parse release tag with build number`() {
        val result = testSubject.parse("mein-thunderbird-2026-10-25-2", PREFIX)

        assertThat(result).isEqualTo(ReleaseTag(year = 2026, month = 10, day = 25, buildNumber = 2))
    }

    @Test
    fun `should parse release tag with two digit build number`() {
        val result = testSubject.parse("mein-thunderbird-2026-10-25-10", PREFIX)

        assertThat(result).isEqualTo(ReleaseTag(year = 2026, month = 10, day = 25, buildNumber = 10))
    }

    @Test
    fun `should parse release tag of leap day`() {
        val result = testSubject.parse("mein-thunderbird-2024-02-29", PREFIX)

        assertThat(result).isEqualTo(ReleaseTag(year = 2024, month = 2, day = 29, buildNumber = 1))
    }

    @Test
    fun `should be greater than release tag of same day without build number`() {
        val releaseTag = parseTag("mein-thunderbird-2026-10-25-2")
        val previousReleaseTag = parseTag("mein-thunderbird-2026-10-25")

        assertThat(releaseTag).isGreaterThan(previousReleaseTag)
    }

    @Test
    fun `should be greater than release tag of previous day`() {
        val releaseTag = parseTag("mein-thunderbird-2026-10-26")
        val previousReleaseTag = parseTag("mein-thunderbird-2026-10-25-9")

        assertThat(releaseTag).isGreaterThan(previousReleaseTag)
    }

    @Test
    fun `should be greater than release tag with smaller build number`() {
        val releaseTag = parseTag("mein-thunderbird-2026-10-25-10")
        val previousReleaseTag = parseTag("mein-thunderbird-2026-10-25-9")

        assertThat(releaseTag).isGreaterThan(previousReleaseTag)
    }

    @Test
    fun `should be less than release tag of later day`() {
        val releaseTag = parseTag("mein-thunderbird-2026-10-25")
        val laterReleaseTag = parseTag("mein-thunderbird-2026-10-26")

        assertThat(releaseTag).isLessThan(laterReleaseTag)
    }

    @Test
    fun `should reject release tag with different prefix`() {
        val result = testSubject.parse("k9mail-2026-10-25", PREFIX)

        assertThat(result).isNull()
    }

    @Test
    fun `should reject release tag whose prefix is not at the start`() {
        val result = testSubject.parse("combined-preview-2026-10-25", PREFIX)

        assertThat(result).isNull()
    }

    @Test
    fun `should reject release tag with build number zero`() {
        val result = testSubject.parse("mein-thunderbird-2026-10-25-0", PREFIX)

        assertThat(result).isNull()
    }

    @Test
    fun `should reject release tag with empty build number`() {
        val result = testSubject.parse("mein-thunderbird-2026-10-25-", PREFIX)

        assertThat(result).isNull()
    }

    @Test
    fun `should reject release tag with non numeric build number`() {
        val result = testSubject.parse("mein-thunderbird-2026-10-25-x", PREFIX)

        assertThat(result).isNull()
    }

    @Test
    fun `should reject release tag with two build numbers`() {
        val result = testSubject.parse("mein-thunderbird-2026-10-25-1-2", PREFIX)

        assertThat(result).isNull()
    }

    @Test
    fun `should reject release tag with build number that is too large`() {
        val result = testSubject.parse("mein-thunderbird-2026-10-25-99999999999", PREFIX)

        assertThat(result).isNull()
    }

    @Test
    fun `should reject release tag with trailing whitespace`() {
        val result = testSubject.parse("mein-thunderbird-2026-10-25 ", PREFIX)

        assertThat(result).isNull()
    }

    @Test
    fun `should reject release tag with leading whitespace`() {
        val result = testSubject.parse(" mein-thunderbird-2026-10-25", PREFIX)

        assertThat(result).isNull()
    }

    @Test
    fun `should reject release tag with invalid month`() {
        val result = testSubject.parse("mein-thunderbird-2026-13-01", PREFIX)

        assertThat(result).isNull()
    }

    @Test
    fun `should reject release tag with invalid day`() {
        val result = testSubject.parse("mein-thunderbird-2026-02-30", PREFIX)

        assertThat(result).isNull()
    }

    @Test
    fun `should reject release tag with day that doesn't exist in february`() {
        val result = testSubject.parse("mein-thunderbird-2026-02-29", PREFIX)

        assertThat(result).isNull()
    }

    @Test
    fun `should reject release tag with single digit month and day`() {
        val result = testSubject.parse("mein-thunderbird-2026-1-5", PREFIX)

        assertThat(result).isNull()
    }

    @Test
    fun `should reject empty release tag`() {
        val result = testSubject.parse("", PREFIX)

        assertThat(result).isNull()
    }

    @Test
    fun `should reject release tag without date`() {
        val result = testSubject.parse(PREFIX, PREFIX)

        assertThat(result).isNull()
    }

    private fun parseTag(tag: String): ReleaseTag = requireNotNull(testSubject.parse(tag, PREFIX))
}
