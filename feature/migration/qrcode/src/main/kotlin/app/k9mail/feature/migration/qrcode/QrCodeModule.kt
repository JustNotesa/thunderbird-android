package app.k9mail.feature.migration.qrcode

import android.app.KeyguardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import app.k9mail.feature.migration.qrcode.domain.QrCodeDomainContract.UseCase
import app.k9mail.feature.migration.qrcode.domain.usecase.QrCodePayloadReader
import app.k9mail.feature.migration.qrcode.domain.usecase.QrCodeSettingsWriter
import app.k9mail.feature.migration.qrcode.payload.QrCodePayloadAdapter
import app.k9mail.feature.migration.qrcode.payload.QrCodePayloadMapper
import app.k9mail.feature.migration.qrcode.payload.QrCodePayloadParser
import app.k9mail.feature.migration.qrcode.payload.QrCodePayloadValidator
import app.k9mail.feature.migration.qrcode.settings.DefaultUuidGenerator
import app.k9mail.feature.migration.qrcode.settings.UuidGenerator
import app.k9mail.feature.migration.qrcode.settings.XmlSettingWriter
import app.k9mail.feature.migration.qrcode.transfer.AndroidLocalNetworkAddressProvider
import app.k9mail.feature.migration.qrcode.transfer.LanTransferClient
import app.k9mail.feature.migration.qrcode.transfer.LegacyTransferAccountSource
import app.k9mail.feature.migration.qrcode.transfer.LocalNetworkAddressProvider
import app.k9mail.feature.migration.qrcode.transfer.TransferAccountSource
import app.k9mail.feature.migration.qrcode.transfer.TransferContainerFetcher
import app.k9mail.feature.migration.qrcode.transfer.TransferReceiver
import app.k9mail.feature.migration.qrcode.transfer.TransferSender
import app.k9mail.feature.migration.qrcode.transfer.TransferTextExporter
import app.k9mail.feature.migration.qrcode.transfer.auth.AccountPasswordVerifier
import app.k9mail.feature.migration.qrcode.transfer.auth.AndroidDeviceSecurity
import app.k9mail.feature.migration.qrcode.transfer.auth.AndroidTransferAuthAttemptStore
import app.k9mail.feature.migration.qrcode.transfer.auth.DeviceSecurity
import app.k9mail.feature.migration.qrcode.transfer.auth.LegacyPasswordAccountSource
import app.k9mail.feature.migration.qrcode.transfer.auth.PasswordAccountSource
import app.k9mail.feature.migration.qrcode.transfer.auth.TransferAuthAttemptStore
import app.k9mail.feature.migration.qrcode.transfer.auth.TransferAuthorization
import app.k9mail.feature.migration.qrcode.transfer.hotspot.AndroidHotspotController
import app.k9mail.feature.migration.qrcode.transfer.hotspot.AndroidHotspotNetworkConnector
import app.k9mail.feature.migration.qrcode.transfer.hotspot.HotspotController
import app.k9mail.feature.migration.qrcode.transfer.hotspot.HotspotNetworkConnector
import app.k9mail.feature.migration.qrcode.transfer.hotspot.UnsupportedHotspotController
import app.k9mail.feature.migration.qrcode.transfer.hotspot.UnsupportedHotspotNetworkConnector
import app.k9mail.feature.migration.qrcode.ui.QrCodeScannerViewModel
import app.k9mail.feature.migration.qrcode.ui.auth.TransferAuthorizationViewModel
import app.k9mail.feature.migration.qrcode.ui.paste.TransferPasteViewModel
import app.k9mail.feature.migration.qrcode.ui.send.TransferSendViewModel
import app.k9mail.feature.migration.qrcode.ui.text.TransferTextViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val qrCodeModule = module {
    viewModel {
        QrCodeScannerViewModel(
            qrCodePayloadReader = get(),
            qrCodeSettingsWriter = get(),
            transferReceiver = get(),
        )
    }

    viewModel {
        TransferSendViewModel(transferSender = get())
    }

    viewModel {
        TransferPasteViewModel(
            qrCodePayloadReader = get(),
            qrCodeSettingsWriter = get(),
        )
    }

    viewModel {
        TransferTextViewModel(transferTextExporter = get())
    }

    viewModel {
        TransferAuthorizationViewModel(
            transferAuthorization = get(),
            passwordVerifier = get(),
        )
    }

    factory { QrCodePayloadAdapter() }
    factory { QrCodePayloadParser(qrCodePayloadAdapter = get()) }
    factory { QrCodePayloadValidator() }
    factory {
        QrCodePayloadMapper(
            qrCodePayloadValidator = get(),
            deletePolicyProvider = get(),
        )
    }

    factory<UseCase.QrCodePayloadReader> {
        QrCodePayloadReader(
            parser = get(),
            mapper = get(),
        )
    }

    factory<UseCase.QrCodeSettingsWriter> {
        QrCodeSettingsWriter(
            context = get(),
            xmlSettingWriter = get(),
        )
    }

    factory<UuidGenerator> { DefaultUuidGenerator() }
    factory {
        XmlSettingWriter(
            uuidGenerator = get(),
            featureFlagProvider = get(),
            accountColorProvider = get(),
        )
    }

    factory<TransferContainerFetcher> { LanTransferClient() }
    factory { TransferReceiver(fetcher = get(), hotspotConnector = get()) }

    factory<LocalNetworkAddressProvider> {
        AndroidLocalNetworkAddressProvider(
            connectivityManager = get<Context>().getSystemService(ConnectivityManager::class.java),
        )
    }
    factory<TransferAccountSource> { LegacyTransferAccountSource(accountManager = get()) }
    factory { TransferSender(accountSource = get(), addressProvider = get(), hotspotController = get()) }
    factory { TransferTextExporter(accountSource = get()) }

    factory<DeviceSecurity> {
        AndroidDeviceSecurity(
            keyguardManager = get<Context>().getSystemService(KeyguardManager::class.java),
        )
    }
    single<PasswordAccountSource> { LegacyPasswordAccountSource(accountManager = get()) }
    single<TransferAuthAttemptStore> { AndroidTransferAuthAttemptStore(context = get()) }
    single { AccountPasswordVerifier(accountSource = get(), attemptStore = get()) }
    factory { TransferAuthorization(deviceSecurity = get(), passwordVerifier = get()) }

    factory<HotspotController> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AndroidHotspotController(
                wifiManager = get<Context>().applicationContext.getSystemService(WifiManager::class.java),
            )
        } else {
            UnsupportedHotspotController
        }
    }

    factory<HotspotNetworkConnector> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            AndroidHotspotNetworkConnector(
                connectivityManager = get<Context>().getSystemService(ConnectivityManager::class.java),
            )
        } else {
            UnsupportedHotspotNetworkConnector
        }
    }
}
