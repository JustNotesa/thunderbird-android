package com.fsck.k9.ui.settings.search

/**
 * Turns the preference tree of a settings XML file into a flat list of searchable entries.
 */
internal object SettingsSearchIndexBuilder {
    /**
     * @param root The root screen. Its own title is not part of any breadcrumb.
     * @param scope Which settings the tree belongs to. For an account, the account name starts every breadcrumb.
     * @param excludedKeys Keys of preferences (and screens, and categories with everything in them) that are not
     *   shown to the user and therefore must not be found.
     * @param noInlineToggleKeys Keys of switches whose screen does more than saving the value (e.g. opens a dialog).
     *   Those can only be changed on their own screen.
     */
    fun build(
        root: SettingsNode,
        scope: SettingsScope,
        excludedKeys: Set<String> = emptySet(),
        noInlineToggleKeys: Set<String> = emptySet(),
    ): List<SettingsSearchEntry> {
        val context = Context(scope, excludedKeys, noInlineToggleKeys)
        val start = if (scope is SettingsScope.Account) listOf(scope.accountName) else emptyList()
        collect(root.children, context, start, screenKey = null)
        return context.entries
    }

    private class Context(
        val scope: SettingsScope,
        val excludedKeys: Set<String>,
        val noInlineToggleKeys: Set<String>,
    ) {
        val entries = mutableListOf<SettingsSearchEntry>()
    }

    private fun collect(nodes: List<SettingsNode>, context: Context, breadcrumb: List<String>, screenKey: String?) {
        for (node in nodes) {
            if (node.key != null && node.key in context.excludedKeys) continue

            when (node.kind) {
                SettingsNodeKind.CATEGORY -> {
                    val inner = if (node.title.isNullOrBlank()) breadcrumb else breadcrumb + node.title
                    collect(node.children, context, inner, screenKey)
                }

                SettingsNodeKind.SCREEN -> {
                    val entry = node.toEntry(context, breadcrumb, screenKey = node.key, highlightKey = null)
                    if (entry != null) context.entries.add(entry)
                    val inner = if (node.title.isNullOrBlank()) breadcrumb else breadcrumb + node.title
                    collect(node.children, context, inner, node.key)
                }

                SettingsNodeKind.PREFERENCE,
                SettingsNodeKind.TOGGLE,
                -> node.toEntry(context, breadcrumb, screenKey, highlightKey = node.key)?.let(context.entries::add)
            }
        }
    }

    private fun SettingsNode.toEntry(
        context: Context,
        breadcrumb: List<String>,
        screenKey: String?,
        highlightKey: String?,
    ): SettingsSearchEntry? {
        if (title.isNullOrBlank()) return null

        val scope = context.scope
        val identity = key ?: (breadcrumb + title).joinToString("/")
        return SettingsSearchEntry(
            id = "${scope.idPrefix()}:$identity",
            scope = scope,
            title = title,
            summary = summary?.takeIf { it.isNotBlank() },
            summaryOn = summaryOn?.takeIf { it.isNotBlank() },
            summaryOff = summaryOff?.takeIf { it.isNotBlank() },
            breadcrumb = breadcrumb,
            screenKey = screenKey,
            highlightKey = highlightKey,
            isToggle = kind == SettingsNodeKind.TOGGLE && key != null && key !in context.noInlineToggleKeys,
            dependencyKey = dependencyKey,
        )
    }

    private fun SettingsScope.idPrefix(): String = when (this) {
        SettingsScope.General -> "general"
        is SettingsScope.Account -> "account-$accountId"
    }
}
