package com.fsck.k9.activity.compose.signature

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.fsck.k9.activity.compose.signature.SignatureChipState.Kind
import kotlin.test.Test
import net.thunderbird.core.android.account.AccountSignature

class SignatureChooserModelTest {
    @Test
    fun `should show html as one plain line`() {
        val result = SignatureChooserModel.previewLine(
            "<p>Max&nbsp;Mustermann</p><p>Projekt &amp; Leitung</p>",
            isHtml = true,
        )

        assertThat(result).isEqualTo("Max Mustermann · Projekt & Leitung")
    }

    @Test
    fun `should join the lines of a plain text signature`() {
        val result = SignatureChooserModel.previewLine("Max\nMustermann\n\n", isHtml = false)

        assertThat(result).isEqualTo("Max Mustermann")
    }

    @Test
    fun `should shorten a long preview`() {
        val result = SignatureChooserModel.previewLine("x".repeat(100), isHtml = false)

        assertThat(result.length).isEqualTo(60)
        assertThat(result.endsWith("…")).isTrue()
    }

    @Test
    fun `should list the default and the saved signatures and mark the chosen one`() {
        val saved = listOf(AccountSignature("p", "Privat", "Max"), AccountSignature("w", "Work", "<p>W</p>", true))

        val result = SignatureChooserModel.of(
            saved = saved,
            defaultText = "Default text",
            defaultIsHtml = false,
            defaultName = "Standardsignatur",
            chip = SignatureChipState(Kind.Saved, id = "w", name = "Work", isHtml = true),
        )

        assertThat(result.defaultEntry!!.preview).isEqualTo("Default text")
        assertThat(result.savedEntries.map { it.name }).containsExactly("Privat", "Work")
        assertThat(result.savedEntries[1].isHtml).isTrue()
        assertThat(result.currentSelection).isEqualTo(SignatureChooserModel.Selection.Saved("w"))
        assertThat(result.hasSavedSignatures).isTrue()
    }

    @Test
    fun `should have no default entry and no saved ones when there are none`() {
        val result = SignatureChooserModel.of(
            saved = emptyList(),
            defaultText = null,
            defaultIsHtml = false,
            defaultName = "Standardsignatur",
            chip = SignatureChipState(Kind.Create),
        )

        assertThat(result.defaultEntry).isNull()
        assertThat(result.hasSavedSignatures).isFalse()
        assertThat(result.currentSelection).isEqualTo(SignatureChooserModel.Selection.None)
    }
}
