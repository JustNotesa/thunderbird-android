package com.fsck.k9.ui.compose.richtext.bar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import net.thunderbird.components.ui.bolt.atom.DropdownMenu
import net.thunderbird.components.ui.bolt.atom.DropdownMenuItem
import net.thunderbird.components.ui.bolt.theme.BoltTheme

private const val SWATCHES_PER_ROW = 5
private const val OPAQUE = 0xFF000000.toInt()
private val SWATCH_SIZE = 34.dp
private val SWATCH_BORDER = 2.dp

/**
 * A button of the format bar that opens a grid of [colors] to give the text, plus an entry that takes the colour away.
 */
@Composable
internal fun ColorMenuButton(
    icon: ImageVector,
    descriptionRes: Int,
    colors: ImmutableList<NamedColor>,
    removeRes: Int,
    selected: Int?,
    onSelect: (Int?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val select = { color: Int? ->
        expanded = false
        onSelect(color)
    }

    Box {
        FormatButton(
            icon = icon,
            descriptionRes = descriptionRes,
            onClick = { expanded = true },
            isOn = selected != null,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Column(
                modifier = Modifier.padding(horizontal = BoltTheme.spacings.double),
                verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.default),
            ) {
                for (row in colors.chunked(SWATCHES_PER_ROW)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(BoltTheme.spacings.default)) {
                        row.forEach { namedColor ->
                            ColorSwatch(namedColor, isSelected = namedColor.color == selected, onClick = select)
                        }
                    }
                }
            }
            DropdownMenuItem(text = stringResource(removeRes), onClick = { select(null) })
        }
    }
}

@Composable
private fun ColorSwatch(namedColor: NamedColor, isSelected: Boolean, onClick: (Int) -> Unit) {
    val name = stringResource(namedColor.nameRes)
    val borderColor = if (isSelected) BoltTheme.colors.primary else BoltTheme.colors.outlineVariant

    Box(
        modifier = Modifier
            .size(SWATCH_SIZE)
            .clip(CircleShape)
            .background(Color(OPAQUE or namedColor.color))
            .border(SWATCH_BORDER, borderColor, CircleShape)
            .clickable { onClick(namedColor.color) }
            .semantics {
                contentDescription = name
                selected = isSelected
            },
    )
}
