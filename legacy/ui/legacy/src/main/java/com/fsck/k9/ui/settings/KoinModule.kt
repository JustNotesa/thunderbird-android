package com.fsck.k9.ui.settings

import com.fsck.k9.helper.NamedThreadFactory
import com.fsck.k9.ui.base.onehanded.OneHandedModeSetting
import com.fsck.k9.ui.settings.account.AccountSettingsDataStoreFactory
import com.fsck.k9.ui.settings.account.AccountSettingsViewModel
import com.fsck.k9.ui.settings.account.getSystemVibrator
import com.fsck.k9.ui.settings.export.SettingsExportViewModel
import com.fsck.k9.ui.settings.general.GeneralSettingsDataStore
import com.fsck.k9.ui.settings.general.GeneralSettingsViewModel
import com.fsck.k9.ui.settings.offline.OfflineSettingsViewModel
import java.util.concurrent.Executors
import net.thunderbird.core.preference.GeneralSettingsManager
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val settingsUiModule = module {
    single<OneHandedModeSetting> {
        val generalSettingsManager = get<GeneralSettingsManager>()
        OneHandedModeSetting { generalSettingsManager.getConfig().display.inboxSettings.isMessageListBottomAppBar }
    }

    viewModel { SettingsViewModel(accountManager = get(), accountRemover = get()) }

    viewModel {
        GeneralSettingsViewModel(
            logcatExporter = get(),
            syncDebugLogExporter = get(),
        )
    }
    factory {
        GeneralSettingsDataStore(
            jobManager = get(),
            appLanguageManager = get(),
            generalSettingsManager = get(),
            telemetryManager = get(),
            searchHistory = get(),
        )
    }
    single(named("SaveSettingsExecutorService")) {
        Executors.newSingleThreadExecutor(NamedThreadFactory("SaveSettings"))
    }

    viewModel {
        AccountSettingsViewModel(
            accountManager = get(),
            remoteFolderQueryRepository = get(),
            specialFolderSelectionStrategy = get(),
            messageStoreManager = get(),
            backendManager = get(),
            logger = get(),
        )
    }
    single {
        AccountSettingsDataStoreFactory(
            accountManager = get(),
            jobManager = get(),
            executorService = get(named("SaveSettingsExecutorService")),
            notificationChannelManager = get(),
            notificationController = get(),
            messagingController = get(),
            logger = get(),
        )
    }
    factory { getSystemVibrator(context = get()) }

    viewModel {
        SettingsExportViewModel(
            accountManager = get(),
            settingsExporter = get(),
        )
    }

    viewModel {
        OfflineSettingsViewModel(
            networkSettingsManager = get(),
            statusStore = get(),
            workerManager = get(),
            accountManager = get(),
            messageStoreManager = get(),
            logger = get(),
        )
    }
}
