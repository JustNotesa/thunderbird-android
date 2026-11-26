package com.fsck.k9.activity.compose.signature

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import net.thunderbird.core.ui.theme.api.FeatureThemeProvider
import net.thunderbird.feature.mail.message.composer.signature.SignaturePasteProcessor

/**
 * Shows the sheets of the signature in a [ComposeView] and reports what the user chose to [listener].
 *
 * Showing a sheet closes the one that is open, and every choice closes the sheet before it is reported.
 */
class SignatureSheetController(
    composeView: ComposeView,
    private val listener: SignatureSheetListener,
    themeProvider: FeatureThemeProvider,
    pasteProcessor: SignaturePasteProcessor,
) {
    private var sheet by mutableStateOf<SignatureSheet?>(null)

    private val closingListener = object : SignatureSheetListener {
        override fun onDefaultChosen() = closeThen { listener.onDefaultChosen() }
        override fun onSavedChosen(id: String) = closeThen { listener.onSavedChosen(id) }
        override fun onNoneChosen() = closeThen { listener.onNoneChosen() }
        override fun onCreateRequested() = closeThen { listener.onCreateRequested() }
        override fun onEditSavedRequested(id: String) = closeThen { listener.onEditSavedRequested(id) }
        override fun onEditHtmlForMessageRequested() = closeThen { listener.onEditHtmlForMessageRequested() }
        override fun onChooseOtherRequested() = closeThen { listener.onChooseOtherRequested() }
        override fun onEditAppliedSavedRequested() = closeThen { listener.onEditAppliedSavedRequested() }
        override fun onHtmlEdited(html: String) = closeThen { listener.onHtmlEdited(html) }
        override fun onSheetDismissed() = closeThen { listener.onSheetDismissed() }
    }

    init {
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        composeView.setContent {
            themeProvider.WithTheme {
                SignatureSheetHost(sheet = sheet, listener = closingListener, pasteProcessor = pasteProcessor)
            }
        }
    }

    fun show(sheet: SignatureSheet) {
        this.sheet = sheet
    }

    private fun closeThen(action: () -> Unit) {
        sheet = null
        action()
    }
}
