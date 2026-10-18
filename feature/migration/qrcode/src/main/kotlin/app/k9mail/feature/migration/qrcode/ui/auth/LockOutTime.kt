package app.k9mail.feature.migration.qrcode.ui.auth

/**
 * How long the user still has to wait before the accounts may be tried again, as minutes and seconds.
 *
 * @param millis How much of the lock out is left, never less than nothing.
 */
internal fun lockOutTime(millis: Long): String {
    val totalSeconds = (millis / MILLISECONDS_PER_SECOND).coerceAtLeast(NO_SECONDS)
    val minutes = totalSeconds / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE

    return when {
        minutes == NO_MINUTES -> "${seconds}s"
        seconds == NO_SECONDS -> "${minutes}min"
        else -> "${minutes}min ${seconds}s"
    }
}

private const val MILLISECONDS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val NO_MINUTES = 0L
private const val NO_SECONDS = 0L
