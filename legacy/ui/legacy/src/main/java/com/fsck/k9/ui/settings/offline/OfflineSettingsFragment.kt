package com.fsck.k9.ui.settings.offline

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import net.thunderbird.core.ui.theme.api.FeatureThemeProvider
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Lets users configure the background download of all messages.
 */
class OfflineSettingsFragment : Fragment() {
    private val viewModel: OfflineSettingsViewModel by viewModel()
    private val themeProvider: FeatureThemeProvider by inject()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val uiState by viewModel.uiState.collectAsState()

                themeProvider.WithTheme {
                    OfflineSettingsScreen(
                        uiState = uiState,
                        onEnabledChange = viewModel::setEnabled,
                        onOnlyWhileChargingChange = viewModel::setOnlyWhileCharging,
                        onStorageLimitChange = viewModel::setStorageLimit,
                        onKeepFreeChange = viewModel::setKeepFree,
                    )
                }
            }
        }
    }
}
