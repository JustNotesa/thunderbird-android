package com.fsck.k9.preferences

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.thunderbird.core.preference.display.inboxSettings.DisplayInboxSettings
import net.thunderbird.core.preference.display.inboxSettings.DisplayInboxSettingsPreferenceManager
import net.thunderbird.core.preference.display.inboxSettings.UnifiedFoldersLayout
import net.thunderbird.core.preference.display.visualSettings.DisplayVisualSettings
import net.thunderbird.core.preference.display.visualSettings.DisplayVisualSettingsPreferenceManager
import net.thunderbird.feature.navigation.drawer.api.NavigationDrawerExternalContract.DrawerConfig
import org.junit.Test

class DefaultDrawerConfigManagerTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `should emit new drawer config when unified folders layout was changed`() = runTest {
        // Arrange
        val inboxSettingsPreferenceManager = FakeDisplayInboxSettingsPreferenceManager(DisplayInboxSettings())
        val visualSettingsPreferenceManager = FakeDisplayVisualSettingsPreferenceManager(DisplayVisualSettings())
        val testSubject = DefaultDrawerConfigManager(
            coroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            displayInboxSettingsPreferenceManager = inboxSettingsPreferenceManager,
            displayVisualSettingsPreferenceManager = visualSettingsPreferenceManager,
        )
        val drawerConfigs = mutableListOf<DrawerConfig>()
        val collectJob = launch {
            testSubject.getConfigFlow().collect { drawerConfigs += it }
        }

        advanceUntilIdle()

        // Act
        inboxSettingsPreferenceManager.save(
            DisplayInboxSettings(unifiedFoldersLayout = UnifiedFoldersLayout.Pinned),
        )

        advanceUntilIdle()

        // Then
        assertThat(drawerConfigs.map { it.unifiedFoldersLayout }).containsExactly(
            UnifiedFoldersLayout.Flat,
            UnifiedFoldersLayout.Pinned,
        )
        assertThat(testSubject.getConfig().unifiedFoldersLayout).isEqualTo(UnifiedFoldersLayout.Pinned)

        collectJob.cancel()
    }
}

private class FakeDisplayInboxSettingsPreferenceManager(
    config: DisplayInboxSettings,
) : DisplayInboxSettingsPreferenceManager {
    private val configState = MutableStateFlow(config)

    override fun save(config: DisplayInboxSettings) {
        configState.value = config
    }

    override fun getConfig(): DisplayInboxSettings = configState.value

    override fun getConfigFlow(): Flow<DisplayInboxSettings> = configState
}

private class FakeDisplayVisualSettingsPreferenceManager(
    config: DisplayVisualSettings,
) : DisplayVisualSettingsPreferenceManager {
    private val configState = MutableStateFlow(config)

    override fun save(config: DisplayVisualSettings) {
        configState.value = config
    }

    override fun getConfig(): DisplayVisualSettings = configState.value

    override fun getConfigFlow(): Flow<DisplayVisualSettings> = configState
}
