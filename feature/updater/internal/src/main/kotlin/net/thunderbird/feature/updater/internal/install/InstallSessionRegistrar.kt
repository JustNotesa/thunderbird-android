package net.thunderbird.feature.updater.internal.install

import android.content.Context
import android.content.pm.PackageInstaller
import android.os.Handler
import android.os.Looper

/**
 * Registers the callback that watches the install sessions of the system.
 *
 * The seam exists, so that the observer of the progress can be tested without the system.
 */
internal interface InstallSessionRegistrar {
    /** Registers the [callback] for the install sessions of the system. */
    fun register(callback: PackageInstaller.SessionCallback)

    /** Removes the [callback] from the install sessions of the system. */
    fun unregister(callback: PackageInstaller.SessionCallback)
}

/**
 * Registers the callback with the package installer of the system.
 *
 * The callbacks are delivered on the main thread.
 */
internal class PackageInstallerSessionRegistrar(context: Context) : InstallSessionRegistrar {

    private val packageInstaller = context.packageManager.packageInstaller

    @Suppress("DEPRECATION")
    override fun register(callback: PackageInstaller.SessionCallback) {
        packageInstaller.registerSessionCallback(callback, Handler(Looper.getMainLooper()))
    }

    override fun unregister(callback: PackageInstaller.SessionCallback) {
        packageInstaller.unregisterSessionCallback(callback)
    }
}
