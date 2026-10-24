package app.k9mail.feature.migration.qrcode.ui.text

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.k9mail.feature.migration.qrcode.R
import net.thunderbird.components.ui.bolt.atom.button.ButtonIcon
import net.thunderbird.components.ui.bolt.atom.icon.Icons
import net.thunderbird.components.ui.bolt.atom.textfield.TextFieldOutlined

/**
 * The accounts with their passwords. They are hidden until the user asks for them, then the field names how much
 * account data it holds instead of showing it to whoever is looking over the user's shoulder. Once the user asks for
 * them, the accounts are shown readable.
 */
@Composable
internal fun AccountsTextField(
    text: String,
    accountCount: Int,
    revealed: Boolean,
    onToggleVisibility: () -> Unit,
) {
    val hiddenText = stringResource(hiddenTextResource(accountCount), accountCount, text.length)

    TextFieldOutlined(
        value = if (revealed) text else hiddenText,
        onValueChange = { },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = MAX_TEXT_FIELD_HEIGHT)
            .testTag("AccountsTextField"),
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

private val MAX_TEXT_FIELD_HEIGHT = 160.dp
