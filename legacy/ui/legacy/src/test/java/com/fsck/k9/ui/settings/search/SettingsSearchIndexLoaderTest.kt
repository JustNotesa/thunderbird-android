package com.fsck.k9.ui.settings.search

import android.content.Context
import androidx.preference.PreferenceDataStore
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.fsck.k9.K9RobolectricTest
import com.fsck.k9.ui.R
import kotlin.test.Test
import net.thunderbird.components.core.logging.testing.TestLogger

class SettingsSearchIndexLoaderTest : K9RobolectricTest() {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dataStore = FakeDataStore()
    private val testSubject = SettingsSearchIndexLoader(context, TestLogger())

    @Test
    fun `reads every screen of the general settings from the XML file`() {
        val index = testSubject.load(listOf(generalSource()))

        assertThat(index.failedScopes).isEmpty()
        assertThat(index.entries.map { it.screenKey }.toSet()).contains("display_preferences")
    }

    @Test
    fun `finds the setting for the star of important messages by a word of its summary`() {
        val index = testSubject.load(listOf(generalSource()))

        val result = SettingsSearcher.search(index.entries, "stars")

        val stars = result.firstOrNull { it.highlightKey == "messagelist_stars" }
        assertThat(stars).isNotNull()
        assertThat(stars!!.isToggle).isTrue()
        assertThat(stars.breadcrumb).isNotEmpty()
    }

    @Test
    fun `leaves out excluded settings`() {
        val index = testSubject.load(listOf(generalSource(excludedKeys = setOf("messagelist_stars"))))

        val keys = index.entries.mapNotNull { it.highlightKey }
        assertThat(keys.contains("messagelist_stars")).isEqualTo(false)
    }

    @Test
    fun `reads and writes switches through the data store of their scope`() {
        val index = testSubject.load(listOf(generalSource()))

        index.setOn(SettingsScope.General, "messagelist_stars", true)

        assertThat(index.isOn(SettingsScope.General, "messagelist_stars")).isTrue()
        assertThat(dataStore.booleans).isEqualTo(mapOf("messagelist_stars" to true))
    }

    @Test
    fun `does not write to the data store while reading the settings`() {
        testSubject.load(listOf(generalSource()))

        assertThat(dataStore.booleans).isEmpty()
    }

    @Test
    fun `puts the account name first in the breadcrumb of an account setting`() {
        val scope = SettingsScope.Account(accountId = "id", accountName = "Work")
        val source = SettingsSearchSource(
            scope = scope,
            xmlResource = R.xml.account_settings,
            dataStore = FakeDataStore(),
            excludedKeys = emptySet(),
            noInlineToggleKeys = emptySet(),
        )

        val index = testSubject.load(listOf(source))

        assertThat(index.entries).isNotEmpty()
        assertThat(index.entries.all { it.breadcrumb.firstOrNull() == "Work" || it.breadcrumb.isEmpty() }).isTrue()
        assertThat(index.entries.any { it.breadcrumb.firstOrNull() == "Work" }).isTrue()
    }

    private fun generalSource(excludedKeys: Set<String> = emptySet()) = SettingsSearchSource(
        scope = SettingsScope.General,
        xmlResource = R.xml.general_settings,
        dataStore = dataStore,
        excludedKeys = excludedKeys,
        noInlineToggleKeys = emptySet(),
    )

    private class FakeDataStore : PreferenceDataStore() {
        val booleans = mutableMapOf<String, Boolean>()

        override fun getBoolean(key: String, defValue: Boolean) = booleans[key] ?: defValue

        // The vibration setting cannot handle a missing value
        override fun getString(key: String, defValue: String?) = defValue ?: "false|Default|1"

        override fun putBoolean(key: String, value: Boolean) {
            booleans[key] = value
        }
    }
}
