package net.thunderbird.app.common.account

import app.k9mail.feature.migration.qrcode.settings.ImportAccountColorProvider

/**
 * Picks the colors of the accounts that are imported by a transfer. The colors are picked the same way accounts that
 * are set up manually get their color.
 */
internal class DefaultImportAccountColorProvider(
    private val accountColorPicker: AccountColorPicker,
) : ImportAccountColorProvider {
    override suspend fun pickColors(count: Int): List<Int> {
        return accountColorPicker.pickColors(count)
    }
}
