package com.fsck.k9.ui.messagelist.item

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.fsck.k9.ui.R
import com.fsck.k9.ui.messagelist.SearchScope
import com.fsck.k9.ui.messagelist.ServerSearchStatus
import com.fsck.k9.ui.messagelist.ServerSearchStatus.Result
import net.thunderbird.core.android.testing.RobolectricTest
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SearchStatusPanelTest : RobolectricTest() {
    private val activity = Robolectric.buildActivity(AppCompatActivity::class.java).create().get()
    private val context: Context =
        ContextThemeWrapper(activity, com.google.android.material.R.style.Theme_Material3_Light)
    private var searchMessageContentsClickCount = 0
    private var searchAttachmentsClickCount = 0
    private val selectedScopes = mutableListOf<SearchScope>()

    @Test
    fun `ended search should display both buttons`() {
        val testSubject = createPanel()

        testSubject.bind(
            ServerSearchStatus.Ended(
                title = "Server search finished",
                result = Result.COMPLETE,
                isMessageContentsSearchOffered = true,
                isAttachmentSearchOffered = true,
            ),
            scope = null,
        )

        val view = testSubject.view
        assertThat(view.isVisible).isTrue()
        assertThat(view.statusText.text.toString()).isEqualTo("Server search finished")
        assertThat(view.contentsButton.isVisible).isTrue()
        assertThat(view.attachmentsButton.isVisible).isTrue()
        assertThat(view.spinner.isVisible).isFalse()
    }

    @Test
    fun `running search should display the spinner and no row of buttons`() {
        val testSubject = createPanel()

        testSubject.bind(ServerSearchStatus.Running(title = "Sending query to server"), scope = null)

        val view = testSubject.view
        assertThat(view.spinner.isVisible).isTrue()
        assertThat(view.buttons.isVisible).isFalse()
    }

    @Test
    fun `buttons should request the searches`() {
        val testSubject = createPanel()
        testSubject.bind(
            ServerSearchStatus.Ended(
                title = "Server search finished",
                result = Result.COMPLETE,
                isMessageContentsSearchOffered = true,
                isAttachmentSearchOffered = true,
            ),
            scope = null,
        )

        testSubject.view.contentsButton.performClick()
        testSubject.view.attachmentsButton.performClick()

        assertThat(searchMessageContentsClickCount).isEqualTo(1)
        assertThat(searchAttachmentsClickCount).isEqualTo(1)
    }

    @Test
    fun `hide should hide the panel`() {
        val testSubject = createPanel()
        testSubject.bind(ServerSearchStatus.Running(title = "Sending query to server"), scope = null)

        testSubject.hide()

        assertThat(testSubject.view.isVisible).isFalse()
    }

    @Test
    fun `click on a place to search should select it`() {
        val testSubject = createPanel()
        testSubject.bind(ServerSearchStatus.Running(title = "Sending query"), SearchScope.EVERYWHERE)

        testSubject.view.findViewById<View>(R.id.search_scope_folder).performClick()

        assertThat(selectedScopes).isEqualTo(listOf(SearchScope.FOLDER))
    }

    private fun createPanel(): SearchStatusPanel {
        return SearchStatusPanel.create(
            layoutInflater = LayoutInflater.from(context),
            parent = LinearLayout(context),
            onSearchMessageContentsClickListener = { searchMessageContentsClickCount++ },
            onSearchAttachmentsClickListener = { searchAttachmentsClickCount++ },
            onScopeSelected = { selectedScopes.add(it) },
        )
    }

    private val View.statusText: TextView get() = findViewById(R.id.search_status_text)
    private val View.spinner: View get() = findViewById(R.id.search_status_spinner)
    private val View.buttons: View get() = findViewById(R.id.search_status_buttons)
    private val View.contentsButton: View get() = findViewById(R.id.search_contents_button)
    private val View.attachmentsButton: View get() = findViewById(R.id.search_attachments_button)
}
