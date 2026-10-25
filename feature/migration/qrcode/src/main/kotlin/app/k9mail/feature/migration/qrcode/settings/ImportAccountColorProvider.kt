package app.k9mail.feature.migration.qrcode.settings

/**
 * Provides the colors for the accounts that are imported by a transfer.
 *
 * The implementation is provided by the app, so imported accounts get the same colors as accounts that were set up
 * manually.
 */
interface ImportAccountColorProvider {
    /**
     * Picks the colors for [count] accounts that are imported together.
     *
     * @return Exactly [count] colors. Every account of the batch gets a color that no other account of the batch uses.
     */
    suspend fun pickColors(count: Int): List<Int>
}
