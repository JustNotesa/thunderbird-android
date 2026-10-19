package app.k9mail.feature.migration.qrcode.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import app.k9mail.feature.migration.qrcode.R
import app.k9mail.feature.migration.qrcode.ui.auth.TransferAuthorizationContract.Effect
import app.k9mail.feature.migration.qrcode.ui.auth.TransferAuthorizationContract.Event
import app.k9mail.feature.migration.qrcode.ui.auth.TransferAuthorizationContract.Step
import kotlinx.coroutines.launch
import net.thunderbird.components.core.outcome.Outcome
import net.thunderbird.core.ui.contract.mvi.observe
import net.thunderbird.feature.account.server.settings.ui.common.Authenticator
import net.thunderbird.feature.account.server.settings.ui.common.rememberBiometricAuthenticator
import org.koin.compose.viewmodel.koinViewModel

/**
 * Asks the user for permission before the content of the screen is composed, so no secret of this device is shown or
 * prepared before the user confirmed it's them.
 *
 * The [content] is only composed once the transfer is authorized.
 */
@Composable
internal fun TransferAuthorizationScreen(
    finish: () -> Unit,
    viewModel: TransferAuthorizationViewModel = koinViewModel<TransferAuthorizationViewModel>(),
    content: @Composable () -> Unit,
) {
    val authenticator = rememberBiometricAuthenticator(
        title = stringResource(R.string.migration_qrcode_transfer_authorization_biometric_title),
        subtitle = stringResource(R.string.migration_qrcode_transfer_authorization_biometric_subtitle),
    )
    val scope = rememberCoroutineScope()

    val (state, dispatch) = viewModel.observe { effect ->
        when (effect) {
            Effect.Authenticate -> scope.launch {
                authenticate(authenticator = authenticator, dispatch = viewModel::event)
            }

            Effect.Close -> finish()
        }
    }

    LaunchedEffect(key1 = Unit) {
        dispatch(Event.StartScreen)
    }

    if (state.value.step == Step.Authorized) {
        content()
    } else {
        TransferAuthorizationContent(state = state.value, onEvent = dispatch)
    }
}

/**
 * Runs the device authentication and reports whether the user confirmed it's them. A prompt that isn't available or
 * that failed leaves the accounts on this device.
 */
private suspend fun authenticate(authenticator: Authenticator, dispatch: (Event) -> Unit) {
    val event = when (authenticator.authenticate()) {
        is Outcome.Success -> Event.AuthenticationSucceeded
        is Outcome.Failure -> Event.AuthenticationFailed
    }

    dispatch(event)
}
