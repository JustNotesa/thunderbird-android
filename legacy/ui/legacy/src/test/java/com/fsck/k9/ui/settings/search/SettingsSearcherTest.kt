package com.fsck.k9.ui.settings.search

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import kotlin.test.Test

class SettingsSearcherTest {
    private val testSubject = SettingsSearcher

    @Test
    fun `finds a setting by a word of its summary ignoring case`() {
        val entries = listOf(
            entry(title = "Important messages", summary = "Stars mark important messages"),
            entry(title = "Theme", summary = "Light"),
        )

        val result = testSubject.search(entries, "  STAR ")

        assertThat(result.map { it.title }).containsExactly("Important messages")
    }

    @Test
    fun `requires every word of the query to match`() {
        val entries = listOf(
            entry(title = "Show pictures", summary = "Show contact pictures"),
            entry(title = "Show names", summary = "Show correspondent names"),
        )

        val result = testSubject.search(entries, "show pictures")

        assertThat(result.map { it.title }).containsExactly("Show pictures")
    }

    @Test
    fun `ignores accents so umlauts can be typed without them`() {
        val entries = listOf(entry(title = "Größe der Schrift", summary = "Für Nachrichten"))

        val result = testSubject.search(entries, "fur")

        assertThat(result.map { it.title }).containsExactly("Größe der Schrift")
    }

    @Test
    fun `also looks at the texts shown when the switch is on or off`() {
        val entries = listOf(entry(title = "Theme", summaryOn = "Dark theme in use", summaryOff = "Light theme in use"))

        val result = testSubject.search(entries, "light")

        assertThat(result).containsExactly(entries.single())
    }

    @Test
    fun `lists title matches before summary matches`() {
        val entries = listOf(
            entry(title = "Notifications", summary = "Choose the sound"),
            entry(title = "Sound", summary = null),
        )

        val result = testSubject.search(entries, "sound")

        assertThat(result.map { it.title }).containsExactly("Sound", "Notifications")
    }

    @Test
    fun `an empty query finds nothing`() {
        val entries = listOf(entry(title = "Theme"))

        val result = testSubject.search(entries, "   ")

        assertThat(result).isEmpty()
    }

    @Test
    fun `finds nothing when no setting matches`() {
        val entries = listOf(entry(title = "Theme"))

        val result = testSubject.search(entries, "zzz")

        assertThat(result).isEmpty()
    }

    @Test
    fun `joins a breadcrumb with an arrow`() {
        val result = testSubject.formatBreadcrumb(listOf("Display", "Message list"))

        assertThat(result).isEqualTo("Display › Message list")
    }

    @Test
    fun `summary follows the state for settings that describe it`() {
        val testSubject = entry(title = "Theme", summary = "Plain", summaryOn = "On text", summaryOff = "Off text")

        assertThat(testSubject.summaryFor(true)).isEqualTo("On text")
        assertThat(testSubject.summaryFor(false)).isEqualTo("Off text")
        assertThat(testSubject.summaryFor(null)).isEqualTo("Plain")
    }

    @Test
    fun `summary falls back to the plain text when there is no text for the state`() {
        val testSubject = entry(title = "Stars", summary = "Plain")

        assertThat(testSubject.summaryFor(true)).isEqualTo("Plain")
        assertThat(testSubject.summaryFor(false)).isEqualTo("Plain")
    }

    private fun entry(
        title: String,
        summary: String? = null,
        summaryOn: String? = null,
        summaryOff: String? = null,
    ) = SettingsSearchEntry(
        id = title,
        scope = SettingsScope.General,
        title = title,
        summary = summary,
        summaryOn = summaryOn,
        summaryOff = summaryOff,
        breadcrumb = emptyList(),
        screenKey = null,
        highlightKey = null,
        isToggle = false,
    )
}
