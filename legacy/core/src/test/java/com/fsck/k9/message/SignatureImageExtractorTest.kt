package com.fsck.k9.message

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import org.junit.Test

class SignatureImageExtractorTest {
    private var counter = 0
    private val testSubject = SignatureImageExtractor(contentIdGenerator = { "id${++counter}@example.test" })

    @Test
    fun `should replace an embedded image by a reference to its part`() {
        val html = """<p>Hi <img src="data:image/png;base64,AQID" alt="x"></p>"""

        val result = testSubject.extract(html)

        assertThat(result.html).isEqualTo("""<p>Hi <img src="cid:id1@example.test" alt="x"></p>""")
        assertThat(result.parts).hasSize(1)
        assertThat(result.parts[0].contentType).isEqualTo("image/png")
        assertThat(result.parts[0].data.toList()).isEqualTo(listOf<Byte>(1, 2, 3))
        assertThat(result.parts[0].fileName).isEqualTo("signature-image-1.png")
    }

    @Test
    fun `should give every image its own content id`() {
        val html = """<img src='data:image/jpeg;base64,AQID'><img src="data:image/gif;base64,BAUG">"""

        val result = testSubject.extract(html)

        assertThat(result.parts).hasSize(2)
        assertThat(result.html).contains("cid:id1@example.test")
        assertThat(result.html).contains("cid:id2@example.test")
        assertThat(result.parts[0].fileName).isEqualTo("signature-image-1.jpg")
    }

    @Test
    fun `should leave images with web addresses alone`() {
        val html = """<img src="https://example.test/a.png">"""

        val result = testSubject.extract(html)

        assertThat(result.html).isEqualTo(html)
        assertThat(result.parts).hasSize(0)
    }

    @Test
    fun `should keep an image with broken data embedded`() {
        val html = """<img src="data:image/png;base64,A">"""

        val result = testSubject.extract(html)

        assertThat(result.html).contains("data:image/png")
        assertThat(result.html).doesNotContain("cid:")
        assertThat(result.parts).hasSize(0)
    }
}
