package app.k9mail.feature.migration.qrcode.transfer.auth

/**
 * What the user has to do before accounts with passwords may leave this device.
 */
internal sealed interface TransferAuthorizationMode {
    /**
     * The device is protected by a screen lock, so device authentication (biometric or device credential) is enough.
     */
    data object DeviceAuthentication : TransferAuthorizationMode

    /**
     * The device has no screen lock, so the password of one of [accounts] has to be entered.
     */
    data class AccountPassword(val accounts: List<PasswordAccount>) : TransferAuthorizationMode

    /**
     * No account of this device has a password, so there is no secret that could leave the device.
     */
    data object NotRequired : TransferAuthorizationMode
}

/**
 * An account of this device whose password authorizes an account transfer.
 *
 * @param id the ID of the account.
 * @param displayName the name or email address shown when the user picks an account.
 */
internal data class PasswordAccount(
    val id: String,
    val displayName: String,
)
