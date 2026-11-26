package com.fsck.k9.ui.settings.search

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlin.test.Test

class SettingsSearchIndexBuilderTest {
    private val testSubject = SettingsSearchIndexBuilder

    private val tree = SettingsNode(
        kind = SettingsNodeKind.SCREEN,
        key = null,
        title = "General settings",
        children = listOf(
            SettingsNode(
                kind = SettingsNodeKind.SCREEN,
                key = "display",
                title = "Display",
                children = listOf(
                    SettingsNode(
                        kind = SettingsNodeKind.CATEGORY,
                        key = "lists",
                        title = "Message list",
                        children = listOf(
                            SettingsNode(
                                kind = SettingsNodeKind.TOGGLE,
                                key = "stars",
                                title = "Important messages",
                                summary = "Stars mark important messages",
                            ),
                            SettingsNode(kind = SettingsNodeKind.PREFERENCE, key = "hidden", title = "Hidden"),
                        ),
                    ),
                ),
            ),
            SettingsNode(kind = SettingsNodeKind.PREFERENCE, key = "about", title = "About"),
        ),
    )

    @Test
    fun `puts screens and categories into the breadcrumb but not the root screen`() {
        val result = testSubject.build(tree, SettingsScope.General)

        val stars = result.single { it.highlightKey == "stars" }
        assertThat(stars.breadcrumb).containsExactly("Display", "Message list")
    }

    @Test
    fun `points a setting to the screen that contains it`() {
        val result = testSubject.build(tree, SettingsScope.General)

        assertThat(result.single { it.highlightKey == "stars" }.screenKey).isEqualTo("display")
        assertThat(result.single { it.highlightKey == "about" }.screenKey).isNull()
    }

    @Test
    fun `a screen opens itself and has nothing to highlight`() {
        val result = testSubject.build(tree, SettingsScope.General)

        val display = result.single { it.title == "Display" }
        assertThat(display.screenKey).isEqualTo("display")
        assertThat(display.highlightKey).isNull()
        assertThat(display.breadcrumb).isEmpty()
    }

    @Test
    fun `only switches with a key can be toggled in place`() {
        val result = testSubject.build(tree, SettingsScope.General)

        assertThat(result.single { it.highlightKey == "stars" }.isToggle).isTrue()
        assertThat(result.single { it.highlightKey == "about" }.isToggle).isFalse()
    }

    @Test
    fun `a switch that must not be changed in place is only found`() {
        val result = testSubject.build(tree, SettingsScope.General, noInlineToggleKeys = setOf("stars"))

        assertThat(result.single { it.highlightKey == "stars" }.isToggle).isFalse()
    }

    @Test
    fun `remembers which switch another setting depends on`() {
        val dependent = SettingsNode(
            kind = SettingsNodeKind.PREFERENCE,
            key = "child",
            title = "Child",
            dependencyKey = "parent",
        )
        val root = tree.copy(children = listOf(dependent))

        val result = testSubject.build(root, SettingsScope.General)

        assertThat(result.single().dependencyKey).isEqualTo("parent")
    }

    @Test
    fun `leaves out excluded preferences`() {
        val result = testSubject.build(tree, SettingsScope.General, excludedKeys = setOf("hidden"))

        assertThat(result.map { it.title }).containsExactly("Display", "Important messages", "About")
    }

    @Test
    fun `leaves out everything inside an excluded screen`() {
        val result = testSubject.build(tree, SettingsScope.General, excludedKeys = setOf("display"))

        assertThat(result.map { it.title }).containsExactly("About")
    }

    @Test
    fun `starts the breadcrumb of an account setting with the account name`() {
        val scope = SettingsScope.Account(accountId = "abc", accountName = "Work")

        val result = testSubject.build(tree, scope)

        assertThat(result.single { it.highlightKey == "stars" }.breadcrumb)
            .containsExactly("Work", "Display", "Message list")
        assertThat(result.first().scope).isEqualTo(scope)
    }

    @Test
    fun `gives the same setting of two accounts different ids`() {
        val first = testSubject.build(tree, SettingsScope.Account("a", "A")).map { it.id }
        val second = testSubject.build(tree, SettingsScope.Account("b", "B")).map { it.id }

        assertThat(first.intersect(second.toSet())).isEmpty()
    }
}
