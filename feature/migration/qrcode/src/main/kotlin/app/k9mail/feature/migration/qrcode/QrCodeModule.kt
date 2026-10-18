package app.k9mail.feature.migration.qrcode

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
import app.k9mail.feature.migration.qrcode.transfer.hotspot.AndroidHotspotController
import app.k9mail.feature.migration.qrcode.transfer.hotspot.HotspotController
import app.k9mail.feature.migration.qrcode.transfer.hotspot.UnsupportedHotspotController
import app.k9mail.feature.migration.qrcode.ui.QrCodeScannerViewModel
import app.k9mail.feature.migration.qrcode.ui.send.TransferSendViewModel
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
        )
    }

    factory<TransferContainerFetcher> { LanTransferClient() }
    factory { TransferReceiver(fetcher = get()) }

    factory<LocalNetworkAddressProvider> {
        AndroidLocalNetworkAddressProvider(
            connectivityManager = get<Context>().getSystemService(ConnectivityManager::class.java),
        )
    }
    factory<TransferAccountSource> { LegacyTransferAccountSource(accountManager = get()) }
    factory { TransferSender(accountSource = get(), addressProvider = get()) }

    factory<HotspotController> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AndroidHotspotController(
                wifiManager = get<Context>().applicationContext.getSystemService(WifiManager::class.java),
            )
        } else {
            UnsupportedHotspotController
        }
    }
}
