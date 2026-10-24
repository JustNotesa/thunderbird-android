package app.k9mail.feature.migration.qrcode.ui.text

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import com.fsck.k9.ui.base.BaseActivity
import net.thunderbird.core.ui.theme.api.FeatureThemeProvider
import org.koin.android.ext.android.inject

class TransferTextActivity : BaseActivity() {
    private val themeProvider: FeatureThemeProvider by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            themeProvider.WithTheme {
                TransferTextScreen(finish = ::finish)
            }
        }
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, TransferTextActivity::class.java)
        }
    }
}
