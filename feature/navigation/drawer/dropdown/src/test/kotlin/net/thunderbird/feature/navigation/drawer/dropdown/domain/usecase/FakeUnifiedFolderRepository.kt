package net.thunderbird.feature.navigation.drawer.dropdown.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import net.thunderbird.feature.navigation.drawer.dropdown.domain.DomainContract.UnifiedFolderRepository
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.UnifiedDisplayFolder
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.UnifiedDisplayFolderType

internal class FakeUnifiedFolderRepository(
    private val displayUnifiedFolderFlow: Flow<UnifiedDisplayFolder>,
    private val unifiedFolderTypes: Flow<List<UnifiedDisplayFolderType>> = flowOf(
        listOf(UnifiedDisplayFolderType.INBOX),
    ),
    private val displayUnifiedFolderFlows: Map<UnifiedDisplayFolderType, Flow<UnifiedDisplayFolder>> = emptyMap(),
) : UnifiedFolderRepository {

    override fun getUnifiedDisplayFolderFlow(unifiedFolderType: UnifiedDisplayFolderType): Flow<UnifiedDisplayFolder> {
        return displayUnifiedFolderFlows[unifiedFolderType] ?: displayUnifiedFolderFlow
    }

    override fun getUnifiedFolderTypesFlow(): Flow<List<UnifiedDisplayFolderType>> {
        return unifiedFolderTypes
    }
}
