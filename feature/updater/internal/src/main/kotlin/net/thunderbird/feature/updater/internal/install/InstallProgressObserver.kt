package net.thunderbird.feature.updater.internal.install

import android.content.pm.PackageInstaller
import kotlinx.coroutines.flow.MutableStateFlow
import net.thunderbird.components.core.logging.Logger
import net.thunderbird.feature.updater.api.UpdateInstallState
import net.thunderbird.feature.updater.internal.background.UpdateNotifier

private const val TAG = "InstallProgressObserver"

/**
 * Watches the installation of the app update.
 *
 * Android reports the progress of an install session: from 0 to 0.8 the APK is staged by the app and from 0.8 to 1
 * the system installs it, which only starts after the user confirmed the installation.
 *
 * The observer tells the user about the progress of the installation with a notification, because Android closes the
 * app while it installs an update of itself.
 */
internal class InstallProgressObserver(
    private val registrar: InstallSessionRegistrar,
    private val state: MutableStateFlow<UpdateInstallState>,
    private val notifier: UpdateNotifier,
    private val logger: Logger,
) : PackageInstaller.SessionCallback() {

    private var watchedSessionId = NO_SESSION
    private var watching = false

    /** Watches the install session with the id [sessionId]. */
    fun watch(sessionId: Int) {
        stopWatching()
        watchedSessionId = sessionId
        watching = true
        registrar.register(this)
    }

    /** Stops watching the install session. */
    fun stopWatching() {
        if (!watching) return

        watching = false
        watchedSessionId = NO_SESSION
        registrar.unregister(this)
    }

    /**
     * Reports the progress of the installation of the session with the id [sessionId].
     *
     * The progress of the staging of the APK isn't reported, because the user doesn't wait for it.
     */
    fun onProgress(sessionId: Int, progress: Float) {
        if (sessionId != watchedSessionId) return
        if (progress <= INSTALL_START_PROGRESS) return

        val installProgress = ((progress - INSTALL_START_PROGRESS) / INSTALL_PROGRESS_RANGE).coerceIn(0f, 1f)
        state.value = UpdateInstallState.Installing(installProgress)
        notifier.notifyInstalling(installProgress)
    }

    override fun onProgressChanged(sessionId: Int, progress: Float) {
        onProgress(sessionId, progress)
    }

    override fun onFinished(sessionId: Int, success: Boolean) {
        if (sessionId != watchedSessionId) return

        stopWatching()
        val current = state.value
        val pending = current is UpdateInstallState.Installing || current is UpdateInstallState.WaitingForConfirmation
        if (!success && pending) {
            notifier.cancelInstalling()
        }
    }

    override fun onCreated(sessionId: Int) {
        logger.debug(TAG) { "The install session $sessionId was created." }
    }

    override fun onBadgingChanged(sessionId: Int) {
        logger.debug(TAG) { "The badging of the install session $sessionId changed." }
    }

    override fun onActiveChanged(sessionId: Int, active: Boolean) {
        logger.debug(TAG) { "The install session $sessionId is active: $active." }
    }

    private companion object {
        const val NO_SESSION = -1
        const val INSTALL_START_PROGRESS = 0.8f
        const val INSTALL_PROGRESS_RANGE = 0.2f
    }
}
