package com.fsck.k9.preferences

import net.thunderbird.core.android.account.LegacyAccountManager
import net.thunderbird.core.preference.GeneralSettingsManager
import net.thunderbird.core.preference.update

/**
 * Configures the unified inbox based on the number of accounts:
 * - If there are exactly 2 accounts → unified inbox is enabled.
 * - For all other cases → no change is made.
 *
 * The unified inbox is shown by default, so it is not disabled when there is only 1 account.
 */
class UnifiedInboxConfigurator(
    private val accountManager: LegacyAccountManager,
    private val generalSettingsManager: GeneralSettingsManager,
) {
    fun configureUnifiedInbox() {
        if (accountManager.findAll().size == 2) {
            updateUnifiedInbox(true)
        }
    }
    private fun updateUnifiedInbox(isShowUnifiedInbox: Boolean) {
        generalSettingsManager.update { settings ->
            settings.copy(
                display = settings.display.copy(
                    inboxSettings = settings.display.inboxSettings.copy(
                        isShowUnifiedInbox = isShowUnifiedInbox,
                    ),
                ),
            )
        }
    }
}
