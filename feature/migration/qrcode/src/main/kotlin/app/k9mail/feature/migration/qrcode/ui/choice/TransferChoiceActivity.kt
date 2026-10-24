package app.k9mail.feature.migration.qrcode.ui.choice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import app.k9mail.feature.migration.qrcode.ui.send.TransferSendActivity
import app.k9mail.feature.migration.qrcode.ui.text.TransferTextActivity
import com.fsck.k9.ui.base.BaseActivity
import net.thunderbird.core.ui.theme.api.FeatureThemeProvider
import org.koin.android.ext.android.inject

class TransferChoiceActivity : BaseActivity() {
    private val themeProvider: FeatureThemeProvider by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            themeProvider.WithTheme {
                TransferChoiceContent(
                    onQrCodeClick = ::startTransferSend,
                    onPasswordManagerClick = ::startTransferText,
                )
            }
        }
    }

    private fun startTransferSend() {
        startActivity(TransferSendActivity.createIntent(this))
    }

    private fun startTransferText() {
        startActivity(TransferTextActivity.createIntent(this))
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, TransferChoiceActivity::class.java)
        }
    }
}
