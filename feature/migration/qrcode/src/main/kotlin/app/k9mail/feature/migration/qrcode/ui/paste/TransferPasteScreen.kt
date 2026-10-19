package app.k9mail.feature.migration.qrcode.ui.paste

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.k9mail.feature.migration.qrcode.ui.paste.TransferPasteContract.Effect
import app.k9mail.feature.migration.qrcode.ui.paste.TransferPasteContract.Event
import net.thunderbird.core.ui.contract.mvi.observe
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun TransferPasteScreen(
    finishWithResult: (Uri) -> Unit,
    finish: () -> Unit,
    viewModel: TransferPasteViewModel = koinViewModel<TransferPasteViewModel>(),
) {
    val context = LocalContext.current

    val (state, dispatch) = viewModel.observe { effect ->
        when (effect) {
            is Effect.ReturnResult -> finishWithResult(effect.contentUri)
            Effect.Close -> finish()
            Effect.ClearClipboard -> context.clearClipboard()
        }
    }

    TransferPasteContent(
        state = state.value,
        onEvent = { event ->
            when (event) {
                // The clipboard belongs to Android and is only read here, the view model works on the text alone.
                is Event.PasteFromClipboardClicked -> dispatch(event.copy(clipboardText = context.readClipboardText()))

                else -> dispatch(event)
            }
        },
    )
}

/**
 * Reads the text of the clipboard, so the user doesn't have to paste it into the field themselves.
 */
private fun Context.readClipboardText(): String? {
    return getSystemService(ClipboardManager::class.java)
        ?.primaryClip
        ?.takeIf { clip -> clip.itemCount > 0 }
        ?.getItemAt(0)
        ?.coerceToText(this)
        ?.toString()
}

/**
 * Removes the accounts from the clipboard. Only Android 9 and newer offer to clear it, on older versions the text
 * stays on the clipboard until the user copies something else.
 */
private fun Context.clearClipboard() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        getSystemService(ClipboardManager::class.java)?.clearPrimaryClip()
    }
}
