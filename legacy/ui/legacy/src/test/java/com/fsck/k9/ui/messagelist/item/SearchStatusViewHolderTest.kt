package com.fsck.k9.ui.messagelist.item

import android.app.Activity
import android.content.Context
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import assertk.all
import assertk.assertThat
import assertk.assertions.each
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isTrue
import com.fsck.k9.ui.R
import com.fsck.k9.ui.messagelist.ServerSearchStatus
import com.fsck.k9.ui.messagelist.ServerSearchStatus.Result
import net.thunderbird.core.android.testing.RobolectricTest
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

private const val ROW_WIDTH = 1080
private const val LONG_TEXT = "Unable to resolve host \"imap.domain.example\": No address associated with hostname. " +
    "The connection to the server could not be established. Please check your network connection and try again."

@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SearchStatusViewHolderTest : RobolectricTest() {
    private val activity = Robolectric.buildActivity(AppCompatActivity::class.java).create().get()
    private val context: Context =
        ContextThemeWrapper(activity, com.google.android.material.R.style.Theme_Material3_Light)
    private var searchMessageContentsClickCount = 0

    @Test
    fun `row should have the same height no matter what is displayed`() {
        val testSubject = createViewHolder()

        val heights = STATUSES.map { status ->
            testSubject.bind(status)
            testSubject.itemView.layoutInWindow()
            testSubject.itemView.measuredHeight
        }

        assertThat(heights.first()).isGreaterThan(0)
        assertThat(heights).each { it.isEqualTo(heights.first()) }
    }

    @Test
    fun `new row should have the same height no matter what is displayed first`() {
        val heights = STATUSES.map { status ->
            val testSubject = createViewHolder()
            testSubject.bind(status)
            testSubject.itemView.layoutInWindow()
            testSubject.itemView.measuredHeight
        }

        assertThat(heights).each { it.isEqualTo(heights.first()) }
    }

    @Test
    fun `running search should display progress`() {
        val testSubject = createViewHolder()

        testSubject.bind(
            ServerSearchStatus.Running(
                title = "Server search: folder 3 of 10",
                progress = ServerSearchStatus.Progress(folderIndex = 3, folderCount = 10, folderLabel = "Projects"),
                detail = "2 messages found",
            ),
        )

        val view = testSubject.itemView
        assertThat(view.titleView.text.toString()).isEqualTo("Server search: folder 3 of 10")
        assertThat(view.spinnerView.isVisible).isTrue()
        assertThat(view.iconView.isVisible).isFalse()
        assertThat(view.progressView.isVisible).isTrue()
        assertThat(view.folderView.text.toString()).isEqualTo("Projects")
        assertThat(view.detailView.text.toString()).isEqualTo("2 messages found")
        assertThat(view.messageView.isVisible).isFalse()
        assertThat(view.contentsButtonView.isVisible).isFalse()
    }

    @Test
    fun `running search without progress should not display progress of a previous search`() {
        val testSubject = createViewHolder()
        testSubject.bind(
            ServerSearchStatus.Running(
                title = "Server search: folder 3 of 10",
                progress = ServerSearchStatus.Progress(folderIndex = 3, folderCount = 10, folderLabel = "Projects"),
                detail = "2 messages found",
            ),
        )

        testSubject.bind(ServerSearchStatus.Running(title = "Sending query to server"))

        val view = testSubject.itemView
        assertThat(view.progressView.isInvisible).isTrue()
        assertThat(view.folderView.text.toString()).isEqualTo("")
        assertThat(view.detailView.text.toString()).isEqualTo("")
    }

    @Test
    fun `running search should display what it is doing instead of what it has found`() {
        val testSubject = createViewHolder()

        testSubject.bind(
            ServerSearchStatus.Running(
                title = "Server search: folder 3 of 10",
                progress = ServerSearchStatus.Progress(folderIndex = 3, folderCount = 10, folderLabel = "Projects"),
                detail = "2 messages found",
                activity = "Connecting to the server… 5 s",
            ),
        )

        assertThat(testSubject.itemView.detailView.text.toString()).isEqualTo("Connecting to the server… 5 s")
    }

    @Test
    fun `ended search should display problem`() {
        val testSubject = createViewHolder()

        testSubject.bind(
            ServerSearchStatus.Ended(title = "Remote search failed", result = Result.PROBLEM, message = "Timeout"),
        )

        val view = testSubject.itemView
        assertThat(view.titleView.text.toString()).isEqualTo("Remote search failed")
        assertThat(view.spinnerView.isVisible).isFalse()
        assertThat(view.iconView.isVisible).isTrue()
        assertThat(view.progressGroupView.isInvisible).isTrue()
        assertThat(view.messageView.isVisible).isTrue()
        assertThat(view.messageView.text.toString()).isEqualTo("Timeout")
        assertThat(view.contentsButtonView.isVisible).isFalse()
    }

    @Test
    fun `problem should remain readable when the search of the message contents is offered`() {
        val testSubject = createViewHolder()

        testSubject.bind(
            ServerSearchStatus.Ended(
                title = "Server search incomplete",
                result = Result.PROBLEM,
                message = LONG_TEXT,
                isMessageContentsSearchOffered = true,
            ),
        )
        testSubject.itemView.layoutInWindow()

        val view = testSubject.itemView
        assertThat(view.contentsButtonView.isVisible).isTrue()
        assertThat(view.messageView).all {
            transform { it.height }.isGreaterThanOrEqualTo(view.messageView.lineHeight)
            transform { it.maxLines }.isEqualTo(1)
        }
    }

    @Test
    fun `problem should use the available space when the search of the message contents is not offered`() {
        val testSubject = createViewHolder()

        testSubject.bind(
            ServerSearchStatus.Ended(title = "Remote search failed", result = Result.PROBLEM, message = LONG_TEXT),
        )
        testSubject.itemView.layoutInWindow()

        assertThat(testSubject.itemView.messageView.maxLines).isGreaterThan(1)
    }

    @Test
    fun `click on button should request search of the message contents`() {
        val testSubject = createViewHolder()
        testSubject.bind(
            ServerSearchStatus.Ended(
                title = "Server search finished",
                result = Result.COMPLETE,
                isMessageContentsSearchOffered = true,
            ),
        )

        testSubject.itemView.contentsButtonView.performClick()

        assertThat(searchMessageContentsClickCount).isEqualTo(1)
    }

    private fun createViewHolder(): SearchStatusViewHolder {
        return SearchStatusViewHolder.create(
            layoutInflater = LayoutInflater.from(context),
            parent = LinearLayout(context),
            onSearchMessageContentsClickListener = { searchMessageContentsClickCount++ },
        )
    }

    private fun View.layoutInWindow() {
        // Posted runnables only run for views attached to a window
        if (!isAttachedToWindow) {
            Robolectric.buildActivity(Activity::class.java).setup().get().setContentView(this)
        }
        repeat(2) {
            measure(
                View.MeasureSpec.makeMeasureSpec(ROW_WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            layout(0, 0, measuredWidth, measuredHeight)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private val View.titleView: TextView get() = findViewById(R.id.search_status_text)
    private val View.spinnerView: View get() = findViewById(R.id.search_status_spinner)
    private val View.iconView: View get() = findViewById(R.id.search_status_icon)
    private val View.progressGroupView: View get() = findViewById(R.id.search_status_progress_group)
    private val View.progressView: View get() = findViewById(R.id.search_status_progress)
    private val View.folderView: TextView get() = findViewById(R.id.search_status_folder)
    private val View.detailView: TextView get() = findViewById(R.id.search_status_detail)
    private val View.messageView: TextView get() = findViewById(R.id.search_status_message)
    private val View.contentsButtonView: View get() = findViewById(R.id.search_contents_button)

    private companion object {
        val STATUSES = listOf(
            ServerSearchStatus.Running(title = "Searching on this device"),
            ServerSearchStatus.Running(
                title = "Server search: folder 3 of 10",
                progress = ServerSearchStatus.Progress(folderIndex = 3, folderCount = 10, folderLabel = "Projects"),
            ),
            ServerSearchStatus.Running(
                title = LONG_TEXT,
                progress = ServerSearchStatus.Progress(folderIndex = 3, folderCount = 10, folderLabel = LONG_TEXT),
                detail = LONG_TEXT,
            ),
            ServerSearchStatus.Ended(
                title = "Server search finished",
                result = Result.COMPLETE,
                isMessageContentsSearchOffered = true,
            ),
            ServerSearchStatus.Ended(title = "Content search finished", result = Result.COMPLETE),
            ServerSearchStatus.Ended(
                title = "Server search stopped",
                result = Result.STOPPED,
                message = "Pull down to search again.",
                isMessageContentsSearchOffered = true,
            ),
            ServerSearchStatus.Ended(title = "Remote search failed", result = Result.PROBLEM, message = LONG_TEXT),
            ServerSearchStatus.Ended(
                title = LONG_TEXT,
                result = Result.PROBLEM,
                message = LONG_TEXT,
                isMessageContentsSearchOffered = true,
            ),
        )
    }
}
