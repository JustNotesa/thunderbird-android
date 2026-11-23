package com.fsck.k9.ui.messagelist

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Test

class SearchScopeTest {
    @Test
    fun `search from a single folder should offer the scope and start everywhere`() {
        val result = initialSearchScope(isSingleFolderMode = true, isUnifiedFolders = false)

        assertThat(result).isEqualTo(SearchScope.EVERYWHERE)
    }

    @Test
    fun `search from the unified folders should not offer a scope`() {
        val result = initialSearchScope(isSingleFolderMode = true, isUnifiedFolders = true)

        assertThat(result).isNull()
    }

    @Test
    fun `search from several folders should not offer a scope`() {
        val result = initialSearchScope(isSingleFolderMode = false, isUnifiedFolders = false)

        assertThat(result).isNull()
    }
}
