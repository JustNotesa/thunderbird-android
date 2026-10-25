package net.thunderbird.feature.updater.internal.check

/**
 * A release tag of the app, e.g. `mein-thunderbird-2026-10-26-2`.
 *
 * @param year Year of the release.
 * @param month Month of the release.
 * @param day Day of the release.
 * @param buildNumber Build number of the release; `1` when the tag has no build number suffix.
 */
internal data class ReleaseTag(
    val year: Int,
    val month: Int,
    val day: Int,
    val buildNumber: Int,
) : Comparable<ReleaseTag> {

    override fun compareTo(other: ReleaseTag): Int =
        compareBy<ReleaseTag>({ it.year }, { it.month }, { it.day }, { it.buildNumber }).compare(this, other)

    companion object {
        private const val YEAR_GROUP = 1
        private const val MONTH_GROUP = 2
        private const val DAY_GROUP = 3
        private const val BUILD_NUMBER_GROUP = 4

        private const val FIRST_MONTH = 1
        private const val LAST_MONTH = 12
        private const val FIRST_DAY = 1

        private const val FEBRUARY = 2
        private const val APRIL = 4
        private const val JUNE = 6
        private const val SEPTEMBER = 9
        private const val NOVEMBER = 11

        private const val DAYS_IN_FEBRUARY = 28
        private const val DAYS_IN_FEBRUARY_LEAP_YEAR = 29
        private const val DAYS_IN_SHORT_MONTH = 30
        private const val DAYS_IN_LONG_MONTH = 31

        private const val LEAP_YEAR_DIVISOR = 4
        private const val CENTURY_YEARS = 100
        private const val FOUR_CENTURY_YEARS = 400

        private const val FIRST_BUILD_NUMBER = 1

        private const val TAG_PATTERN = """([0-9]{4})-([0-9]{2})-([0-9]{2})(?:-([0-9]+))?"""

        /**
         * Parses a release tag.
         *
         * @param tag Tag to parse, e.g. `mein-thunderbird-2026-10-26-2`.
         * @param prefix Prefix the tag has to start with, e.g. `mein-thunderbird-`.
         * @return The parsed release tag, or `null` if [tag] isn't a valid release tag.
         */
        fun parse(tag: String, prefix: String): ReleaseTag? {
            val match = tagPattern(prefix).matchEntire(tag) ?: return null
            return toReleaseTag(match)
        }

        private fun tagPattern(prefix: String): Regex = Regex(Regex.escape(prefix) + TAG_PATTERN)

        private fun toReleaseTag(match: MatchResult): ReleaseTag? {
            val year = match.groupValues[YEAR_GROUP].toInt()
            val month = match.groupValues[MONTH_GROUP].toInt()
            val day = match.groupValues[DAY_GROUP].toInt()
            val buildNumber = parseBuildNumber(match.groupValues[BUILD_NUMBER_GROUP]) ?: return null
            return if (isValidDate(year, month, day)) ReleaseTag(year, month, day, buildNumber) else null
        }

        private fun parseBuildNumber(text: String): Int? = when {
            text.isEmpty() -> FIRST_BUILD_NUMBER
            else -> text.toIntOrNull()?.takeIf { it >= FIRST_BUILD_NUMBER }
        }

        private fun isValidDate(year: Int, month: Int, day: Int): Boolean =
            month in FIRST_MONTH..LAST_MONTH && day in FIRST_DAY..daysInMonth(year, month)

        private fun daysInMonth(year: Int, month: Int): Int = when (month) {
            FEBRUARY -> if (isLeapYear(year)) DAYS_IN_FEBRUARY_LEAP_YEAR else DAYS_IN_FEBRUARY
            APRIL, JUNE, SEPTEMBER, NOVEMBER -> DAYS_IN_SHORT_MONTH
            else -> DAYS_IN_LONG_MONTH
        }

        private fun isLeapYear(year: Int): Boolean =
            year % LEAP_YEAR_DIVISOR == 0 && (year % CENTURY_YEARS != 0 || year % FOUR_CENTURY_YEARS == 0)
    }
}
