package app.k9mail.feature.account.server.validation.domain

import com.fsck.k9.mail.ServerSettings
import com.fsck.k9.mail.oauth.AuthStateStorage
import com.fsck.k9.mail.server.ServerSettingsValidationResult

interface ServerValidationDomainContract {

    interface UseCase {

        fun interface ValidateServerSettings {
            suspend fun execute(settings: ServerSettings): ServerSettingsValidationResult

            /**
             * Validates the given [settings] using the authentication state stored in [authStateStorage].
             *
             * This is needed to validate the server settings of an account that already exists. The authentication
             * state of such an account is stored in the account itself and not in a running account setup.
             */
            suspend fun execute(
                settings: ServerSettings,
                authStateStorage: AuthStateStorage,
            ): ServerSettingsValidationResult = throw UnsupportedOperationException(
                "Validating server settings with a given authentication state is not supported.",
            )
        }
    }
}
