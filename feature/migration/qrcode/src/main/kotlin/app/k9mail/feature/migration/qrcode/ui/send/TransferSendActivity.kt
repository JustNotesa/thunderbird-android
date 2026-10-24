package app.k9mail.feature.migration.qrcode.ui.send

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import app.k9mail.feature.migration.qrcode.ui.text.TransferTextActivity
import com.fsck.k9.ui.base.BaseActivity
import net.thunderbird.core.ui.theme.api.FeatureThemeProvider
import org.koin.android.ext.android.inject

class TransferSendActivity : BaseActivity() {
    private val themeProvider: FeatureThemeProvider by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The QR code contains the key of the transfer, it shouldn't end up in a screenshot or a screen recording.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        setContent {
            themeProvider.WithTheme {
                TransferSendScreen(
                    finish = ::finish,
                    onUsePasswordManager = ::startTransferText,
                )
            }
        }
    }

    /**
     * Transfers the accounts with the password manager instead of the QR code, the other way to transfer them.
     */
    private fun startTransferText() {
        startActivity(TransferTextActivity.createIntent(this))
        finish()
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, TransferSendActivity::class.java)
        }
    }
}
