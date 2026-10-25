package net.thunderbird.feature.updater.internal.install

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.thunderbird.feature.updater.api.AppUpdate
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody

/**
 * Downloads the APK of an available app update.
 *
 * The APK is stored in [downloadDir]. Only HTTPS download URLs are accepted. The downloaded file is verified against
 * the checksum and size reported by GitHub before it is published as the final APK file.
 */
internal class ApkDownloader(
    private val httpClient: OkHttpClient,
    private val downloadDir: File,
) {
    /**
     * Downloads the APK of [update].
     *
     * @param onProgress called with the number of bytes downloaded so far and the total number of bytes to download.
     *   The total is `-1` when the size of the download isn't known.
     */
    suspend fun download(
        update: AppUpdate,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): DownloadResult = withContext(Dispatchers.IO) {
        val url = update.apkUrl.toHttpUrlOrNull()
        if (url == null || !url.isHttps) {
            return@withContext DownloadResult.Failed
        }
        // Without a checksum the APK can't be verified, so it isn't downloaded at all.
        if (update.apkSha256 == null) {
            return@withContext DownloadResult.ChecksumMismatch
        }

        prepareDownloadDirectory()
        val partFile = File(downloadDir, PART_FILE_NAME)
        try {
            executeRequest(update, url, partFile, onProgress)
        } catch (_: IOException) {
            DownloadResult.Failed
        } finally {
            // The part file is only left over when the download didn't succeed.
            if (partFile.exists()) {
                partFile.delete()
            }
        }
    }

    private fun prepareDownloadDirectory() {
        downloadDir.mkdirs()
        downloadDir.listFiles().orEmpty().forEach { file ->
            if (file.isFile && (file.name.endsWith(APK_EXTENSION) || file.name.endsWith(PART_EXTENSION))) {
                file.delete()
            }
        }
    }

    private suspend fun executeRequest(
        update: AppUpdate,
        url: HttpUrl,
        partFile: File,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): DownloadResult {
        val request = Request.Builder().url(url).build()
        val call = httpClient.newCall(request)
        val context = currentCoroutineContext()
        // Abort the request when the coroutine is cancelled.
        context[Job]?.invokeOnCompletion { cause ->
            if (cause != null) {
                call.cancel()
            }
        }

        val response = call.execute()
        return try {
            when {
                // The APK is only downloaded over HTTPS, a redirect to an insecure URL is not accepted.
                !response.request.url.isHttps -> DownloadResult.Failed

                response.isSuccessful -> writeBody(update, response, partFile, onProgress)

                else -> DownloadResult.Failed
            }
        } finally {
            response.close()
        }
    }

    private suspend fun writeBody(
        update: AppUpdate,
        response: Response,
        partFile: File,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): DownloadResult {
        val body = response.body
        val total = resolveTotal(body.contentLength(), update.apkSizeBytes)
        val digest = MessageDigest.getInstance(SHA_256_ALGORITHM)
        val context = currentCoroutineContext()

        onProgress(0L, total)
        val downloaded = body.byteStream().use { input ->
            partFile.outputStream().use { output ->
                copyToFile(input, output, digest, total, context, onProgress)
            }
        }
        onProgress(downloaded, total)

        return verify(update, downloaded, digest.digest(), partFile)
    }

    private fun copyToFile(
        input: InputStream,
        output: OutputStream,
        digest: MessageDigest,
        total: Long,
        context: CoroutineContext,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): Long {
        val buffer = ByteArray(BUFFER_SIZE)
        var downloaded = 0L
        var lastReported = 0L
        while (true) {
            context.ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            digest.update(buffer, 0, read)
            downloaded += read
            if (downloaded - lastReported >= PROGRESS_INTERVAL_BYTES) {
                onProgress(downloaded, total)
                lastReported = downloaded
            }
        }
        return downloaded
    }

    private fun resolveTotal(contentLength: Long, declaredSize: Long): Long {
        return when {
            contentLength > 0L -> contentLength
            declaredSize > 0L -> declaredSize
            else -> UNKNOWN_TOTAL
        }
    }

    private fun verify(
        update: AppUpdate,
        downloaded: Long,
        digest: ByteArray,
        partFile: File,
    ): DownloadResult {
        val expectedChecksum = update.apkSha256
        val actualChecksum = hexDigest(digest)
        // The download isn't started without a checksum, so a missing checksum never matches.
        val checksumMatches = expectedChecksum?.equals(actualChecksum, ignoreCase = true) == true
        val sizeMatches = update.apkSizeBytes <= 0L || downloaded == update.apkSizeBytes
        return when {
            !checksumMatches -> DownloadResult.ChecksumMismatch
            !sizeMatches -> DownloadResult.Failed
            else -> renameToApk(update, partFile)
        }
    }

    private fun renameToApk(update: AppUpdate, partFile: File): DownloadResult {
        val targetFile = File(downloadDir, sanitizeTag(update.tag) + APK_EXTENSION)
        return if (partFile.renameTo(targetFile)) {
            DownloadResult.Success(targetFile)
        } else {
            DownloadResult.Failed
        }
    }

    private fun sanitizeTag(tag: String): String {
        return tag.map { character -> if (character in TAG_CHARACTERS) character else '_' }.joinToString("")
    }

    private fun hexDigest(digest: ByteArray): String {
        val builder = StringBuilder(digest.size * HEX_CHARACTERS_PER_BYTE)
        for (byte in digest) {
            val value = byte.toInt() and BYTE_MASK
            builder.append(HEX_DIGITS[value shr NIBBLE_SHIFT])
            builder.append(HEX_DIGITS[value and NIBBLE_MASK])
        }
        return builder.toString()
    }

    private companion object {
        const val SHA_256_ALGORITHM = "SHA-256"
        const val PART_FILE_NAME = "download.part"
        const val APK_EXTENSION = ".apk"
        const val PART_EXTENSION = ".part"
        const val BUFFER_SIZE = 8 * 1024
        const val PROGRESS_INTERVAL_BYTES = 256L * 1024L
        const val UNKNOWN_TOTAL = -1L
        const val TAG_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789._-"
        const val HEX_DIGITS = "0123456789abcdef"
        const val HEX_CHARACTERS_PER_BYTE = 2
        const val BYTE_MASK = 0xFF
        const val NIBBLE_MASK = 0x0F
        const val NIBBLE_SHIFT = 4
    }
}

/** Result of downloading the APK of an app update. */
internal sealed interface DownloadResult {
    /** The APK was downloaded and verified. [file] is the downloaded APK. */
    data class Success(val file: File) : DownloadResult

    /** The APK couldn't be downloaded or didn't pass verification. */
    data object Failed : DownloadResult

    /** The downloaded APK doesn't match the checksum reported for the update. */
    data object ChecksumMismatch : DownloadResult
}
