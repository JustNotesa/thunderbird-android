package app.k9mail.feature.migration.qrcode.transfer.auth

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class TransferAuthorizationTest {
    private val account = passwordAccount()

    @Test
    fun `a device with a screen lock is authorized by device authentication`() = runTest {
        val testSubject = createAuthorization(
            deviceSecurity = DeviceSecurity { true },
            accounts = listOf(account to "password"),
        )

        val result = testSubject.mode()

        assertThat(result).isEqualTo(TransferAuthorizationMode.DeviceAuthentication)
    }

    @Test
    fun `a device without a screen lock needs the password of an account`() = runTest {
        val accounts = listOf(account to "password", passwordAccount(id = "account-2") to "other")
        val testSubject = createAuthorization(deviceSecurity = DeviceSecurity { false }, accounts = accounts)

        val result = testSubject.mode()

        assertThat(result).isEqualTo(TransferAuthorizationMode.AccountPassword(accounts.map { it.first }))
    }

    @Test
    fun `a device without accounts that have a password needs no authorization`() = runTest {
        val accounts = listOf(account to null, passwordAccount(id = "account-2") to "")
        val testSubject = createAuthorization(deviceSecurity = DeviceSecurity { false }, accounts = accounts)

        val result = testSubject.mode()

        assertThat(result).isEqualTo(TransferAuthorizationMode.NotRequired)
    }

    private fun createAuthorization(
        deviceSecurity: DeviceSecurity,
        accounts: List<Pair<PasswordAccount, String?>>,
    ): TransferAuthorization {
        return TransferAuthorization(
            deviceSecurity = deviceSecurity,
            passwordVerifier = AccountPasswordVerifier(
                accountSource = FakePasswordAccountSource(accounts),
                attemptStore = FakeTransferAuthAttemptStore(),
                clock = { NOW },
            ),
        )
    }

    private companion object {
        private const val NOW = 1_792_300_000_000L
    }
}
