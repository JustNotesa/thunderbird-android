package net.thunderbird.core.preference.network

import net.thunderbird.core.preference.BackgroundOps

val NETWORK_SETTINGS_DEFAULT_BACKGROUND_OPS = BackgroundOps.ALWAYS
const val NETWORK_SETTINGS_DEFAULT_OFFLINE_DOWNLOAD = false
const val NETWORK_SETTINGS_DEFAULT_OFFLINE_DOWNLOAD_ONLY_WHILE_CHARGING = false
const val NETWORK_SETTINGS_DEFAULT_OFFLINE_STORAGE_LIMIT_MEGABYTES = 0
const val NETWORK_SETTINGS_DEFAULT_OFFLINE_KEEP_FREE_MEGABYTES = 1024

data class NetworkSettings(
    val backgroundOps: BackgroundOps = NETWORK_SETTINGS_DEFAULT_BACKGROUND_OPS,
    /**
     * Whether all messages including attachments should be downloaded in the background.
     */
    val offlineDownload: Boolean = NETWORK_SETTINGS_DEFAULT_OFFLINE_DOWNLOAD,
    /**
     * Whether the background download should only be performed while the device is charging.
     */
    val offlineDownloadOnlyWhileCharging: Boolean = NETWORK_SETTINGS_DEFAULT_OFFLINE_DOWNLOAD_ONLY_WHILE_CHARGING,
    /**
     * The maximum amount of storage in megabytes the messages of all accounts together may use.
     *
     * A value of 0 means that no limit is applied.
     */
    val offlineStorageLimitMegabytes: Int = NETWORK_SETTINGS_DEFAULT_OFFLINE_STORAGE_LIMIT_MEGABYTES,
    /**
     * The minimum amount of free storage in megabytes that needs to be available on the device.
     */
    val offlineKeepFreeMegabytes: Int = NETWORK_SETTINGS_DEFAULT_OFFLINE_KEEP_FREE_MEGABYTES,
)
