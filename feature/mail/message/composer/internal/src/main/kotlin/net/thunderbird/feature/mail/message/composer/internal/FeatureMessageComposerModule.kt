package net.thunderbird.feature.mail.message.composer.internal

import java.util.concurrent.TimeUnit
import net.thunderbird.feature.mail.message.composer.dialog.SentFolderNotFoundConfirmationDialogFragmentFactory
import net.thunderbird.feature.mail.message.composer.html.MessageComposerHtmlSettingsProvider
import net.thunderbird.feature.mail.message.composer.internal.dialog.SentFolderNotFoundConfirmationDialogFragment
import net.thunderbird.feature.mail.message.composer.internal.html.DefaultMessageComposerHtmlSettingsProvider
import net.thunderbird.feature.mail.message.composer.internal.richtext.DefaultRichTextHtmlConverter
import net.thunderbird.feature.mail.message.composer.internal.signature.AndroidSignatureImageLoader
import net.thunderbird.feature.mail.message.composer.internal.signature.DefaultHtmlSignatureSanitizer
import net.thunderbird.feature.mail.message.composer.internal.signature.DefaultSignaturePasteProcessor
import net.thunderbird.feature.mail.message.composer.richtext.RichTextHtmlConverter
import net.thunderbird.feature.mail.message.composer.signature.HtmlSignatureSanitizer
import net.thunderbird.feature.mail.message.composer.signature.SignaturePasteProcessor
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

private const val CONNECT_TIMEOUT_SECONDS = 10L
private const val READ_TIMEOUT_SECONDS = 15L
private const val CALL_TIMEOUT_SECONDS = 25L

// Own client under a qualifier so pasting never replaces or depends on another module's OkHttpClient binding.
private val signatureImageHttpClient = named("SignatureImageOkHttpClient")

val featureMessageComposerModule = module {
    factory<SentFolderNotFoundConfirmationDialogFragmentFactory> {
        SentFolderNotFoundConfirmationDialogFragment.Factory
    }
    factory<MessageComposerHtmlSettingsProvider> {
        DefaultMessageComposerHtmlSettingsProvider(themeManager = get())
    }
    single<HtmlSignatureSanitizer> { DefaultHtmlSignatureSanitizer() }
    factory<RichTextHtmlConverter> { DefaultRichTextHtmlConverter() }
    single(signatureImageHttpClient) {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            // Images are only loaded over HTTPS or from where the user pasted them, never redirected to plain HTTP.
            .followSslRedirects(false)
            .build()
    }
    factory<SignaturePasteProcessor> {
        DefaultSignaturePasteProcessor(
            imageLoader = AndroidSignatureImageLoader(
                contentResolver = androidContext().contentResolver,
                httpClient = get(signatureImageHttpClient),
                logger = get(),
            ),
            sanitizer = get(),
        )
    }
}
