package app.k9mail.feature.migration.launcher.api

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

interface MigrationManager {
    /**
     * Returns whether the features to import accounts by scanning QR codes and importing directly from another
     * app are included in the app.
     */
    fun isFeatureIncluded(): Boolean

    /**
     * Returns an [ActivityResultContract] that can be used to start the QR code scanner. In case of success a
     * content: URI to the account settings in the XML format supported by `SettingsImporter` is returned.
     */
    fun getQrCodeActivityResultContract(): ActivityResultContract<Unit, Uri?>

    /**
     * Returns an [ActivityResultContract] that can be used to import accounts the user pasted from a password
     * manager. In case of success a content: URI to the account settings in the XML format supported by
     * `SettingsImporter` is returned.
     */
    fun getTransferPasteActivityResultContract(): ActivityResultContract<Unit, Uri?>

    /**
     * Returns an [Intent] that starts the screen to transfer the accounts of this device to another device, or
     * `null` if the feature isn't included.
     */
    fun createTransferSendIntent(context: Context): Intent?
}
