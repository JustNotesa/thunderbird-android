package app.k9mail.feature.migration.launcher.thunderbird

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import app.k9mail.feature.migration.launcher.api.MigrationManager
import app.k9mail.feature.migration.qrcode.ui.QrCodeScannerActivityContract
import app.k9mail.feature.migration.qrcode.ui.paste.TransferPasteActivityContract
import app.k9mail.feature.migration.qrcode.ui.send.TransferSendActivity
import app.k9mail.feature.migration.qrcode.ui.text.TransferTextActivity

internal class TbMigrationManager : MigrationManager {
    override fun isFeatureIncluded() = true

    override fun getQrCodeActivityResultContract(): ActivityResultContract<Unit, Uri?> {
        return QrCodeScannerActivityContract()
    }

    override fun getTransferPasteActivityResultContract(): ActivityResultContract<Unit, Uri?> {
        return TransferPasteActivityContract()
    }

    override fun createTransferSendIntent(context: Context): Intent {
        return TransferSendActivity.createIntent(context)
    }

    override fun createTransferTextIntent(context: Context): Intent {
        return TransferTextActivity.createIntent(context)
    }
}
