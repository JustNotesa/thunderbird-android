package com.fsck.k9.ui.settings

import android.app.Application
import android.os.Looper
import android.os.SystemClock
import android.text.format.Formatter
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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
import com.fsck.k9.ui.R
import java.util.Locale
import net.thunderbird.components.ui.bolt.theme.thunderbird.ThunderbirdBoltTheme
import net.thunderbird.feature.updater.api.AppUpdate
import net.thunderbird.feature.updater.api.DownloadWaiting
import net.thunderbird.feature.updater.api.UpdateInstallState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

class AboutUpdatesSectionTest {

    private val formatSize: (Long) -> String = { bytes ->
        String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
    }

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
    fun `the download status shows the downloaded size and the total size`() {
        val status = downloadStatus(
            downloadedBytes = 12_342_000,
            totalBytes = 36_300_000,
            waiting = null,
            formatSize = formatSize,
        )

        assertThat(status.progressTextRes).isEqualTo(R.string.about_updates_downloading_progress)
        assertThat(status.progressTextArgs).containsExactly("12.3 MB", "36.3 MB", 34)
        assertThat(status.percent).isEqualTo(34)
    }

    @Test
    fun `the download status shows only the downloaded size when the total size is unknown`() {
        val status = downloadStatus(
            downloadedBytes = 12_342_000,
            totalBytes = -1,
            waiting = null,
            formatSize = formatSize,
        )

        assertThat(status.progressTextRes).isEqualTo(R.string.about_updates_downloading_progress_unknown)
        assertThat(status.progressTextArgs).containsExactly("12.3 MB")
        assertThat(status.percent).isNull()
    }

    @Test
    fun `the download status contains the reason why the download waits`() {
        val status = downloadStatus(
            downloadedBytes = 50,
            totalBytes = 100,
            waiting = DownloadWaiting.NETWORK,
            formatSize = formatSize,
        )

        assertThat(status.waitingTextRes).isEqualTo(R.string.about_updates_downloading_waiting_network)
    }

