package app.k9mail.feature.migration.qrcode.ui.send

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.k9mail.feature.migration.qrcode.ui.send.TransferSendContract.Effect
import app.k9mail.feature.migration.qrcode.ui.send.TransferSendContract.Event
import net.thunderbird.core.ui.contract.mvi.observe
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun TransferSendScreen(
    finish: () -> Unit,
    viewModel: TransferSendViewModel = koinViewModel<TransferSendViewModel>(),
) {
    val (state, dispatch) = viewModel.observe { effect ->
        when (effect) {
            Effect.Close -> finish()
        }
    }

    LaunchedEffect(key1 = Unit) {
        dispatch(Event.StartScreen)
    }

    TransferSendContent(
        step = state.value.step,
        onEvent = dispatch,
    )
}
