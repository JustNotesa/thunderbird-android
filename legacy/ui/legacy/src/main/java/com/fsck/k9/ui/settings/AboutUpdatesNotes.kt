package com.fsck.k9.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fsck.k9.ui.R
import net.thunderbird.components.ui.bolt.atom.CircularProgressIndicator
import net.thunderbird.components.ui.bolt.atom.button.ButtonOutlined
import net.thunderbird.components.ui.bolt.atom.text.TextBodyMedium
import net.thunderbird.components.ui.bolt.atom.text.TextTitleMedium
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
 * The release notes of the installed version. They are shown below the row with the installed version.
 */
@Composable
internal fun AboutUpdateInstalledNotes(
    notes: InstalledNotesStatus,
    onRetryClick: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BoltTheme.spacings.double),
        verticalArrangement = Arrangement.spacedBy(BoltTheme.spacings.half),
    ) {
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
                TextTitleMedium(text = notes.title)
                TextBodyMedium(text = formatReleaseNotes(notes.notes, shorten = false))
                if (isGithubReleaseUrl(notes.url)) {
                    DisableSelection {
                        ButtonOutlined(
                            text = stringResource(R.string.about_updates_notes_open_on_github),
                            onClick = { onOpenUrl(notes.url) },
                        )
                    }
                }
            }

            is InstalledNotesStatus.Failed -> {
                TextBodyMedium(text = stringResource(checkFailureMessageRes(notes.reason)))
                DisableSelection {
                    ButtonOutlined(
                        text = stringResource(R.string.about_updates_retry_button),
                        onClick = onRetryClick,
                    )
                }
            }
        }
    }
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
