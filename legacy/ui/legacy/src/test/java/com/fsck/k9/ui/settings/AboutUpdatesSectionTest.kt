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
import net.thunderbird.feature.updater.api.UpdateInstallFailure
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
    fun `release notes don't contain markdown bold markers`() {
        val releaseNotes = "A **bold** and __strong__ text"

        val result = formatReleaseNotes(releaseNotes)

        assertThat(result).isEqualTo("A bold and strong text")
    }

    @Test
    fun `release notes don't contain markdown code markers`() {
        val releaseNotes = "Use the `fossBeta` variant"

        val result = formatReleaseNotes(releaseNotes)

        assertThat(result).isEqualTo("Use the fossBeta variant")
    }

    @Test
    fun `release notes only keep the text of markdown links`() {
        val releaseNotes = "See [the changelog](https://github.com/example/repo/releases) now"

        val result = formatReleaseNotes(releaseNotes)

        assertThat(result).isEqualTo("See the changelog now")
    }

    @Test
    fun `release notes keep list markers and line breaks`() {
        val releaseNotes = "- **First** change\n1. Second `change`"

        val result = formatReleaseNotes(releaseNotes)

        assertThat(result).isEqualTo("- First change\n1. Second change")
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
    fun `the download doesn't offer a button to check the status`() {
        val composeView = createComposeView(
            install = UpdateInstallState.Downloading(downloadedBytes = 50, totalBytes = 100),
        )

        assertThat(findTextNode(composeView, CHECK_STATUS_BUTTON_LABEL)).isNull()
    }

    @Test
    fun `the confirmation of the installation shows that the app is opened again afterwards`() {
        val composeView = createComposeView(install = UpdateInstallState.WaitingForConfirmation)

        assertThat(findTextNode(composeView, REOPEN_HINT)).isNotNull()
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
        assertThat(findTextNode(composeView, REOPEN_HINT)).isNotNull()
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
    fun `the failure of an installation shows the version and the time`() {
        val composeView = createComposeView(
            install = UpdateInstallState.Failed(
                reason = UpdateInstallFailure.DOWNLOAD,
                tag = FAILED_TAG,
                failedAtMillis = FAILED_AT_MILLIS,
            ),
        )

        val detailsNode = findTextNode(composeView, "Version $FAILED_TAG, ")

        assertThat(detailsNode).isNotNull()
    }

    @Test
    fun `the failure of an installation without a version and time shows no details line`() {
        val composeView = createComposeView(install = UpdateInstallState.Failed(UpdateInstallFailure.DOWNLOAD))

        val detailsNode = findTextNode(composeView, "Version $FAILED_TAG")

        assertThat(detailsNode).isNull()
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

    @Test
    fun `the release notes of an available update are only shown after tapping the button`() {
        val composeView = createComposeView(availableReleaseNotes = LONG_RELEASE_NOTES)

        val notesNode = findTextNode(composeView, LONG_RELEASE_NOTES_END)
        val buttonNode = findTextNode(composeView, SHOW_NOTES_BUTTON_LABEL)

        assertThat(notesNode).isNull()
        assertThat(buttonNode).isNotNull()
    }

    @Test
    fun `the button to show the release notes is hidden when there are no release notes`() {
        val composeView = createComposeView(availableReleaseNotes = "# Only a heading")

        val buttonNode = findTextNode(composeView, SHOW_NOTES_BUTTON_LABEL)

        assertThat(buttonNode).isNull()
    }

    @Test
    fun `the full release notes of an available update are shown in a dialog that can be closed`() {
        val composeView = createComposeView(availableReleaseNotes = LONG_RELEASE_NOTES)
        val buttonNode = findTextNode(composeView, SHOW_NOTES_BUTTON_LABEL)
        assertThat(buttonNode).isNotNull()

        tap(composeView, buttonNode!!.boundsInRoot.center)

        val notesNode = findDialogTextNode(LONG_RELEASE_NOTES_END)
        val closeNode = findDialogTextNode(CLOSE_BUTTON_LABEL)
        assertThat(notesNode).isNotNull()
        assertThat(closeNode).isNotNull()

        tapDialog(closeNode!!.boundsInRoot.center)

        assertThat(findDialogTextNode(LONG_RELEASE_NOTES_END)).isNull()
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
        val decorView = ShadowDialog.getLatestDialog()?.window?.decorView ?: return null

        return findRootView(decorView)
    }

    private fun findRootView(view: View): View? {
        return when (view) {
            is ViewRootForTest -> view
            is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findRootView(view.getChildAt(it)) }
            else -> null
        }
    }

    private fun createComposeView(
        install: UpdateInstallState = UpdateInstallState.Idle,
        installedNotes: InstalledNotesStatus = InstalledNotesStatus.Hidden,
        availableReleaseNotes: String = "Release notes",
        onBackgroundDownloadChange: (Boolean) -> Unit = {},
        onCancelInstallClick: () -> Unit = {},
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
                        check = CheckStatus.Available(update = createAppUpdate(availableReleaseNotes)),
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

    private fun createAppUpdate(releaseNotes: String) = AppUpdate(
        tag = VERSION_TAG,
        title = "Thunderbird 9.9.9",
        releaseNotes = releaseNotes,
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
        const val FAILED_TAG = "mein-thunderbird-2026-11-21"
        const val FAILED_AT_MILLIS = 1_700_000_000_000L
        const val CANCEL_BUTTON_LABEL = "Cancel"
        const val CHECK_STATUS_BUTTON_LABEL = "Check status"
        const val REOPEN_HINT = "to open it again"
        const val CLOSE_BUTTON_LABEL = "Close"
        const val INSTALL_BUTTON_LABEL = "Download and install"
        const val INSTALL_NOW_BUTTON_LABEL = "Install now"
        const val INSTALLING_PROGRESS_TEXT = "Android is installing the update…"
        const val INSTALLING_HINT_TEXT = "tap the notification “Thunderbird updated”"
        const val SHOW_NOTES_BUTTON_LABEL = "Show release notes"
        const val LONG_RELEASE_NOTES_END = "The very end of the long release notes"
        val LONG_RELEASE_NOTES = "Fixed: ".padEnd(400, 'x') + "\n\n" + LONG_RELEASE_NOTES_END
        const val RELEASE_NOTES_TEXT = "Some release notes of the installed version"
        const val RELEASE_NOTES_URL = "https://github.com/JustNotesa/thunderbird-android/releases/tag/1.0"
        const val WAITING_FOR_NETWORK_TEXT = "Waiting for an internet connection…"
        const val MAX_LOOPER_TASKS = 500
        const val WIDTH = 1080
        const val HEIGHT = 1920
    }
}
