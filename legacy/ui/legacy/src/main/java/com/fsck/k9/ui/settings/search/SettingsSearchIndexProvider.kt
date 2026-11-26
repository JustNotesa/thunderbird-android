package com.fsck.k9.ui.settings.search

import android.content.Context
import android.os.Build
import com.fsck.k9.controller.MessagingController
import com.fsck.k9.ui.BuildConfig
import com.fsck.k9.ui.R
import com.fsck.k9.ui.settings.account.AccountSettingsDataStoreFactory
import com.fsck.k9.ui.settings.account.Vibrator
import com.fsck.k9.ui.settings.general.GENERAL_FONT_PREFERENCE_KEYS
import com.fsck.k9.ui.settings.general.GeneralSettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.thunderbird.components.core.logging.Logger
import net.thunderbird.core.android.account.LegacyAccount
import net.thunderbird.core.featureflag.FeatureFlagProvider
import net.thunderbird.core.featureflag.keys.GeneratedFeatureFlagKey
import app.k9mail.feature.telemetry.api.TelemetryManager

/**
 * Finds out which settings exist for the user right now and reads them from the preference XML files.
 *
 * What the settings screens remove at runtime (because of the build, the device or the server) is left out here as
 * well, so the search never offers a setting the user cannot see.
 */
internal class SettingsSearchIndexProvider(
    private val featureFlagProvider: FeatureFlagProvider,
    private val telemetryManager: TelemetryManager,
    private val messagingController: MessagingController,
    private val vibrator: Vibrator,
    private val accountDataStoreFactory: AccountSettingsDataStoreFactory,
    private val generalDataStore: GeneralSettingsDataStore,
    private val logger: Logger,
) {
    /** Reads the settings on a background thread, as this inflates the preference XML files of all accounts. */
    suspend fun load(context: Context, accounts: List<LegacyAccount>): SettingsSearchIndex {
        return withContext(Dispatchers.IO) { loadBlocking(context, accounts) }
    }

    private fun loadBlocking(context: Context, accounts: List<LegacyAccount>): SettingsSearchIndex {
        val sources = buildList {
            add(generalSource())
            accounts.forEach { add(accountSource(it)) }
        }

        return SettingsSearchIndexLoader(context, logger).load(sources)
    }

    private fun generalSource(): SettingsSearchSource {
        val excluded = buildSet {
            var fontsDisabled = false
            featureFlagProvider.provide(GeneratedFeatureFlagKey.DISABLE_FONT_SIZE_CONFIG)
                .onEnabled { fontsDisabled = true }
            if (fontsDisabled) addAll(GENERAL_FONT_PREFERENCE_KEYS)

            if (!BuildConfig.DEBUG) add("debug_secret_debug_screen")
            if (!telemetryManager.isTelemetryFeatureIncluded()) add("data_collection")
        }

        return SettingsSearchSource(
            scope = SettingsScope.General,
            xmlResource = R.xml.general_settings,
            dataStore = generalDataStore,
            excludedKeys = excluded,
            // Turning this on or off asks the user for a file to write the logs to.
            noInlineToggleKeys = setOf("sync_debug_logging"),
        )
    }

    private fun accountSource(account: LegacyAccount): SettingsSearchSource {
        val accountId = account.id
        val excluded = buildSet {
            if (!messagingController.supportsExpunge(accountId)) add("expunge_policy")
            if (!messagingController.supportsSearchByDate(accountId)) add("account_message_age")
            if (!messagingController.isPushCapable(accountId)) add("push_advanced")
            if (!messagingController.supportsFolderSubscriptions(accountId)) add("subscribed_folders_only")
            if (!messagingController.isMoveCapable(accountId)) {
                addAll(listOf("archive_folder", "drafts_folder", "sent_folder", "spam_folder", "trash_folder"))
            }
            if (!vibrator.hasVibrator) add("account_combined_vibration")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) add("notification_channels")
        }

        return SettingsSearchSource(
            scope = SettingsScope.Account(
                accountId = accountId.toString(),
                accountName = account.name?.takeIf { it.isNotBlank() } ?: account.email,
            ),
            xmlResource = R.xml.account_settings,
            dataStore = accountDataStoreFactory.create(account),
            excludedKeys = excluded,
            // Choosing the OpenPGP app opens a dialog.
            noInlineToggleKeys = setOf("openpgp_provider"),
        )
    }
}
