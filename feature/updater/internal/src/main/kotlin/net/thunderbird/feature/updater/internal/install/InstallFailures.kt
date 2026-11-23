package net.thunderbird.feature.updater.internal.install

import net.thunderbird.feature.updater.api.UpdateInstallFailure
import net.thunderbird.feature.updater.api.UpdateInstallState

/**
 * Builds the [UpdateInstallState.Failed] values of the installer.
 *
 * A failure names the release it belongs to and the time it happened at. The release is the one the installer works
 * on, which is remembered in [tag] when the work starts.
 */
internal class InstallFailures(private val clock: () -> Long = System::currentTimeMillis) {

    /** The release tag of the update the installer works on, `null` when there is none. */
    @Volatile
    var tag: String? = null

    /**
     * Builds a failure with the reason [reason] for the release [releaseTag], which is the release the installer works
     * on by default.
     */
    fun create(reason: UpdateInstallFailure, releaseTag: String? = tag): UpdateInstallState.Failed {
        return UpdateInstallState.Failed(reason = reason, tag = releaseTag, failedAtMillis = clock())
    }
}
