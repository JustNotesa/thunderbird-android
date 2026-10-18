package app.k9mail.feature.migration.qrcode.transfer.auth

/**
 * Decides what the user has to do before accounts with passwords may leave this device.
 *
 * A device with a screen lock is protected by device authentication. Without a screen lock the password of one of
 * the accounts of this device is needed. When no account has a password, there is no secret to protect.
 */
internal class TransferAuthorization(
    private val deviceSecurity: DeviceSecurity,
    private val passwordVerifier: AccountPasswordVerifier,
) {
    /**
     * Returns how the transfer has to be authorized.
     */
    suspend fun mode(): TransferAuthorizationMode {
        if (deviceSecurity.isDeviceSecure()) {
            return TransferAuthorizationMode.DeviceAuthentication
        }

        val passwordAccounts = passwordVerifier.passwordAccounts()

        return when {
            passwordAccounts.isEmpty() -> TransferAuthorizationMode.NotRequired
            else -> TransferAuthorizationMode.AccountPassword(accounts = passwordAccounts)
        }
    }
}
