package net.thunderbird.core.preference.network

import net.thunderbird.core.preference.PreferenceManager

enum class NetworkSettingKey(val value: String) {
    BackgroundOperations("backgroundOperations"),
    OfflineDownload("offlineDownload"),
    OfflineDownloadOnlyWhileCharging("offlineDownloadOnlyWhileCharging"),
    OfflineStorageLimit("offlineStorageLimitMegabytes"),
    OfflineKeepFree("offlineKeepFreeMegabytes"),
}

interface NetworkSettingsPreferenceManager : PreferenceManager<NetworkSettings>