    @Test
    fun `the download status has no reason while the download is running`() {
        val status = downloadStatus(
            downloadedBytes = 50,
            totalBytes = 100,
            waiting = null,
            formatSize = formatSize,
        )

        assertThat(status.waitingTextRes).isNull()
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

    @Test
    fun `release notes aren't shortened when the shortening is turned off`() {
        val releaseNotes = "a".repeat(400)

        val result = formatReleaseNotes(releaseNotes, shorten = false)

        assertThat(result.length).isEqualTo(400)
    }

    @Test
    fun `only URLs of GitHub are opened in the browser`() {
        assertThat(isGithubReleaseUrl("https://github.com/JustNotesa/thunderbird-android")).isEqualTo(true)
        assertThat(isGithubReleaseUrl("https://example.com/mein-thunderbird.apk")).isEqualTo(false)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "en-rUS-w411dp-h891dp")
class AboutUpdatesSectionUiTest {

    @Test
    fun `the background download switch can still be toggled by tapping it`() {
        val toggles = mutableListOf<Boolean>()
        val composeView = createComposeView(onBackgroundDownloadChange = { toggles.add(it) })

        val switchNode = findNode(composeView) { node ->
            node.config.contains(SemanticsProperties.ToggleableState)
        }
        assertThat(switchNode).isNotNull()

        tap(composeView, switchNode!!.boundsInRoot.center)

        assertThat(toggles).containsExactly(true)
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
    fun `the status of the download can be checked while the update is downloading`() {
        val refreshes = mutableListOf<Boolean>()
        val composeView = createComposeView(
            install = UpdateInstallState.Downloading(downloadedBytes = 50, totalBytes = 100),
            onRefreshDownloadClick = { refreshes.add(true) },
        )

        val refreshNode = findTextNode(composeView, CHECK_STATUS_BUTTON_LABEL)
        assertThat(refreshNode).isNotNull()

        tap(composeView, refreshNode!!.boundsInRoot.center)

        assertThat(refreshes).containsExactly(true)
    }

    @Test
    fun `the download shows the downloaded size and the total size`() {
        val composeView = createComposeView(
            install = UpdateInstallState.Downloading(downloadedBytes = 12_342_000, totalBytes = 36_300_000),
        )

        val context = composeView.context
        val expectedText = String.format(
            "%s of %s (%d %%)",
            Formatter.formatShortFileSize(context, 12_342_000),
            Formatter.formatShortFileSize(context, 36_300_000),
            34,
        )
        val progressNode = findTextNode(composeView, expectedText)

        assertThat(progressNode).isNotNull()
    }

    @Test
    fun `the download shows the reason why it is waiting`() {
        val composeView = createComposeView(
            install = UpdateInstallState.Downloading(
                downloadedBytes = 12_342_000,
                totalBytes = 36_300_000,
                waiting = DownloadWaiting.NETWORK,
            ),
        )

        val waitingNode = findTextNode(composeView, WAITING_FOR_NETWORK_TEXT)

        assertThat(waitingNode).isNotNull()
    }

    @Test
    fun `the installation progress is shown without buttons while the update is installing`() {
        val composeView = createComposeView(install = UpdateInstallState.Installing)

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
    fun `no percentage is shown while the update is installing`() {
        val composeView = createComposeView(install = UpdateInstallState.Installing)

        val percentNode = findTextNode(composeView, "%")

        assertThat(percentNode).isNull()
    }

    @Test
    fun `the update that was downloaded in the background can be installed`() {
        val installs = mutableListOf<Boolean>()
        val composeView = createComposeView(
            install = UpdateInstallState.ReadyToInstall(tag = VERSION_TAG),
            onInstallDownloadedClick = { installs.add(true) },
        )

        val installNowNode = findTextNode(composeView, INSTALL_NOW_BUTTON_LABEL)
        assertThat(installNowNode).isNotNull()

        tap(composeView, installNowNode!!.boundsInRoot.center)

        assertThat(installs).containsExactly(true)
    }

    @Test
    fun `the downloaded update is shown with its release tag`() {
        val composeView = createComposeView(install = UpdateInstallState.ReadyToInstall(tag = VERSION_TAG))

        val messageNode = findTextNode(composeView, "Update $VERSION_TAG has been downloaded and is ready to install.")

        assertThat(messageNode).isNotNull()
    }

    @Test
    fun `the update isn't downloaded again while it is ready to install`() {
        val composeView = createComposeView(install = UpdateInstallState.ReadyToInstall(tag = VERSION_TAG))

        val installButtonNode = findTextNode(composeView, INSTALL_BUTTON_LABEL)

        assertThat(installButtonNode).isNull()
    }

    @Test
    fun `the release notes of the installed version are shown in a dialog that can be closed`() {
        val dismissals = mutableListOf<Boolean>()
        createComposeView(
            installedNotes = InstalledNotesStatus.Loaded(
                title = "Thunderbird 9.9.9",
                notes = RELEASE_NOTES_TEXT,
                url = RELEASE_NOTES_URL,
            ),
            onInstalledNotesDismiss = { dismissals.add(true) },
        )

        val notesNode = findDialogTextNode(RELEASE_NOTES_TEXT)
        val closeNode = findDialogTextNode(CLOSE_BUTTON_LABEL)
        assertThat(notesNode).isNotNull()
        assertThat(closeNode).isNotNull()

        tapDialog(closeNode!!.boundsInRoot.center)

        assertThat(dismissals).containsExactly(true)
    }

    private fun findTextNode(composeView: ComposeView, text: String): SemanticsNode? {
        return findNode(composeView) { node ->
            node.config.contains(SemanticsProperties.Text) &&
                node.config[SemanticsProperties.Text].joinToString { it.text }.contains(text)
        }
    }

    private fun findDialogTextNode(text: String): SemanticsNode? {
        val rootView = findDialogRootView() ?: return null
        val owner = rootView as ViewRootForTest

        return findNode(owner.semanticsOwner.unmergedRootSemanticsNode) { node ->
            node.config.contains(SemanticsProperties.Text) &&
                node.config[SemanticsProperties.Text].joinToString { it.text }.contains(text)
        }
    }

    /**
     * The dialog showing the release notes is displayed in its own window. This returns the root view of that window.
     */
    private fun findDialogRootView(): View? {
        val dialog = ShadowDialog.getLatestDialog() ?: return null
        val window = dialog.window ?: return null

        return findRootView(window.decorView)
    }

    private fun findRootView(view: View): View? {
        if (view is ViewRootForTest) return view

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findRootView(view.getChildAt(index))?.let { return it }
            }
        }

        return null
    }

    private fun createComposeView(
        install: UpdateInstallState = UpdateInstallState.Idle,
        installedNotes: InstalledNotesStatus = InstalledNotesStatus.Hidden,
        onBackgroundDownloadChange: (Boolean) -> Unit = {},
        onCancelInstallClick: () -> Unit = {},
        onRefreshDownloadClick: () -> Unit = {},
        onInstallDownloadedClick: () -> Unit = {},
        onInstalledNotesDismiss: () -> Unit = {},
    ): ComposeView {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val composeView = ComposeView(activity)
        activity.setContentView(composeView)
        composeView.setContent {
            ThunderbirdBoltTheme {
                AboutUpdatesSection(
                    state = UpdatesState(
                        currentVersionTag = VERSION_TAG,
                        backgroundDownloadEnabled = false,
                        downloadOnlyOnWifi = true,
                        autoInstallWhenIdle = false,
                        check = CheckStatus.Available(update = createAppUpdate()),
                        install = install,
                        canInstallPackages = true,
                        installedNotes = installedNotes,
                    ),
                    onBackgroundDownloadChange = onBackgroundDownloadChange,
                    onDownloadOnlyOnWifiChange = {},
                    onAutoInstallWhenIdleChange = {},
                    onInstalledVersionClick = {},
                    onRetryInstalledNotes = {},
                    onInstalledNotesDismiss = onInstalledNotesDismiss,
                    onCheckClick = {},
                    onInstallClick = {},
                    onInstallDownloadedClick = onInstallDownloadedClick,
                    onCancelInstallClick = onCancelInstallClick,
                    onRefreshDownloadClick = onRefreshDownloadClick,
                    onOpenInstallPermissionClick = {},
                    onInstallFailureDismiss = {},
                    onOpenUrl = {},
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

    private fun tapDialog(position: Offset) {
        val rootView = findDialogRootView()
        assertThat(rootView).isNotNull()

        tap(rootView!!, position)
    }

    private fun tap(view: View, position: Offset) {
        val downTime = SystemClock.uptimeMillis()
        dispatchTouchEvent(view, MotionEvent.ACTION_DOWN, position, downTime)
        dispatchTouchEvent(view, MotionEvent.ACTION_UP, position, downTime)
        idle()
    }

    private fun dispatchTouchEvent(
        view: View,
        action: Int,
        position: Offset,
        downTime: Long,
    ) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, position.x, position.y, 0)
        view.dispatchTouchEvent(event)
        event.recycle()
    }

    private fun idle() {
        // An indeterminate progress indicator animates forever and never lets the main looper become idle, so only a
        // limited number of tasks is run. That is enough to compose, lay out and handle a tap.
        val looper = shadowOf(Looper.getMainLooper())
        var tasks = 0
        while (!looper.isIdle && tasks < MAX_LOOPER_TASKS) {
            looper.runOneTask()
            tasks++
        }
    }

    companion object {
        const val VERSION_TAG = "mein-thunderbird-2026-10-25"
        const val CANCEL_BUTTON_LABEL = "Cancel"
        const val CHECK_STATUS_BUTTON_LABEL = "Check status"
        const val CLOSE_BUTTON_LABEL = "Close"
        const val INSTALL_BUTTON_LABEL = "Download and install"
        const val INSTALL_NOW_BUTTON_LABEL = "Install now"
        const val INSTALLING_PROGRESS_TEXT = "Android is installing the update…"
        const val INSTALLING_HINT_TEXT = "tap the notification “Thunderbird updated”"
        const val RELEASE_NOTES_TEXT = "Some release notes of the installed version"
        const val RELEASE_NOTES_URL = "https://github.com/JustNotesa/thunderbird-android/releases/tag/1.0"
        const val WAITING_FOR_NETWORK_TEXT = "Waiting for an internet connection…"
        const val MAX_LOOPER_TASKS = 500
        const val WIDTH = 1080
        const val HEIGHT = 1920
    }
}
