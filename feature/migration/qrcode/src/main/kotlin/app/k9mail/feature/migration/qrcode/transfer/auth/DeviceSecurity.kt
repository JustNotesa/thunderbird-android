package app.k9mail.feature.migration.qrcode.transfer.auth

import android.app.KeyguardManager

/**
 * Tells whether this device is protected by a screen lock.
 */
internal fun interface DeviceSecurity {
    /**
     * Returns whether the device has a secure screen lock (PIN, pattern or password).
     */
    fun isDeviceSecure(): Boolean
}

/**
 * Reads the screen lock state of the device.
 */
internal class AndroidDeviceSecurity(
    private val keyguardManager: KeyguardManager,
) : DeviceSecurity {
    override fun isDeviceSecure(): Boolean {
        return keyguardManager.isDeviceSecure
    }
}
