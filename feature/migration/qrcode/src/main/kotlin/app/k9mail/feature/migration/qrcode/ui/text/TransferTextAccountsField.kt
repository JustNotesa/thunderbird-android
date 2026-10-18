package app.k9mail.feature.migration.qrcode.ui.text

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
import net.thunderbird.components.ui.bolt.atom.button.ButtonIcon
import net.thunderbird.components.ui.bolt.atom.icon.Icons
import net.thunderbird.components.ui.bolt.atom.textfield.TextFieldOutlined

/** Whether the field holds the accounts blurred, so a test can see what a tap on the field changes. */
internal val AccountsBlurredKey = SemanticsPropertyKey<Boolean>("AccountsBlurred")

/**
 * The accounts with their passwords. They are hidden until the user asks for them, then the field names how much
 * account data it holds instead of showing it to whoever is looking over the user's shoulder. On the devices that can
 * blur, the accounts appear blurred until the user taps them, hiding them again starts over.
 */
@Composable
internal fun AccountsTextField(
    text: String,
    accountCount: Int,
    revealed: Boolean,
    onToggleVisibility: () -> Unit,
) {
    var blurred by rememberSaveable(revealed) { mutableStateOf(true) }
    val blurredText = revealed && blurred && canBlurText()
    val hiddenText = stringResource(hiddenTextResource(accountCount), accountCount, text.length)

    TextFieldOutlined(
        value = if (revealed) text else hiddenText,
        onValueChange = { },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = MAX_TEXT_FIELD_HEIGHT)
            .testTag("AccountsTextField")
            .blurredUntilTapped(blurred = blurredText) { blurred = false }
            .semantics { this[AccountsBlurredKey] = blurredText },
        trailingIcon = {
            ButtonIcon(
                onClick = onToggleVisibility,
                imageVector = if (revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                contentDescription = stringResource(toggleVisibilityResource(revealed)),
                modifier = Modifier.testTag("TextVisibilityButton"),
            )
        },
        isReadOnly = true,
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

/** Android 12 and newer can blur, below that the accounts are readable as soon as they are shown. */
private fun canBlurText(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

private fun hiddenTextResource(accountCount: Int): Int {
    return if (accountCount == 1) {
        R.string.migration_qrcode_transfer_text_hidden_one
    } else {
        R.string.migration_qrcode_transfer_text_hidden
    }
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
