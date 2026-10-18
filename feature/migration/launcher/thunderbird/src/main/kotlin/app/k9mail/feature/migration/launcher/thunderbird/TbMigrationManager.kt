package app.k9mail.feature.migration.launcher.thunderbird

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import app.k9mail.feature.migration.launcher.api.MigrationManager
import app.k9mail.feature.migration.qrcode.ui.QrCodeScannerActivityContract
import app.k9mail.feature.migration.qrcode.ui.send.TransferSendActivity

internal class TbMigrationManager : MigrationManager {
    override fun isFeatureIncluded() = true

    override fun getQrCodeActivityResultContract(): ActivityResultContract<Unit, Uri?> {
        return QrCodeScannerActivityContract()
    }

    override fun createTransferSendIntent(context: Context): Intent {
        return TransferSendActivity.createIntent(context)
    }
}
