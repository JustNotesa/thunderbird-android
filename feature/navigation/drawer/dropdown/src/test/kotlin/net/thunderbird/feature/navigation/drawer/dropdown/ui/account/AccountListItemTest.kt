package net.thunderbird.feature.navigation.drawer.dropdown.ui.account

import androidx.compose.ui.test.performClick
import app.k9mail.core.ui.compose.testing.ComposeTest
import app.k9mail.core.ui.compose.testing.onNodeWithContentDescription
import app.k9mail.core.ui.compose.testing.onNodeWithText
import app.k9mail.core.ui.compose.testing.setContentWithTheme
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import kotlin.test.Test
import net.thunderbird.feature.navigation.drawer.dropdown.R
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.DisplayAccount
import net.thunderbird.feature.navigation.drawer.dropdown.ui.FakeData.MAIL_DISPLAY_ACCOUNT

internal class AccountListItemTest : ComposeTest() {

    @Test
    fun `clicking the avatar should open the inbox of the account and not select the account`() {
        val clickedAccounts = mutableListOf<DisplayAccount>()
        val avatarClickedAccounts = mutableListOf<DisplayAccount>()
        setAccountListItem(
            onClick = { clickedAccounts += it },
            onAvatarClick = { avatarClickedAccounts += it },
        )

        onNodeWithContentDescription(
            label = getString(R.string.navigation_drawer_dropdown_account_open_inbox_action),
            useUnmergedTree = true,
        ).performClick()

        assertThat(avatarClickedAccounts).containsExactly(MAIL_DISPLAY_ACCOUNT)
        assertThat(clickedAccounts).isEmpty()
    }

    @Test
    fun `clicking the account name should select the account and not open the inbox`() {
        val clickedAccounts = mutableListOf<DisplayAccount>()
        val avatarClickedAccounts = mutableListOf<DisplayAccount>()
        setAccountListItem(
            onClick = { clickedAccounts += it },
            onAvatarClick = { avatarClickedAccounts += it },
        )

        onNodeWithText(MAIL_DISPLAY_ACCOUNT.name).performClick()

        assertThat(clickedAccounts).containsExactly(MAIL_DISPLAY_ACCOUNT)
        assertThat(avatarClickedAccounts).isEmpty()
    }

    private fun setAccountListItem(
        onClick: (DisplayAccount) -> Unit,
        onAvatarClick: (DisplayAccount) -> Unit,
    ) {
        setContentWithTheme {
            AccountListItem(
                account = MAIL_DISPLAY_ACCOUNT,
                onClick = onClick,
                onAvatarClick = onAvatarClick,
                selected = false,
                showStarredCount = false,
            )
        }
    }
}
