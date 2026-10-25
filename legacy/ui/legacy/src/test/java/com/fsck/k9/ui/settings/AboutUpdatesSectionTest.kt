package com.fsck.k9.ui.settings

import android.app.Application
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import net.thunderbird.components.ui.bolt.theme.thunderbird.ThunderbirdBoltTheme
import net.thunderbird.feature.updater.api.AppUpdate
import net.thunderbird.feature.updater.api.UpdateInstallState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

class AboutUpdatesSectionTest {

    @Test
    fun `download percent is calculated from the downloaded and the total number of bytes`() {
        assertThat(calculateDownloadPercent(downloadedBytes = 50, totalBytes = 200)).isEqualTo(25)
    }

    @Test
    fun `download percent is null when the total size is unknown`() {
        assertThat(calculateDownloadPercent(downloadedBytes = 50, totalBytes = 0)).isNull()
        assertThat(calculateDownloadPercent(downloadedBytes = 50, totalBytes = -1)).isNull()
    }

    @Test
    fun `download percent never exceeds 100 percent`() {
        assertThat(calculateDownloadPercent(downloadedBytes = 300, totalBytes = 200)).isEqualTo(100)
    }

    @Test
    fun `release notes don't contain markdown headings`() {
        val releaseNotes = "# Release notes\nSome notes"

        assertThat(formatReleaseNotes(releaseNotes)).isEqualTo("Some notes")
    }

    @Test
    fun `long release notes are shortened`() {
        val releaseNotes = "a".repeat(400)

        val result = formatReleaseNotes(releaseNotes)

        assertThat(result.length).isEqualTo(301)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp")
class AboutUpdatesSectionUiTest {

    @Test
    fun `auto check switch can still be toggled by tapping it`() {
        val toggles = mutableListOf<Boolean>()
        val composeView = createComposeView(onAutoCheckChange = { toggles.add(it) })

        val switchNode = findNode(composeView) { node ->
            node.config.contains(SemanticsProperties.ToggleableState)
        }
        assertThat(switchNode).isNotNull()

        tap(composeView, switchNode!!.boundsInRoot.center)

        assertThat(toggles).containsExactly(false)
    }

    @Test
    fun `the download can be cancelled while the update is downloading`() {
        val cancels = mutableListOf<Boolean>()
        val composeView = createComposeView(
            install = UpdateInstallState.Downloading(downloadedBytes = 50, totalBytes = 100),
            onCancelInstallClick = { cancels.add(true) },
        )

        val cancelNode = findNode(composeView) { node ->
            node.config.contains(SemanticsProperties.Text) &&
                node.config[SemanticsProperties.Text].joinToString { it.text }.contains(CANCEL_BUTTON_LABEL)
        }
        assertThat(cancelNode).isNotNull()

        tap(composeView, cancelNode!!.boundsInRoot.center)

        assertThat(cancels).containsExactly(true)
    }

    @Test
    fun `the installation progress is shown without buttons while the update is installing`() {
        val composeView = createComposeView(install = UpdateInstallState.Installing(progress = 0.42f))

        val progressNode = findTextNode(composeView, INSTALLING_PROGRESS_TEXT)
        val hintNode = findTextNode(composeView, INSTALLING_HINT_TEXT)
        val installButtonNode = findTextNode(composeView, INSTALL_BUTTON_LABEL)
        val cancelNode = findTextNode(composeView, CANCEL_BUTTON_LABEL)

        assertThat(progressNode).isNotNull()
        assertThat(hintNode).isNotNull()
        assertThat(installButtonNode).isNull()
        assertThat(cancelNode).isNull()
    }

    @Test
    fun `an indeterminate installation progress is shown when the progress is unknown`() {
        val composeView = createComposeView(install = UpdateInstallState.Installing(progress = -1f))

        val progressNode = findTextNode(composeView, INSTALLING_INDETERMINATE_TEXT)
        val percentNode = findTextNode(composeView, "%")

        assertThat(progressNode).isNotNull()
        assertThat(percentNode).isNull()
    }

    private fun findTextNode(composeView: ComposeView, text: String): SemanticsNode? {
        return findNode(composeView) { node ->
            node.config.contains(SemanticsProperties.Text) &&
                node.config[SemanticsProperties.Text].joinToString { it.text }.contains(text)
        }
    }

    private fun createComposeView(
        install: UpdateInstallState = UpdateInstallState.Idle,
        onAutoCheckChange: (Boolean) -> Unit = {},
        onCancelInstallClick: () -> Unit = {},
    ): ComposeView {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        composeView.setContent {
            ThunderbirdBoltTheme {
                AboutUpdatesSection(
                    state = UpdatesState(
                        currentVersionTag = VERSION_TAG,
                        autoCheckEnabled = true,
                        check = CheckStatus.Available(update = createAppUpdate()),
                        install = install,
                        canInstallPackages = true,
                    ),
                    onAutoCheckChange = onAutoCheckChange,
                    onCheckClick = {},
                    onInstallClick = {},
                    onCancelInstallClick = onCancelInstallClick,
                    onOpenInstallPermissionClick = {},
                    onInstallFailureDismiss = {},
                )
            }
        }

        composeView.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY),
        )
        composeView.layout(0, 0, WIDTH, HEIGHT)
        idle()

        return composeView
    }

    private fun createAppUpdate() = AppUpdate(
        tag = VERSION_TAG,
        title = "Thunderbird 9.9.9",
        releaseNotes = "Release notes",
        apkUrl = "https://example.com/thunderbird.apk",
        apkSizeBytes = 1024L,
        apkSha256 = null,
    )

    private fun findNode(composeView: ComposeView, predicate: (SemanticsNode) -> Boolean): SemanticsNode? {
        val owner = composeView.getChildAt(0) as ViewRootForTest
        return findNode(owner.semanticsOwner.unmergedRootSemanticsNode, predicate)
    }

    private fun findNode(node: SemanticsNode, predicate: (SemanticsNode) -> Boolean): SemanticsNode? {
        if (predicate(node)) return node

        return node.children.firstNotNullOfOrNull { findNode(it, predicate) }
    }

    private fun tap(composeView: ComposeView, position: Offset) {
        val downTime = SystemClock.uptimeMillis()
        dispatchTouchEvent(composeView, MotionEvent.ACTION_DOWN, position, downTime)
        dispatchTouchEvent(composeView, MotionEvent.ACTION_UP, position, downTime)
        idle()
    }

    private fun dispatchTouchEvent(
        composeView: ComposeView,
        action: Int,
        position: Offset,
        downTime: Long,
    ) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, position.x, position.y, 0)
        composeView.dispatchTouchEvent(event)
        event.recycle()
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    companion object {
        const val VERSION_TAG = "mein-thunderbird-2026-10-25"
        const val CANCEL_BUTTON_LABEL = "Cancel"
        const val INSTALL_BUTTON_LABEL = "Download and install"
        const val INSTALLING_PROGRESS_TEXT = "Installing… 42%"
        const val INSTALLING_INDETERMINATE_TEXT = "Installing…"
        const val INSTALLING_HINT_TEXT = "tap the notification “Thunderbird updated”"
        const val WIDTH = 1080
        const val HEIGHT = 1920
    }
}
