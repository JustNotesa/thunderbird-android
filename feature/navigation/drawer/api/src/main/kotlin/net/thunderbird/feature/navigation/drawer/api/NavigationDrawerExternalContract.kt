package net.thunderbird.feature.navigation.drawer.api

import kotlinx.coroutines.flow.Flow
import net.thunderbird.core.preference.display.inboxSettings.UnifiedFoldersLayout

interface NavigationDrawerExternalContract {

    data class DrawerConfig(
        val showUnifiedFolders: Boolean,
        val showStarredCount: Boolean,
        val expandAllFolder: Boolean,
        val unifiedFoldersLayout: UnifiedFoldersLayout,
    )

    fun interface DrawerConfigLoader {
        fun loadDrawerConfigFlow(): Flow<DrawerConfig>
    }

    fun interface DrawerConfigWriter {
        fun writeDrawerConfig(drawerConfig: DrawerConfig)
    }
}
