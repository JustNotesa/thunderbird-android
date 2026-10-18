package app.k9mail.feature.migration.qrcode.ui.text

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import app.k9mail.feature.migration.qrcode.ui.text.TransferTextContract.Effect
import app.k9mail.feature.migration.qrcode.ui.text.TransferTextContract.Event
import net.thunderbird.core.ui.contract.mvi.observe
import org.koin.compose.viewmodel.koinViewModel

private const val CLIP_LABEL = "Thunderbird accounts"

@Composable
internal fun TransferTextScreen(
    finish: () -> Unit,
    viewModel: TransferTextViewModel = koinViewModel<TransferTextViewModel>(),
) {
    val context = LocalContext.current

    val (state, dispatch) = viewModel.observe { effect ->
        when (effect) {
            is Effect.CopyToClipboard -> context.copyToClipboard(effect.text)
            Effect.Close -> finish()
        }
    }

    LaunchedEffect(key1 = Unit) {
        dispatch(Event.StartScreen)
    }

    TransferTextContent(
        state = state.value,
        onEvent = dispatch,
    )
}

/**
 * Copies the accounts to the clipboard. The clip is marked as sensitive, so Android doesn't show the accounts in the
 * clipboard preview and doesn't offer them to the clipboard history.
 */
private fun Context.copyToClipboard(text: String) {
    val clip = ClipData.newPlainText(CLIP_LABEL, text)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(sensitiveExtraKey(), true)
        }
    }

    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
}

/**
 * The name of the extra that marks a clip as sensitive. It is documented as [ClipDescription.EXTRA_IS_SENSITIVE] from
 * Android 13 on, before that the constant doesn't exist yet and the name it will get is used.
 */
private fun sensitiveExtraKey(): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ClipDescription.EXTRA_IS_SENSITIVE
    } else {
        "android.content.extra.IS_SENSITIVE"
    }
}
