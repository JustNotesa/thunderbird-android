package com.fsck.k9.message.extractors

import org.koin.dsl.module

val extractorModule = module {
    single { AttachmentInfoExtractor(get()) }
    single { TextPartFinder() }
    single { BasicPartInfoExtractor() }
    factory<PdfTextReader> { AndroidPdfTextReader(context = get()) }
    factory { AttachmentTextExtractor(pdfTextReader = get()) }
}
