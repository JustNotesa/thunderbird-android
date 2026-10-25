package net.thunderbird.feature.updater.internal.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File

/**
 * Writes a downloaded APK into a [PackageInstaller] session and hands that session over to the system.
 *
 * The session is always closed. When the APK can't be written or the session can't be committed, the session is
 * abandoned, so that no half written session is left behind.
 */
internal class ApkSessionWriter(private val context: Context) {

    /**
     * Writes [file] into a new install session and commits that session.
     *
     * @return the id of the committed session, `null` when the session had to be abandoned.
     */
    fun write(file: File): Int? {
        val packageInstaller = context.packageManager.packageInstaller
        val sessionId = packageInstaller.createSession(createSessionParams())
        return try {
            writeToSession(packageInstaller, sessionId, file)
        } catch (_: Exception) {
            // The session exists, but it can't be used anymore, so it has to be abandoned.
            abandonSessionById(packageInstaller, sessionId)
            null
        }
    }

    private fun writeToSession(
        packageInstaller: PackageInstaller,
        sessionId: Int,
        file: File,
    ): Int? {
        val session = packageInstaller.openSession(sessionId)
        return try {
            copyToSession(session, file)
            commitSession(session, sessionId)
            sessionId
        } catch (_: Exception) {
            abandonSession(session)
            null
        } finally {
            closeSession(session)
        }
    }

    private fun createSessionParams(): PackageInstaller.SessionParams {
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        return params
    }

    private fun copyToSession(session: PackageInstaller.Session, file: File) {
        val output = session.openWrite(SESSION_NAME, 0, file.length())
        output.use { stream ->
            file.inputStream().use { input -> input.copyTo(stream) }
            session.fsync(stream)
        }
    }

    private fun commitSession(session: PackageInstaller.Session, sessionId: Int) {
        val intent = Intent(context, InstallResultReceiver::class.java)
            .setAction(InstallResultReceiver.ACTION_INSTALL_RESULT)
            .setPackage(context.packageName)
        val pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, pendingIntentFlags())
        session.commit(pendingIntent.intentSender)
    }

    private fun pendingIntentFlags(): Int {
        // The system adds the result of the installation to the pending intent, so it has to be mutable.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
    }

    private fun abandonSession(session: PackageInstaller.Session) {
        try {
            session.abandon()
        } catch (_: Exception) {
            // The session was closed already, there is nothing left to abandon.
        }
    }

    private fun abandonSessionById(packageInstaller: PackageInstaller, sessionId: Int) {
        try {
            packageInstaller.abandonSession(sessionId)
        } catch (_: Exception) {
            // The session was closed already, there is nothing left to abandon.
        }
    }

    private fun closeSession(session: PackageInstaller.Session) {
        try {
            session.close()
        } catch (_: Exception) {
            // The session was closed already.
        }
    }

    private companion object {
        const val SESSION_NAME = "app-update"
    }
}
