package net.thunderbird.feature.navigation.drawer.dropdown.domain.entity

/**
 * The folder of a single account inside a [UnifiedDisplayFolder]. It's shown with the account name, in the tree layout.
 */
internal data class UnifiedAccountFolder(
    val accountName: String,
    val folder: MailDisplayFolder,
)
