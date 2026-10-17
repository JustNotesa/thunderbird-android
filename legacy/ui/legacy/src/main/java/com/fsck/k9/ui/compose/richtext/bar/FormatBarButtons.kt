package com.fsck.k9.ui.compose.richtext.bar

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import kotlinx.collections.immutable.ImmutableList
import net.thunderbird.components.ui.bolt.atom.DropdownMenu
import net.thunderbird.components.ui.bolt.atom.DropdownMenuItem
import net.thunderbird.components.ui.bolt.atom.button.ButtonIcon
import net.thunderbird.components.ui.bolt.atom.button.ButtonIconDefaults
import net.thunderbird.components.ui.bolt.atom.icon.Icons
import net.thunderbird.components.ui.bolt.theme.BoltTheme

/**
 * A button of the format bar. A format that is switched on looks pressed.
 */
@Composable
internal fun FormatButton(
    icon: ImageVector,
    descriptionRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isOn: Boolean = false,
    enabled: Boolean = true,
) {
    val containerColor = if (isOn) BoltTheme.colors.secondaryContainer else Color.Transparent
    ButtonIcon(
        onClick = onClick,
        imageVector = icon,
        modifier = modifier.semantics { selected = isOn },
        enabled = enabled,
        contentDescription = stringResource(descriptionRes),
        colors = ButtonIconDefaults.buttonIconColors(containerColor = containerColor),
    )
}

/**
 * An option of a menu of the format bar, with the string resource of its name.
 */
internal data class MenuOption<T>(val value: T, val nameRes: Int)

/**
 * A button of the format bar that opens a list of options, e.g. the sizes. The option in effect is marked.
 */
@Composable
internal fun <T> OptionMenuButton(
    icon: ImageVector,
    descriptionRes: Int,
    options: ImmutableList<MenuOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    isOn: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        FormatButton(icon = icon, descriptionRes = descriptionRes, onClick = { expanded = true }, isOn = isOn)
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (option in options) {
                DropdownMenuItem(
                    text = stringResource(option.nameRes),
                    onClick = {
                        expanded = false
                        onSelect(option.value)
                    },
                    trailingIcon = if (option.value == selected) Icons.Outlined.Check else null,
                )
            }
        }
    }
}
