package app.k9mail.feature.migration.qrcode.transfer.auth

/**
 * Keeps the attempts in memory, so a test can hand the same store to more than one verifier.
 */
internal class FakeTransferAuthAttemptStore(
    attempts: TransferAuthAttempts = TransferAuthAttempts(),
) : TransferAuthAttemptStore {
    var storedAttempts = attempts
        private set

    override fun load(): TransferAuthAttempts {
        return storedAttempts
    }

    override fun save(attempts: TransferAuthAttempts) {
        storedAttempts = attempts
    }
}
