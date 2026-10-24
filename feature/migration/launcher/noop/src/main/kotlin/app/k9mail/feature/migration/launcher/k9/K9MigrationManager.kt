package app.k9mail.feature.migration.launcher.k9

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import app.k9mail.feature.migration.launcher.api.MigrationManager
import app.k9mail.feature.migration.qrcode.ui.QrCodeScannerActivityContract
import app.k9mail.feature.migration.qrcode.ui.choice.TransferChoiceActivity
import app.k9mail.feature.migration.qrcode.ui.paste.TransferPasteActivityContract

class K9MigrationManager : MigrationManager {
    override fun isFeatureIncluded(): Boolean = false

    override fun getQrCodeActivityResultContract(): ActivityResultContract<Unit, Uri?> =
        QrCodeScannerActivityContract()

    override fun getTransferPasteActivityResultContract(): ActivityResultContract<Unit, Uri?> =
        TransferPasteActivityContract()

    override fun createTransferChoiceIntent(context: Context): Intent {
        return TransferChoiceActivity.createIntent(context)
    }
}
