package app.k9mail.feature.migration.qrcode.transfer.hotspot

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.NEARBY_WIFI_DEVICES
import android.net.wifi.SoftApConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.LocalOnlyHotspotReservation
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import net.thunderbird.legacy.logging.Log

private const val ADDRESS_POLL_INTERVAL_MILLIS = 100L
private const val ADDRESS_POLL_ATTEMPTS = 30
private const val QUOTE = '"'

/**
 * Runs the local-only hotspot of the system Wi-Fi service, so a receiving device can fetch the transfer container.
 *
 * Only available from Android 8 on, older devices get [UnsupportedHotspotController].
 */
@RequiresApi(Build.VERSION_CODES.O)
internal class AndroidHotspotController(
    private val wifiManager: WifiManager,
    private val interfaceAddresses: () -> Set<Inet4Address> = ::currentInterfaceAddresses,
) : HotspotController {
    @Volatile
    private var reservation: LocalOnlyHotspotReservation? = null

    /** Starts the hotspot. The caller holds the runtime permission, the transfer screen asks for it beforehand. */
    @RequiresPermission(anyOf = [NEARBY_WIFI_DEVICES, ACCESS_FINE_LOCATION])
    override suspend fun start(): HotspotStartResult {
        val before = interfaceAddresses()
        val started = requestHotspot()

        return if (started == null) {
            HotspotStartResult.Failed
        } else {
            startedResult(started = started, before = before)
        }
    }

    /** Stops the hotspot. Has no effect when it isn't running. */
    override fun stop() {
        val running = reservation ?: return
        reservation = null
        running.close()
    }

    private suspend fun startedResult(
        started: LocalOnlyHotspotReservation,
        before: Set<Inet4Address>,
    ): HotspotStartResult {
        val credentials = credentialsOf(started)

        return if (credentials == null) {
            started.close()
            HotspotStartResult.Unsupported
        } else {
            val result = awaitAddress(started = started, credentials = credentials, before = before)
            if (result is HotspotStartResult.Started) {
                reservation = started
            }
            result
        }
    }

    private suspend fun awaitAddress(
        started: LocalOnlyHotspotReservation,
        credentials: HotspotCredentials,
        before: Set<Inet4Address>,
    ): HotspotStartResult {
        repeat(ADDRESS_POLL_ATTEMPTS) {
            val address = findNewAddress(before = before, after = interfaceAddresses())
            if (address != null) {
                return HotspotStartResult.Started(
                    ssid = credentials.ssid,
                    passphrase = credentials.passphrase,
                    address = address,
                )
            }
            delay(ADDRESS_POLL_INTERVAL_MILLIS)
        }

        started.close()
        return HotspotStartResult.Failed
    }

    private suspend fun requestHotspot(): LocalOnlyHotspotReservation? {
        return try {
            suspendCancellableCoroutine { continuation ->
                var lateReservation: LocalOnlyHotspotReservation? = null

                continuation.invokeOnCancellation {
                    // The hotspot can still come up after the transfer was given up, then it has to go away again.
                    lateReservation?.close()
                }

                val callback = object : WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(reservation: LocalOnlyHotspotReservation) {
                        if (continuation.isActive) {
                            lateReservation = reservation
                            continuation.resume(reservation)
                        } else {
                            reservation.close()
                        }
                    }

                    // Both can also be reported long after the hotspot was started, e.g. when the system stops it.
                    override fun onFailed(reason: Int) = resumeIfActive()

                    override fun onStopped() = resumeIfActive()

                    private fun resumeIfActive() {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }

                wifiManager.startLocalOnlyHotspot(callback, Handler(Looper.getMainLooper()))
            }
        } catch (_: SecurityException) {
            Log.d("Failed to start the hotspot of the transfer without the required permission")
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun credentialsOf(reservation: LocalOnlyHotspotReservation): HotspotCredentials? {
        val (ssid, passphrase) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            softApCredentialsOf(reservation)
        } else {
            // Up to Android 11 the reservation only hands out the deprecated WifiConfiguration.
            val configuration = reservation.wifiConfiguration
            configuration?.SSID to configuration?.preSharedKey
        }

        val name = ssid.unquoted()

        return if (name.isNotEmpty() && !passphrase.isNullOrBlank()) {
            HotspotCredentials(ssid = name, passphrase = passphrase)
        } else {
            null
        }
    }

    /** Reads the credentials from the configuration of Android 12, which knows the network name from Android 13 on. */
    @RequiresApi(Build.VERSION_CODES.S)
    @Suppress("DEPRECATION")
    private fun softApCredentialsOf(reservation: LocalOnlyHotspotReservation): Pair<String?, String?> {
        val configuration = reservation.softApConfiguration
        val ssid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            wifiSsidOf(configuration)
        } else {
            configuration.ssid
        }

        return ssid to configuration.passphrase
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun wifiSsidOf(configuration: SoftApConfiguration): String? = configuration.wifiSsid?.toString()
}

internal data class HotspotCredentials(
    val ssid: String,
    val passphrase: String,
)

/** Reads the IPv4 addresses of all network interfaces that are up, to spot the one of the hotspot interface. */
internal fun currentInterfaceAddresses(): Set<Inet4Address> {
    val interfaces = try {
        NetworkInterface.getNetworkInterfaces().toList()
    } catch (_: SocketException) {
        Log.d("Failed to read the network interfaces for the hotspot of the transfer")
        return emptySet()
    }

    return interfaces
        .filter { it.isUp && !it.isLoopback }
        .flatMap { networkInterface -> networkInterface.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .toSet()
}

/** Finds the address of the hotspot: the first site-local address that appeared while the hotspot started. */
internal fun findNewAddress(before: Set<Inet4Address>, after: Set<Inet4Address>): Inet4Address? {
    return after.asSequence()
        .filterNot { it in before }
        .firstOrNull { it.isSiteLocalAddress && !it.isLoopbackAddress }
}

private fun String?.unquoted(): String = this?.trim { it == QUOTE } ?: ""
