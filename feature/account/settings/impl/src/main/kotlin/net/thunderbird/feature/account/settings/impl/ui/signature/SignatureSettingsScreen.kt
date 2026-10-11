package net.thunderbird.feature.account.settings.impl.ui.signature

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import net.thunderbird.core.ui.contract.mvi.observe
import net.thunderbird.feature.account.AccountId
import net.thunderbird.feature.account.settings.impl.ui.signature.SignatureSettingsContract.Effect
import net.thunderbird.feature.account.settings.impl.ui.signature.SignatureSettingsContract.Event
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun SignatureSettingsScreen(
    accountId: AccountId,
    onBack: () -> Unit,
    viewModel: SignatureSettingsContract.ViewModel = koinViewModel<SignatureSettingsViewModel> {
        parametersOf(accountId)
    },
) {
    val (state, dispatch) = viewModel.observe { effect ->
        when (effect) {
            Effect.NavigateBack -> onBack()
        }
    }

    BackHandler { dispatch(Event.OnBackPressed) }

    SignatureSettingsContent(
        state = state.value,
        onEvent = { dispatch(it) },
    )
}
