package app.k9mail.feature.migration.qrcode.ui.paste

import android.os.Build
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.k9mail.feature.migration.qrcode.R
import app.k9mail.feature.migration.qrcode.ui.paste.TransferPasteContract.Event
import app.k9mail.feature.migration.qrcode.ui.paste.TransferPasteContract.State
import net.thunderbird.components.ui.bolt.atom.button.ButtonIcon
import net.thunderbird.components.ui.bolt.atom.icon.Icons
import net.thunderbird.components.ui.bolt.atom.textfield.TextFieldOutlined

/** Mask of a text that isn't the accounts of a transfer, so it doesn't show what the user pasted. */
internal const val MASKED_TEXT = "••••••••••••"

/** Whether the field holds the pasted text blurred, so a test can see what a tap on the field changes. */
internal val AccountsBlurredKey = SemanticsPropertyKey<Boolean>("AccountsBlurred")

/**
 * The pasted text holds the accounts with their passwords, so it stays hidden until the user asks for it. Text that
 * holds accounts of a transfer is described by their amount, any other text is masked. On the devices that can blur,
 * the text appears blurred until the user taps it, hiding it again starts over.
 */
@Composable
internal fun AccountsTextField(
    state: State,
    onEvent: (Event) -> Unit,
) {
    var blurred by rememberSaveable(state.revealed) { mutableStateOf(true) }
    val masked = state.text.isNotEmpty() && !state.revealed
    val blurredText = !masked && blurred && canBlurText()

    TextFieldOutlined(
        value = when {
            !masked -> state.text
            state.recognizedAccountCount != null -> recognizedText(accountCount = state.recognizedAccountCount)
            else -> MASKED_TEXT
        },
        onValueChange = { text -> onEvent(Event.TextChanged(text)) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = MAX_TEXT_FIELD_HEIGHT)
            .testTag("AccountsTextField")
            .blurredUntilTapped(blurred = blurredText) { blurred = false }
            .semantics { this[AccountsBlurredKey] = blurredText },
        label = stringResource(R.string.migration_qrcode_transfer_paste_text_field_label),
        trailingIcon = if (state.text.isEmpty()) {
            null
        } else {
            {
                ButtonIcon(
                    onClick = { onEvent(Event.TextVisibilityToggled) },
                    imageVector = if (state.revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = stringResource(toggleVisibilityResource(revealed = state.revealed)),
                    modifier = Modifier.testTag("TextVisibilityButton"),
                )
            }
        },
        hasError = state.error != null,
        isReadOnly = masked,
        isSingleLine = false,
    )
}

/** Blurs the field as long as [blurred] and reports a tap on it, which is how the user asks for readable text. */
@Composable
private fun Modifier.blurredUntilTapped(
    blurred: Boolean,
    onTap: () -> Unit,
): Modifier {
    val currentOnTap by rememberUpdatedState(onTap)

    return this
        .then(if (blurred) Modifier.blur(BLUR_RADIUS.dp) else Modifier)
        .pointerInput(blurred) {
            // The field keeps handling the tap itself, so nothing here consumes it.
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                if (up != null && blurred) {
                    currentOnTap()
                }
            }
        }
}

/** Android 12 and newer can blur, below that the text is readable as soon as it is shown. */
private fun canBlurText(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

/** The field tells how many accounts of a transfer the pasted text holds. */
@Composable
private fun recognizedText(accountCount: Int): String {
    val resource = if (accountCount == 1) {
        R.string.migration_qrcode_transfer_paste_recognized_one
    } else {
        R.string.migration_qrcode_transfer_paste_recognized
    }

    return stringResource(resource, accountCount)
}

private fun toggleVisibilityResource(revealed: Boolean): Int {
    return if (revealed) {
        R.string.migration_qrcode_transfer_text_hide_button
    } else {
        R.string.migration_qrcode_transfer_text_show_button
    }
}

private const val BLUR_RADIUS = 8
private val MAX_TEXT_FIELD_HEIGHT = 160.dp
