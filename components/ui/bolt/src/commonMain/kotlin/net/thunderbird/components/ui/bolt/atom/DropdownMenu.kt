package net.thunderbird.components.ui.bolt.atom

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import net.thunderbird.components.ui.bolt.atom.icon.Icon
import androidx.compose.material3.DropdownMenu as Material3DropdownMenu
import androidx.compose.material3.DropdownMenuItem as Material3DropdownMenuItem
import androidx.compose.material3.Text as Material3Text

/**
 * A menu that opens below the element it is placed next to, e.g. the options of a button in a toolbar.
 */
@Composable
fun DropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Material3DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        content = content,
    )
}

/**
 * An entry of a [DropdownMenu]. [trailingIcon] marks e.g. the entry that is currently chosen.
 */
@Composable
fun DropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
) {
    Material3DropdownMenuItem(
        text = { Material3Text(text = text) },
        onClick = onClick,
        modifier = modifier,
        leadingIcon = leadingIcon?.let { icon -> { Icon(imageVector = icon) } },
        trailingIcon = trailingIcon?.let { icon -> { Icon(imageVector = icon) } },
    )
}
