package net.thunderbird.feature.account.settings.impl.ui.signature

import android.webkit.WebView
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import net.thunderbird.components.ui.bolt.atom.text.TextBodyLarge
import net.thunderbird.components.ui.bolt.theme.BoltTheme
import android.graphics.Color as AndroidColor

private const val RGB_MASK = 0xFFFFFF

/**
 * Renders the sanitized HTML of a signature the way a mail client shows it.
 */
@Composable
internal fun SignatureHtmlPreview(
    html: String,
    modifier: Modifier = Modifier,
) {
    if (LocalInspectionMode.current) {
        TextBodyLarge(text = html, modifier = modifier)
        return
    }

    val document = buildPreviewDocument(html = html, textColor = BoltTheme.colors.onSurface)

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                // The signature is the user's own content, but nothing in a preview needs scripts or files.
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                setBackgroundColor(AndroidColor.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
            }
        },
        update = { webView ->
            if (webView.tag != document) {
                webView.tag = document
                webView.loadDataWithBaseURL(null, document, "text/html", "utf-8", null)
            }
        },
        onRelease = { webView -> webView.destroy() },
        modifier = modifier
            .heightIn(min = 1.dp)
            .wrapContentHeight(),
    )
}

private fun buildPreviewDocument(html: String, textColor: Color): String {
    val cssColor = "#%06X".format(textColor.toArgb() and RGB_MASK)

    return "<!DOCTYPE html><html><head>" +
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
        "<style>body { margin: 0; color: $cssColor; font-family: sans-serif; }</style>" +
        "</head><body>$html</body></html>"
}
