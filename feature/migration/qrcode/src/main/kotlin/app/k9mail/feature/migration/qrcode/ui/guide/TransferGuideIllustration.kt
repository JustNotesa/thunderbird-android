package app.k9mail.feature.migration.qrcode.ui.guide

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.k9mail.feature.migration.qrcode.R
import net.thunderbird.components.ui.bolt.atom.text.TextLabelSmall
import net.thunderbird.components.ui.bolt.theme.BoltTheme

/**
 * Illustration of the way the accounts travel through the vault of the password manager, from the computer that has
 * the accounts set up to the phone that imports them. The sending device shows the same illustration.
 */
@Composable
internal fun TransferGuideIllustration(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.migration_qrcode_transfer_guide_description)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = MAX_GUIDE_WIDTH)
                .fillMaxWidth()
                .aspectRatio(GUIDE_ASPECT_RATIO)
                .testTag("TransferGuideIllustration")
                .clearAndSetSemantics { contentDescription = description },
        ) {
            GuideImage(
                drawableId = R.drawable.migration_qrcode_transfer_guide_lines,
                color = BoltTheme.colors.onSurfaceVariant,
            )
            GuideImage(
                drawableId = R.drawable.migration_qrcode_transfer_guide_accent,
                color = BoltTheme.colors.primary,
            )
        }
        Spacer(modifier = Modifier.height(BoltTheme.spacings.default))

        GuideLabels(
            modifier = Modifier
                .widthIn(max = MAX_GUIDE_WIDTH)
                .fillMaxWidth()
                .testTag("TransferGuideLabels"),
        )
    }
}

/** The drawables are black, the colors of the theme are applied at runtime. */
@Composable
private fun GuideImage(@DrawableRes drawableId: Int, color: Color) {
    Image(
        painter = painterResource(drawableId),
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier = Modifier.fillMaxSize(),
    )
}

/** The labels of the three devices of the illustration, the group description of the drawing covers them. */
@Composable
private fun GuideLabels(modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier.clearAndSetSemantics { },
    ) {
        GuideLabel(
            text = stringResource(R.string.migration_qrcode_transfer_guide_label_computer),
            start = COMPUTER_LABEL_START,
            width = COMPUTER_LABEL_WIDTH,
        )
        GuideLabel(
            text = stringResource(R.string.migration_qrcode_transfer_guide_label_password_manager),
            start = VAULT_LABEL_START,
            width = VAULT_LABEL_WIDTH,
        )
        GuideLabel(
            text = stringResource(R.string.migration_qrcode_transfer_guide_label_thunderbird),
            start = PHONE_LABEL_START,
            width = PHONE_LABEL_WIDTH,
        )
    }
}

/** Places a label with its centre at the given share of the width of the illustration. */
@Composable
private fun BoxWithConstraintsScope.GuideLabel(text: String, start: Float, width: Float) {
    TextLabelSmall(
        text = text,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier
            .offset(x = maxWidth * start)
            .width(maxWidth * width),
    )
}

private val MAX_GUIDE_WIDTH = 400.dp

// Size of the viewport of the guide drawables.
private const val GUIDE_VIEWPORT_WIDTH = 560f
private const val GUIDE_VIEWPORT_HEIGHT = 172f
private const val GUIDE_ASPECT_RATIO = GUIDE_VIEWPORT_WIDTH / GUIDE_VIEWPORT_HEIGHT
private const val COMPUTER_LABEL_START = 0f
private const val COMPUTER_LABEL_WIDTH = 0.3f
private const val VAULT_LABEL_START = 0.39f
private const val VAULT_LABEL_WIDTH = 0.3f
private const val PHONE_LABEL_START = 0.78f
private const val PHONE_LABEL_WIDTH = 0.22f
