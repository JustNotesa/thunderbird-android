package app.k9mail.feature.migration.qrcode.ui.send

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.k9mail.feature.migration.qrcode.ui.auth.TransferAuthorizationScreen
import app.k9mail.feature.migration.qrcode.ui.send.TransferSendContract.Effect
import app.k9mail.feature.migration.qrcode.ui.send.TransferSendContract.Event
import net.thunderbird.core.ui.contract.mvi.observe
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun TransferSendScreen(
    finish: () -> Unit,
    viewModel: TransferSendViewModel = koinViewModel<TransferSendViewModel>(),
) {
    val context = LocalContext.current

    val hotspotPermissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        viewModel.event(Event.HotspotPermissionResult(granted))
    }

    val (state, dispatch) = viewModel.observe { effect ->
        when (effect) {
            Effect.Close -> finish()

            Effect.RequestHotspotPermission -> context.requestHotspotPermission(
                launcher = hotspotPermissionLauncher,
                onResult = { granted -> viewModel.event(Event.HotspotPermissionResult(granted)) },
            )
        }
    }

    // The QR code holds the key of the transfer, so it is only prepared once the user confirmed it's them.
    TransferAuthorizationScreen(finish = finish) {
        LaunchedEffect(key1 = Unit) {
            dispatch(Event.StartScreen)
        }

        TransferSendContent(
            step = state.value.step,
            onEvent = dispatch,
        )
    }
}

/**
 * Asks the user for the permission the local-only hotspot needs, unless this Android version doesn't need one or the
 * user has already allowed it. In both cases the hotspot can be started right away.
 */
private fun Context.requestHotspotPermission(
    launcher: ManagedActivityResultLauncher<String, Boolean>,
    onResult: (Boolean) -> Unit,
) {
    val permission = hotspotPermission()

    if (permission != null &&
        ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
    ) {
        launcher.launch(permission)
    } else {
        onResult(true)
    }
}

/**
 * The permission the local-only hotspot needs: the nearby devices permission from Android 13 on, the location
 * permission up to Android 12. Older versions don't ask for any permission at all.
 */
private fun hotspotPermission(): String? {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> Manifest.permission.NEARBY_WIFI_DEVICES
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> Manifest.permission.ACCESS_FINE_LOCATION
        else -> null
    }
}
