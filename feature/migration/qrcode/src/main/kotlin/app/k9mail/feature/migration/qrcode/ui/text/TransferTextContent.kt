package app.k9mail.feature.migration.qrcode.ui.text

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.k9mail.feature.migration.qrcode.R
import app.k9mail.feature.migration.qrcode.ui.guide.TransferGuideIllustration
import app.k9mail.feature.migration.qrcode.ui.text.TransferTextContract.Event
import app.k9mail.feature.migration.qrcode.ui.text.TransferTextContract.State
import app.k9mail.feature.migration.qrcode.ui.text.TransferTextContract.Step
import net.thunderbird.components.ui.bolt.atom.Surface
import net.thunderbird.components.ui.bolt.atom.text.TextBodyMedium
import net.thunderbird.components.ui.bolt.atom.text.TextTitleLarge
import net.thunderbird.components.ui.bolt.template.ResponsiveContent
import net.thunderbird.components.ui.bolt.theme.BoltTheme

/**
 * Stateless content of the screen that copies the accounts of this device into the clipboard.
 */
@Composable
internal fun TransferTextContent(
    state: State,
    onEvent: (Event) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        ResponsiveContent(
            modifier = Modifier.testTag("TransferTextContent"),
        ) { contentPadding ->
            when (val step = state.step) {
                is Step.Ready -> ReadyContent(
                    text = step.text,
                    accountCount = step.accountCount,
                    revealed = state.revealed,
                    copied = state.copied,
                    contentPadding = contentPadding,
                    onEvent = onEvent,
                )

                Step.Preparing -> MessageStepContent(
                    text = stringResource(R.string.migration_qrcode_transfer_send_preparing),
                    contentPadding = contentPadding,
                    onEvent = onEvent,
                )

                Step.NoAccounts -> MessageStepContent(
                    text = stringResource(R.string.migration_qrcode_transfer_send_no_accounts),
                    contentPadding = contentPadding,
                    onEvent = onEvent,
                )
            }
        }
    }
}

@Composable
private fun ReadyContent(
    text: String,
    accountCount: Int,
    revealed: Boolean,
    copied: Boolean,
    contentPadding: PaddingValues,
    onEvent: (Event) -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(BoltTheme.spacings.double)
            .padding(contentPadding)
            .verticalScroll(rememberScrollState()),
    ) {
        TextTitleLarge(text = stringResource(R.string.migration_qrcode_transfer_text_title))
        Spacer(modifier = Modifier.height(BoltTheme.spacings.double))

        TransferGuideIllustration()
        Spacer(modifier = Modifier.height(BoltTheme.spacings.triple))

        TextBodyMedium(text = stringResource(R.string.migration_qrcode_transfer_text_instructions))
        Spacer(modifier = Modifier.height(BoltTheme.spacings.triple))

        AccountsTextField(
            text = text,
            accountCount = accountCount,
            revealed = revealed,
            onToggleVisibility = { onEvent(Event.TextVisibilityToggled) },
        )
        Spacer(modifier = Modifier.height(BoltTheme.spacings.default))

        TextBodyMedium(
            text = stringResource(R.string.migration_qrcode_transfer_text_character_count, text.length),
            color = BoltTheme.colors.onSurfaceVariant,
            modifier = Modifier.testTag("CharacterCount"),
        )

        if (text.length > MAX_RECOMMENDED_LENGTH) {
            TextBodyMedium(
                text = stringResource(R.string.migration_qrcode_transfer_text_too_long),
                color = BoltTheme.colors.error,
                modifier = Modifier.testTag("TooLongText"),
            )
        }

        if (copied) {
            TextBodyMedium(
                text = stringResource(R.string.migration_qrcode_transfer_text_copied),
                modifier = Modifier.testTag("CopiedText"),
            )
        }

        ActionButtons(onEvent = onEvent)
    }
}

/** Notes of a password manager hold a limited number of characters, above this the text may not fit into one. */
private const val MAX_RECOMMENDED_LENGTH = 7000
