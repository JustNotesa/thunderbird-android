package app.k9mail.feature.migration.qrcode.transfer.auth

internal class FakePasswordAccountSource(
    private val accounts: List<Pair<PasswordAccount, String?>>,
) : PasswordAccountSource {
    override suspend fun getAll(): List<Pair<PasswordAccount, String?>> {
        return accounts
    }
}

internal fun passwordAccount(id: String = "account-1", displayName: String = "user@domain.example") =
    PasswordAccount(id = id, displayName = displayName)
