package net.thunderbird.core.preference.network

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import net.thunderbird.components.core.logging.Logger
import net.thunderbird.components.core.logging.testing.TestLogger
import net.thunderbird.core.preference.DefaultPreferenceChangeBroker
import net.thunderbird.core.preference.storage.InMemoryStorage
import net.thunderbird.core.preference.storage.Storage
import net.thunderbird.core.preference.storage.StorageEditor
import net.thunderbird.core.preference.storage.StorageProvider

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultNetworkSettingsPreferenceManagerTest {

    private val logger = TestLogger()

    @Test
    fun `save should write offline download settings to storage`() {
        val storageProvider = TestStorageProvider(logger)
        val preferenceManager = createPreferenceManager(storageProvider)

        preferenceManager.save(
            NetworkSettings(
                offlineDownload = true,
                offlineDownloadOnlyWhileCharging = true,
                offlineStorageLimitMegabytes = 512,
                offlineKeepFreeMegabytes = 2048,
            ),
        )

        val storage = storageProvider.loadLatestStorage()
        val offlineDownload = storage.getBoolean(NetworkSettingKey.OfflineDownload.value, false)
        val offlineDownloadOnlyWhileCharging =
            storage.getBoolean(NetworkSettingKey.OfflineDownloadOnlyWhileCharging.value, false)
        val offlineStorageLimit = storage.getInt(NetworkSettingKey.OfflineStorageLimit.value, 0)
        val offlineKeepFree = storage.getInt(NetworkSettingKey.OfflineKeepFree.value, 0)

        assertThat(offlineDownload).isEqualTo(true)
        assertThat(offlineDownloadOnlyWhileCharging).isEqualTo(true)
        assertThat(offlineStorageLimit).isEqualTo(512)
        assertThat(offlineKeepFree).isEqualTo(2048)
    }

    @Test
    fun `getConfig should return offline download settings loaded from storage`() {
        val storageProvider = TestStorageProvider(logger)
        storageProvider.createStorageEditor().apply {
            putBoolean(NetworkSettingKey.OfflineDownload.value, true)
            putBoolean(NetworkSettingKey.OfflineDownloadOnlyWhileCharging.value, true)
            putInt(NetworkSettingKey.OfflineStorageLimit.value, 512)
            putInt(NetworkSettingKey.OfflineKeepFree.value, 2048)
        }.commit()

        val preferenceManager = createPreferenceManager(storageProvider)

        assertThat(preferenceManager.getConfig()).isEqualTo(
            NetworkSettings(
                offlineDownload = true,
                offlineDownloadOnlyWhileCharging = true,
                offlineStorageLimitMegabytes = 512,
                offlineKeepFreeMegabytes = 2048,
            ),
        )
    }

    @Test
    fun `getConfig should return offline download settings saved by another preference manager`() {
        val storageProvider = TestStorageProvider(logger)
        createPreferenceManager(storageProvider).save(
            NetworkSettings(
                offlineDownload = true,
                offlineDownloadOnlyWhileCharging = false,
                offlineStorageLimitMegabytes = 256,
                offlineKeepFreeMegabytes = 4096,
            ),
        )

        val preferenceManager = createPreferenceManager(storageProvider)

        assertThat(preferenceManager.getConfig()).isEqualTo(
            NetworkSettings(
                offlineDownload = true,
                offlineDownloadOnlyWhileCharging = false,
                offlineStorageLimitMegabytes = 256,
                offlineKeepFreeMegabytes = 4096,
            ),
        )
    }

    @Test
    fun `getConfig should return default offline download settings when storage is empty`() {
        val storageProvider = TestStorageProvider(logger)

        val preferenceManager = createPreferenceManager(storageProvider)

        assertThat(preferenceManager.getConfig()).isEqualTo(
            NetworkSettings(
                offlineDownload = false,
                offlineDownloadOnlyWhileCharging = false,
                offlineStorageLimitMegabytes = 0,
                offlineKeepFreeMegabytes = 1024,
            ),
        )
    }

    private fun createPreferenceManager(storageProvider: StorageProvider): NetworkSettingsPreferenceManager {
        val dispatcher = UnconfinedTestDispatcher()
        return DefaultNetworkSettingsPreferenceManager(
            logger = logger,
            storageProvider = storageProvider,
            ioDispatcher = dispatcher,
            scope = CoroutineScope(dispatcher),
            preferenceChangeBroker = DefaultPreferenceChangeBroker(),
        )
    }

    private class TestStorageProvider(private val logger: Logger) : StorageProvider {
        private val values = mutableMapOf<String, String>()
        private var currentStorage: Storage = InMemoryStorage(values.toMap(), logger)

        override val storage: Storage
            get() = currentStorage

        override fun loadLatestStorage(): Storage = currentStorage

        override fun createStorageEditor(): StorageEditor = TestStorageEditor()

        private inner class TestStorageEditor : StorageEditor {
            private val changes = mutableMapOf<String, String>()
            private val removals = mutableSetOf<String>()

            override fun putBoolean(key: String, value: Boolean): StorageEditor = apply {
                changes[key] = value.toString()
                removals.remove(key)
            }

            override fun putInt(key: String, value: Int): StorageEditor = apply {
                changes[key] = value.toString()
                removals.remove(key)
            }

            override fun putLong(key: String, value: Long): StorageEditor = apply {
                changes[key] = value.toString()
                removals.remove(key)
            }

            override fun putString(key: String, value: String?): StorageEditor = apply {
                if (value == null) {
                    remove(key)
                } else {
                    changes[key] = value
                    removals.remove(key)
                }
            }

            override fun remove(key: String): StorageEditor = apply {
                changes.remove(key)
                removals.add(key)
            }

            override fun commit(): Boolean {
                removals.forEach { values.remove(it) }
                values.putAll(changes)
                currentStorage = InMemoryStorage(values.toMap(), logger)
                return true
            }
        }
    }
}
