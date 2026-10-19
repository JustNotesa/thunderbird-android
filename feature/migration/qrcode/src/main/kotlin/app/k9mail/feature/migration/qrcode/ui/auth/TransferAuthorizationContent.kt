package app.k9mail.feature.migration.qrcode.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.k9mail.feature.migration.qrcode.R
import app.k9mail.feature.migration.qrcode.ui.auth.TransferAuthorizationContract.Event
import app.k9mail.feature.migration.qrcode.ui.auth.TransferAuthorizationContract.State
import app.k9mail.feature.migration.qrcode.ui.auth.TransferAuthorizationContract.Step
import net.thunderbird.components.ui.bolt.atom.Surface
import net.thunderbird.components.ui.bolt.atom.button.ButtonOutlined
import net.thunderbird.components.ui.bolt.atom.text.TextBodyLarge
import net.thunderbird.components.ui.bolt.molecule.LoadingView
import net.thunderbird.components.ui.bolt.template.ResponsiveContent
import net.thunderbird.components.ui.bolt.theme.BoltTheme

/**
 * Stateless content that asks the user for permission before the accounts of this device may leave it.
 */
@Composable
internal fun TransferAuthorizationContent(
    state: State,
    onEvent: (Event) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        ResponsiveContent(
            modifier = Modifier.testTag("TransferAuthorizationContent"),
        ) { contentPadding ->
            when (state.step) {
                Step.Checking, Step.Authenticating -> LoadingView(modifier = Modifier.fillMaxHeight())

                Step.EnteringPassword -> PasswordForm(
                    state = state,
                    contentPadding = contentPadding,
                    onEvent = onEvent,
                )

                Step.Denied -> DeniedContent(
                    contentPadding = contentPadding,
                    onEvent = onEvent,
                )

                // An authorized transfer is replaced by the screen the accounts are transferred with.
                Step.Authorized -> Unit
            }
        }
    }
}

/**
 * Tells the user that the accounts stay on this device, as nobody confirmed it's them.
 */
@Composable
private fun DeniedContent(
    contentPadding: PaddingValues,
    onEvent: (Event) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxHeight()
            .padding(BoltTheme.spacings.double)
            .padding(contentPadding),
    ) {
        TextBodyLarge(text = stringResource(R.string.migration_qrcode_transfer_authorization_denied))
        Spacer(modifier = Modifier.height(BoltTheme.spacings.triple))

        ButtonOutlined(
            text = stringResource(R.string.migration_qrcode_transfer_send_close_button_text),
            onClick = { onEvent(Event.CloseClicked) },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .testTag("CloseButton"),
        )
    }
}
