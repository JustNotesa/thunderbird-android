package net.thunderbird.feature.mail.message.composer.internal.signature

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.doesNotContain
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.mail.message.composer.signature.ClipboardContent
import net.thunderbird.feature.mail.message.composer.signature.PasteNotice
import net.thunderbird.feature.mail.message.composer.signature.PasteResult
import net.thunderbird.feature.mail.message.composer.signature.SignatureImageLimits

class DefaultSignaturePasteProcessorTest {
    private val loadedSources = mutableListOf<String>()
    private val results = mutableMapOf<String, ImageLoadResult>()
    private val testSubject = DefaultSignaturePasteProcessor(
        imageLoader = { source ->
            loadedSources += source
            results[source] ?: ImageLoadResult.Unreadable
        },
        sanitizer = DefaultHtmlSignatureSanitizer(),
    )

    private fun image(size: Int = 3, width: Int = 100, downscaled: Boolean = false) =
        ImageLoadResult.Loaded(ByteArray(size) { 1 }, "image/png", width, downscaled)

    private suspend fun paste(html: String, usedBytes: Int = 0): PasteResult.Content {
        val result = testSubject.process(ClipboardContent(html = html), usedBytes)
        assertThat(result).isInstanceOf(PasteResult.Content::class)
        return result as PasteResult.Content
    }

    @Test
    fun `should keep the formatting of pasted html`() = runTest {
        val result = paste("<p>Hi <b>there</b></p>")

        assertThat(result.html).contains("<b>there</b>")
        assertThat(result.notices).isEmpty()
    }

    @Test
    fun `should turn plain text into html with line breaks`() = runTest {
        val result = testSubject.process(ClipboardContent(plainText = "a < b\nc"), 0)

        assertThat((result as PasteResult.Content).html.replace("\n", "")).isEqualTo("a &lt; b<br>c")
    }

    @Test
    fun `should report an empty clipboard`() = runTest {
        val result = testSubject.process(ClipboardContent(), 0)

        assertThat(result).isEqualTo(PasteResult.NothingToPaste())
    }

    @Test
    fun `should remove scripts and tell the user`() = runTest {
        val result = paste("""<p onclick="x()">Hi</p><script>alert(1)</script>""")

        assertThat(result.html).doesNotContain("script")
        assertThat(result.html).doesNotContain("onclick")
        assertThat(result.notices).containsExactly(PasteNotice.ActiveContentRemoved)
    }

    @Test
    fun `should embed a downloaded image as data address`() = runTest {
        results["https://example.test/a.png"] = image()

        val result = paste("""<img src="https://example.test/a.png">""")

        assertThat(loadedSources).containsExactly("https://example.test/a.png")
        assertThat(result.html).contains("""src="data:image/png;base64,AQEB"""")
        assertThat(result.notices).isEmpty()
    }

    @Test
    fun `should leave out an image that could not be downloaded and tell the user`() = runTest {
        results["https://example.test/a.png"] = ImageLoadResult.NotDownloaded

        val result = paste("""<p>Hi</p><img src="https://example.test/a.png">""")

        assertThat(result.html).doesNotContain("img")
        assertThat(result.notices).containsExactly(PasteNotice.ImagesNotDownloaded(1))
    }

    @Test
    fun `should leave out an unreadable image and tell the user`() = runTest {
        val result = paste("""<p>Hi</p><img src="file:///x.png"><img>""")

        assertThat(result.html).doesNotContain("img")
        assertThat(result.notices).containsExactly(PasteNotice.ImagesUnreadable(2))
    }

    @Test
    fun `should keep the reason when nothing is left to paste`() = runTest {
        results["https://example.test/a.png"] = ImageLoadResult.NotDownloaded

        val result = testSubject.process(ClipboardContent(html = """<img src="https://example.test/a.png">"""), 0)

        assertThat(result).isEqualTo(PasteResult.NothingToPaste(listOf(PasteNotice.ImagesNotDownloaded(1))))
    }

    @Test
    fun `should leave out images that exceed the total limit and tell the user`() = runTest {
        results["https://example.test/a.png"] = image(size = 10)
        results["https://example.test/b.png"] = image(size = 10)

        val result = paste(
            """<img src="https://example.test/a.png"><img src="https://example.test/b.png">""",
            usedBytes = SignatureImageLimits.MAX_TOTAL_BYTES - 15,
        )

        assertThat(result.html).contains("AQEB")
        assertThat(result.notices)
            .containsExactly(PasteNotice.ImagesTooLarge(1, SignatureImageLimits.MAX_TOTAL_BYTES))
    }

    @Test
    fun `should set the width of a downscaled image and tell the user`() = runTest {
        results["content://x/1"] = image(width = 800, downscaled = true)

        val result = paste("""<img src="content://x/1" width="3000" height="2000">""")

        assertThat(result.html).contains("""width="800"""")
        assertThat(result.html).doesNotContain("height")
        assertThat(result.notices).containsExactly(PasteNotice.ImagesDownscaled(1))
    }

    @Test
    fun `should embed images that are on the clipboard on their own`() = runTest {
        results["content://x/1"] = image()

        val result = testSubject.process(ClipboardContent(imageSources = listOf("content://x/1")), 0)

        assertThat((result as PasteResult.Content).html).contains("data:image/png;base64")
    }

    @Test
    fun `should limit the size of an image to the maximum width`() {
        assertThat(SignatureImageLimits.scaledSize(1600, 1000)).isEqualTo(800 to 500)
        assertThat(SignatureImageLimits.scaledSize(400, 1000)).isEqualTo(400 to 1000)
        assertThat(SignatureImageLimits.scaledSize(801, 1)).isEqualTo(800 to 1)
    }
}
