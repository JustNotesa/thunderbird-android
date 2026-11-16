package com.fsck.k9.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fsck.k9.ui.R
import net.thunderbird.components.ui.bolt.atom.CircularProgressIndicator
import net.thunderbird.components.ui.bolt.atom.button.ButtonText
import net.thunderbird.components.ui.bolt.atom.text.TextBodyMedium
import net.thunderbird.components.ui.bolt.atom.text.TextHeadlineSmall
import net.thunderbird.components.ui.bolt.atom.text.TextTitleMedium
import net.thunderbird.components.ui.bolt.organism.BasicDialog
import net.thunderbird.components.ui.bolt.theme.BoltTheme

private const val GITHUB_URL_PREFIX = "https://github.com/"
private const val MAX_RELEASE_NOTES_LENGTH = 300

/**
 * The row showing the release tag of the installed version. Tapping it shows the release notes of that version.
 */
@Composable
internal fun AboutUpdateInstalledVersionRow(
    versionTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = BoltTheme.spacings.double,
                vertical = BoltTheme.spacings.half,
            ),
        verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.quarter),
    ) {
        TextTitleMedium(text = stringResource(R.string.about_updates_installed_version))
        TextBodyMedium(text = versionTag)
        TextBodyMedium(text = stringResource(R.string.about_updates_installed_version_hint))
    }
}

/**
 * The dialog showing the release notes of the installed version. It is shown as long as the release notes aren't
 * hidden.
 */
@Composable
internal fun AboutUpdateInstalledNotesDialog(
    notes: InstalledNotesStatus,
    onRetryClick: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (notes is InstalledNotesStatus.Hidden) return

    BasicDialog(
        onDismissRequest = onDismiss,
        headline = {
            TextHeadlineSmall(text = installedNotesHeadline(notes))
        },
        content = {
            AboutUpdateInstalledNotesContent(notes = notes)
        },
        buttons = {
            AboutUpdateInstalledNotesButtons(
                notes = notes,
                onRetryClick = onRetryClick,
                onOpenUrl = onOpenUrl,
                onDismiss = onDismiss,
            )
        },
        contentPadding = PaddingValues(
            start = BoltTheme.spacings.triple,
            top = BoltTheme.spacings.oneHalf,
            end = BoltTheme.spacings.triple,
            bottom = BoltTheme.spacings.double,
        ),
    )
}

/**
 * The title of the dialog. While the release notes are loading or after loading them failed the title of the installed
 * version is shown.
 */
@Composable
private fun installedNotesHeadline(notes: InstalledNotesStatus): String {
    return when (notes) {
        is InstalledNotesStatus.Loaded -> notes.title

        InstalledNotesStatus.Loading, is InstalledNotesStatus.Failed, InstalledNotesStatus.Hidden -> {
            stringResource(R.string.about_updates_installed_version)
        }
    }
}

/**
 * The content of the dialog showing the release notes of the installed version.
 */
@Composable
private fun AboutUpdateInstalledNotesContent(
    notes: InstalledNotesStatus,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        when (notes) {
            InstalledNotesStatus.Hidden -> Unit

            InstalledNotesStatus.Loading -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(INDICATOR_SIZE.dp))
                    Spacer(modifier = Modifier.width(BoltTheme.spacings.half))
                    TextBodyMedium(text = stringResource(R.string.about_updates_notes_loading))
                }
            }

            is InstalledNotesStatus.Loaded -> {
                SelectionContainer {
                    TextBodyMedium(text = formatReleaseNotes(notes.notes, shorten = false))
                }
            }

            is InstalledNotesStatus.Failed -> {
                TextBodyMedium(text = stringResource(checkFailureMessageRes(notes.reason)))
            }
        }
    }
}

/**
 * The buttons of the dialog showing the release notes of the installed version.
 */
@Composable
private fun AboutUpdateInstalledNotesButtons(
    notes: InstalledNotesStatus,
    onRetryClick: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (notes is InstalledNotesStatus.Loaded) {
        val url = notes.url
        if (isGithubReleaseUrl(url)) {
            ButtonText(
                text = stringResource(R.string.about_updates_notes_open_on_github),
                onClick = { onOpenUrl(url) },
            )
        }
    }

    if (notes is InstalledNotesStatus.Failed) {
        ButtonText(
            text = stringResource(R.string.about_updates_retry_button),
            onClick = onRetryClick,
        )
    }

    ButtonText(
        text = stringResource(R.string.about_updates_notes_close),
        onClick = onDismiss,
    )
}

/**
 * Whether the URL points to a release page on GitHub. Only those URLs are opened in the browser.
 */
internal fun isGithubReleaseUrl(url: String): Boolean {
    return url.startsWith(GITHUB_URL_PREFIX)
}

/**
 * Returns the release notes without Markdown headings. When [shorten] is `true` the notes are shortened to
 * [MAX_RELEASE_NOTES_LENGTH] characters.
 */
internal fun formatReleaseNotes(releaseNotes: String, shorten: Boolean = true): String {
    val withoutHeadings = releaseNotes.lineSequence()
        .filterNot { line -> line.trimStart().startsWith("#") }
        .joinToString(separator = "\n")
        .trim()

    return if (shorten && withoutHeadings.length > MAX_RELEASE_NOTES_LENGTH) {
        withoutHeadings.take(MAX_RELEASE_NOTES_LENGTH).trimEnd() + "…"
    } else {
        withoutHeadings
    }
}
