package app.k9mail.feature.migration.qrcode.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Keeps the content of the screen out of screenshots and screen recordings while this effect is composed. Use it only
 * where a secret is visible, so the rest of the screen stays screenshotable.
 */
@Composable
internal fun SecureContentEffect() {
    val context = LocalContext.current
    val window = remember(context) { context.findActivity()?.window }

    DisposableEffect(window) {
        // A flag that was set before this effect must stay when the effect leaves the composition.
        val wasSecure = window?.isSecure() == true
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        onDispose {
            if (!wasSecure) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}

/** Whether the window is excluded from screenshots and screen recordings. */
private fun Window.isSecure(): Boolean {
    return attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
}

/** The activity the context belongs to, or null when the context doesn't belong to one. */
private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
