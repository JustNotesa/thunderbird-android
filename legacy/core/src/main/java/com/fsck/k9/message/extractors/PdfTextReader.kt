package com.fsck.k9.message.extractors

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.graphics.pdf.PdfRendererPreV
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.ext.SdkExtensions
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresExtension
import net.thunderbird.legacy.logging.Log
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Reads the text content of a PDF document.
 */
interface PdfTextReader {
    /**
     * Read the text of the PDF document provided by [input].
     *
     * @return The text of the document or `null` if the text couldn't be read.
     */
    fun readText(input: InputStream): String?
}

/**
 * [PdfTextReader] that uses the PDF support provided by Android.
 */
class AndroidPdfTextReader(private val context: Context) : PdfTextReader {
    override fun readText(input: InputStream): String? {
        var tempFile: File? = null
        try {
            tempFile = File.createTempFile(TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX, context.cacheDir)
            tempFile.outputStream().use { outputStream -> input.copyTo(outputStream) }
            return readTextFromFile(tempFile)
        } catch (e: IOException) {
            Log.w(TAG, e, "Unable to read PDF file")
            return null
        } finally {
            tempFile?.delete()
        }
    }

    private fun readTextFromFile(file: File): String? {
        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                readText(descriptor)
            }
        } catch (e: IOException) {
            Log.w(TAG, e, "Unable to open PDF file")
            null
        } catch (e: SecurityException) {
            Log.w(TAG, e, "Unable to read PDF file")
            null
        }
    }

    private fun readText(descriptor: ParcelFileDescriptor): String? {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM -> readTextWithPdfRenderer(descriptor)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && isPdfExtensionAvailable() ->
                readTextWithPdfRendererPreV(descriptor)
            else -> null
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun readTextWithPdfRenderer(descriptor: ParcelFileDescriptor): String? {
        return try {
            PdfRenderer(descriptor).use { renderer ->
                readPageTexts(renderer.pageCount) { index ->
                    renderer.openPage(index).use { page -> page.getTextContents().joinToString(" ") { it.text } }
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, e, "Unable to read PDF file")
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @RequiresExtension(extension = Build.VERSION_CODES.S, version = PDF_EXTENSION_VERSION)
    private fun readTextWithPdfRendererPreV(descriptor: ParcelFileDescriptor): String? {
        return try {
            PdfRendererPreV(descriptor).use { renderer ->
                readPageTexts(renderer.pageCount) { index ->
                    renderer.openPage(index).use { page -> page.getTextContents().joinToString(" ") { it.text } }
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, e, "Unable to read PDF file")
            null
        }
    }

    private fun readPageTexts(pageCount: Int, getPageText: (Int) -> String): String {
        val pageTexts = mutableListOf<String>()
        val numberOfPages = minOf(pageCount, MAX_PAGE_COUNT)

        for (index in 0 until numberOfPages) {
            pageTexts += getPageText(index)
        }

        return pageTexts.joinToString("\n")
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun isPdfExtensionAvailable(): Boolean {
        return SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= PDF_EXTENSION_VERSION
    }

    companion object {
        private const val TAG = "AndroidPdfTextReader"
        private const val TEMP_FILE_PREFIX = "pdf"
        private const val TEMP_FILE_SUFFIX = ".pdf"
        private const val MAX_PAGE_COUNT = 200
        private const val PDF_EXTENSION_VERSION = 13
    }
}
