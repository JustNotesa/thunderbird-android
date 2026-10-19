package app.k9mail.feature.migration.qrcode.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.k9mail.feature.migration.qrcode.R
import app.k9mail.feature.migration.qrcode.transfer.auth.PasswordAccount
import app.k9mail.feature.migration.qrcode.ui.auth.TransferAuthorizationContract.Error
import app.k9mail.feature.migration.qrcode.ui.auth.TransferAuthorizationContract.Event
import app.k9mail.feature.migration.qrcode.ui.auth.TransferAuthorizationContract.State
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import net.thunderbird.components.ui.bolt.atom.button.ButtonFilled
import net.thunderbird.components.ui.bolt.atom.button.ButtonOutlined
import net.thunderbird.components.ui.bolt.atom.text.TextBodyLarge
import net.thunderbird.components.ui.bolt.atom.text.TextBodyMedium
import net.thunderbird.components.ui.bolt.atom.textfield.TextFieldOutlinedPassword
import net.thunderbird.components.ui.bolt.atom.textfield.TextFieldOutlinedSelect
import net.thunderbird.components.ui.bolt.theme.BoltTheme

/**
 * The form of a device that has no screen lock: the password of one of the accounts of this device authorizes the
 * transfer.
 */
@Composable
internal fun PasswordForm(
    state: State,
    contentPadding: PaddingValues,
    onEvent: (Event) -> Unit,
) {
    val account = state.accounts.firstOrNull { it.id == state.selectedAccountId }
    val lockOutMillis = (state.error as? Error.LockedOut)?.remainingMillis

    LaunchedEffect(key1 = lockOutMillis) {
        if (lockOutMillis != null) {
            delay(lockOutMillis)
            onEvent(Event.LockOutEnded)
        }
    }

    // The mode with a password always offers an account, so this only guards against a stale state.
    if (account == null) return

    Column(
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxHeight()
            .padding(BoltTheme.spacings.double)
            .padding(contentPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        TextBodyLarge(text = stringResource(R.string.migration_qrcode_transfer_authorization_password_intro))
        Spacer(modifier = Modifier.height(BoltTheme.spacings.triple))

        AccountField(
            accounts = remember(state.accounts) { state.accounts.toImmutableList() },
            selectedAccount = account,
            onEvent = onEvent,
        )
        Spacer(modifier = Modifier.height(BoltTheme.spacings.double))

        TextFieldOutlinedPassword(
            value = state.password,
            onValueChange = { password -> onEvent(Event.PasswordChanged(password)) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("PasswordField"),
            label = stringResource(R.string.migration_qrcode_transfer_authorization_password_label),
            hasError = state.error != null,
        )
        Spacer(modifier = Modifier.height(BoltTheme.spacings.default))

        state.error?.let { error ->
            ErrorText(error = error)
            Spacer(modifier = Modifier.height(BoltTheme.spacings.default))
        }

        FormActions(isLockedOut = lockOutMillis != null, onEvent = onEvent)
    }
}

@Composable
private fun AccountField(
    accounts: ImmutableList<PasswordAccount>,
    selectedAccount: PasswordAccount,
    onEvent: (Event) -> Unit,
) {
    TextFieldOutlinedSelect(
        options = accounts,
        selectedOption = selectedAccount,
        onValueChange = { account -> onEvent(Event.AccountSelected(accountId = account.id)) },
        optionToStringTransformation = { account -> account.displayName },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("AccountField"),
        label = stringResource(R.string.migration_qrcode_transfer_authorization_account_label),
    )
}

@Composable
private fun FormActions(
    isLockedOut: Boolean,
    onEvent: (Event) -> Unit,
) {
    Column {
        Spacer(modifier = Modifier.height(BoltTheme.spacings.triple))

        ButtonFilled(
            text = stringResource(R.string.migration_qrcode_transfer_authorization_continue_button_text),
            onClick = { onEvent(Event.ContinueClicked) },
            modifier = Modifier.testTag("ContinueButton"),
            enabled = !isLockedOut,
        )
        Spacer(modifier = Modifier.height(BoltTheme.spacings.default))

        ButtonOutlined(
            text = stringResource(R.string.migration_qrcode_transfer_authorization_cancel_button_text),
            onClick = { onEvent(Event.CancelClicked) },
            modifier = Modifier.testTag("CancelButton"),
        )
    }
}

@Composable
private fun ErrorText(error: Error) {
    val text = when (error) {
        is Error.WrongPassword -> stringResource(
            R.string.migration_qrcode_transfer_authorization_password_wrong,
            error.remainingAttempts,
        )

        is Error.LockedOut -> stringResource(
            R.string.migration_qrcode_transfer_authorization_password_locked_out,
            lockOutTime(millis = error.remainingMillis),
        )
    }

    TextBodyMedium(
        text = text,
        color = BoltTheme.colors.error,
        modifier = Modifier.testTag("ErrorText"),
    )
}
