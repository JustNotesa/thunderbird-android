package com.fsck.k9.activity.compose.signature

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.fsck.k9.activity.compose.signature.SignatureChipState.Kind
import kotlin.test.Test
import net.thunderbird.core.android.account.AccountSignature

class SignatureChipTest {
    private val private = AccountSignature(id = "p", name = "Privat", text = "Max", isHtml = false)
    private val work = AccountSignature(id = "w", name = "Geschäftlich", text = "<p>Max</p>", isHtml = true)

    private fun signature(
        inUse: Boolean = true,
        text: String = "Max",
        isHtml: Boolean = false,
        applied: String? = null,
        defaultText: String? = null,
        defaultIsHtml: Boolean = false,
    ) = MessageSignature(inUse, text, isHtml, applied, defaultText, defaultIsHtml)

    @Test
    fun `should offer to create a signature when there is none and none is saved`() {
        val result = SignatureChip.stateOf(signature(inUse = false), saved = emptyList())

        assertThat(result).isEqualTo(SignatureChipState(Kind.Create))
    }

    @Test
    fun `should say no signature when the message has none but some are saved`() {
        val result = SignatureChip.stateOf(signature(inUse = false), saved = listOf(private))

        assertThat(result).isEqualTo(SignatureChipState(Kind.None))
    }

    @Test
    fun `should show the name of the saved signature chosen for the message`() {
        val result = SignatureChip.stateOf(signature(applied = "p"), saved = listOf(private, work))

        assertThat(result).isEqualTo(SignatureChipState(Kind.Saved, id = "p", name = "Privat"))
    }

    @Test
    fun `should mark the chosen signature as edited when the message text differs`() {
        val result = SignatureChip.stateOf(signature(text = "Max M.", applied = "p"), saved = listOf(private))

        assertThat(result.isEdited).isEqualTo(true)
        assertThat(result.name).isEqualTo("Privat")
    }

    @Test
    fun `should show the default signature when it is what the message has`() {
        val result = SignatureChip.stateOf(signature(defaultText = "Max"), saved = listOf(private))

        assertThat(result).isEqualTo(SignatureChipState(Kind.Default))
    }

    @Test
    fun `should show the default signature even when no signature is saved`() {
        val result = SignatureChip.stateOf(signature(defaultText = "Max"), saved = emptyList())

        assertThat(result.kind).isEqualTo(Kind.Default)
    }

    @Test
    fun `should find the saved signature of a draft by its text`() {
        val result = SignatureChip.stateOf(
            signature(text = "<p>Max</p>", isHtml = true),
            saved = listOf(private, work),
        )

        assertThat(result).isEqualTo(SignatureChipState(Kind.Saved, id = "w", name = "Geschäftlich", isHtml = true))
    }

    @Test
    fun `should call a signature that matches nothing custom`() {
        val result = SignatureChip.stateOf(signature(text = "Other"), saved = listOf(private))

        assertThat(result).isEqualTo(SignatureChipState(Kind.Custom))
    }

    @Test
    fun `should ignore line ending and surrounding space differences`() {
        val result = SignatureChip.stateOf(signature(text = "Max\r\n\r\n", applied = "p"), saved = listOf(private))

        assertThat(result.isEdited).isEqualTo(false)
    }
}
