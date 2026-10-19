package app.k9mail.feature.migration.qrcode.transfer.hotspot

import android.Manifest.permission.CHANGE_NETWORK_STATE
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import net.thunderbird.legacy.logging.Log

private const val CONNECT_TIMEOUT_MILLIS = 60_000

/**
 * Joins the local-only hotspot of the sending device with the connectivity service, so the transfer container is
 * fetched over that network.
 *
 * Only available from Android 10 on, older devices get [UnsupportedHotspotNetworkConnector].
 */
@RequiresApi(Build.VERSION_CODES.Q)
internal class AndroidHotspotNetworkConnector(
    private val connectivityManager: ConnectivityManager,
) : HotspotNetworkConnector {
    /** Joins the hotspot. The caller holds the network state permission, it's a normal one. */
    @RequiresPermission(CHANGE_NETWORK_STATE)
    override suspend fun connect(ssid: String, passphrase: String): HotspotConnectResult {
        return suspendCancellableCoroutine { continuation ->
            val callback = hotspotCallback(continuation)
            continuation.invokeOnCancellation {
                unregister(callback)
            }

            val requested = requestNetwork(ssid = ssid, passphrase = passphrase, callback = callback)
            if (!requested) {
                unregister(callback)
                if (continuation.isActive) continuation.resume(HotspotConnectResult.Failed)
            }
        }
    }

    private fun hotspotCallback(
        continuation: CancellableContinuation<HotspotConnectResult>,
    ): ConnectivityManager.NetworkCallback {
        return object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // The hotspot can also be reported long after the transfer was given up.
                if (continuation.isActive) {
                    continuation.resume(
                        HotspotConnectResult.Connected(
                            socketFactory = network.socketFactory,
                            release = { unregister(this) },
                        ),
                    )
                }
            }

            override fun onUnavailable() {
                Log.d("The hotspot of the sending device couldn't be joined")
                unregister(this)
                if (continuation.isActive) continuation.resume(HotspotConnectResult.Failed)
            }
        }
    }

    private fun requestNetwork(
        ssid: String,
        passphrase: String,
        callback: ConnectivityManager.NetworkCallback,
    ): Boolean {
        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .setWpa2Passphrase(passphrase)
            .build()

        // The hotspot of the sending device has no internet, so the request mustn't ask for that capability.
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()

        return try {
            connectivityManager.requestNetwork(request, callback, CONNECT_TIMEOUT_MILLIS)
            true
        } catch (_: SecurityException) {
            Log.d("Failed to join the hotspot of the transfer without the required permission")
            false
        }
    }

    private fun unregister(callback: ConnectivityManager.NetworkCallback) {
        try {
            connectivityManager.unregisterNetworkCallback(callback)
        } catch (_: IllegalArgumentException) {
            // The system unregistered the request itself, e.g. when it timed out.
        }
    }
}
