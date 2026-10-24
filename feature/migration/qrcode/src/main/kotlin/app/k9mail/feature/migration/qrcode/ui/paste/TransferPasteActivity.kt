package app.k9mail.feature.migration.qrcode.ui.paste

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import com.fsck.k9.ui.base.BaseActivity
import net.thunderbird.core.ui.theme.api.FeatureThemeProvider
import org.koin.android.ext.android.inject

class TransferPasteActivity : BaseActivity() {
    private val themeProvider: FeatureThemeProvider by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
