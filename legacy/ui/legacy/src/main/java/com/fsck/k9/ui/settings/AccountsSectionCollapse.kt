package com.fsck.k9.ui.settings

internal const val COLLAPSE_ACCOUNTS_THRESHOLD = 5
internal const val COLLAPSED_ACCOUNTS_VISIBLE = 3

internal data class AccountsSectionCollapse(
    val visibleCount: Int,
    val hasPeek: Boolean,
    val isCollapsible: Boolean,
    val isExpanded: Boolean,
)

internal fun accountsSectionCollapse(
    accountCount: Int,
    isExpandedByUser: Boolean,
    isSelectionMode: Boolean,
): AccountsSectionCollapse {
    val isCollapsible = accountCount > COLLAPSE_ACCOUNTS_THRESHOLD
    val isExpanded = !isCollapsible || isExpandedByUser || isSelectionMode
    val visibleCount = if (isExpanded) accountCount else COLLAPSED_ACCOUNTS_VISIBLE

    return AccountsSectionCollapse(visibleCount, hasPeek = !isExpanded, isCollapsible, isExpanded)
}
