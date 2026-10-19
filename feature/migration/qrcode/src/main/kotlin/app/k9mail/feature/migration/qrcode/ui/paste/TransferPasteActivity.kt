package app.k9mail.feature.migration.qrcode.ui.paste

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import com.fsck.k9.ui.base.BaseActivity
import net.thunderbird.core.ui.theme.api.FeatureThemeProvider
import org.koin.android.ext.android.inject

class TransferPasteActivity : BaseActivity() {
    private val themeProvider: FeatureThemeProvider by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The field holds the accounts of another device, they shouldn't end up in a screenshot or a screen recording.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        setContent {
            themeProvider.WithTheme {
                TransferPasteScreen(
                    finishWithResult = ::finishWithResult,
                    finish = ::finish,
                )
            }
        }
    }

    private fun finishWithResult(result: Uri) {
        val resultIntent = Intent().apply {
            data = result
        }
        setResult(RESULT_OK, resultIntent)

        finish()
    }
}
