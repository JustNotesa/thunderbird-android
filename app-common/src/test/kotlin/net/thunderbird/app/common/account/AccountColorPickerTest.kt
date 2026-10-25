package net.thunderbird.app.common.account

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.each
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isIn
import assertk.assertions.isOneOf
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import net.thunderbird.app.common.account.data.FakeAccountProfileRepository
import net.thunderbird.feature.account.AccountIdFactory
import net.thunderbird.feature.account.avatar.Avatar
import net.thunderbird.feature.account.profile.AccountProfile

class AccountColorPickerTest {

    @Test
    fun `should pick random color when none used`() = runTest {
        // Arrange
        val profiles: MutableStateFlow<List<AccountProfile>> = MutableStateFlow(emptyList())
        val testSubject = AccountColorPicker(
            repository = FakeAccountProfileRepository(profiles),
            accountColors = ACCOUNT_COLORS,
        )

        // Act
        val result = testSubject.pickColor()

        // Assert
        assertThat(result).isOneOf(COLOR_RED, COLOR_GREEN, COLOR_BLUE)
    }

    @Test
    fun `should pick one of the available colors when some are used`() = runTest {
        // Arrange
        val profiles: MutableStateFlow<List<AccountProfile>> = MutableStateFlow(
            listOf(
                ACCOUNT_PROFILE_GREEN_1,
            ),
        )
        val testSubject = AccountColorPicker(
            repository = FakeAccountProfileRepository(profiles),
            accountColors = ACCOUNT_COLORS,
        )

        // Act
        val result = testSubject.pickColor()

        // Assert
        assertThat(result).isOneOf(COLOR_RED, COLOR_BLUE)
    }

    @Test
    fun `should pick last available color when others are used`() = runTest {
        // Arrange
        val profiles: MutableStateFlow<List<AccountProfile>> = MutableStateFlow(
            listOf(
                ACCOUNT_PROFILE_RED_1,
                ACCOUNT_PROFILE_GREEN_1,
            ),
        )
        val testSubject = AccountColorPicker(
            repository = FakeAccountProfileRepository(profiles),
            accountColors = ACCOUNT_COLORS,
        )

        // Act
        val result = testSubject.pickColor()

        // Assert
        assertThat(result).isEqualTo(COLOR_BLUE)
    }

    @Test
    fun `should pick random color when all colors are used equally`() = runTest {
        // Arrange
        val profiles: MutableStateFlow<List<AccountProfile>> = MutableStateFlow(
            listOf(
                ACCOUNT_PROFILE_RED_1,
                ACCOUNT_PROFILE_GREEN_1,
                ACCOUNT_PROFILE_BLUE_1,
            ),
        )
        val testSubject = AccountColorPicker(
            repository = FakeAccountProfileRepository(profiles),
            accountColors = ACCOUNT_COLORS,
        )

        // Act
        val result = testSubject.pickColor()

        // Assert
        assertThat(result).isOneOf(COLOR_RED, COLOR_GREEN, COLOR_BLUE)
    }

    @Test
    fun `should pick from least used colors when colors are used multiple times`() = runTest {
        // Arrange
        val profiles: MutableStateFlow<List<AccountProfile>> = MutableStateFlow(
            listOf(
                ACCOUNT_PROFILE_RED_1,
                ACCOUNT_PROFILE_RED_2,
                ACCOUNT_PROFILE_GREEN_1,
                ACCOUNT_PROFILE_GREEN_2,
                ACCOUNT_PROFILE_BLUE_1,
            ),
        )
        val testSubject = AccountColorPicker(
            repository = FakeAccountProfileRepository(profiles),
            accountColors = ACCOUNT_COLORS,
        )

        // Act
        val result = testSubject.pickColor()

        // Assert
        assertThat(result).isEqualTo(COLOR_BLUE)
    }

    @Test
    fun `should pick distinct least used colors for a batch of accounts`() = runTest {
        // Arrange
        val profiles: MutableStateFlow<List<AccountProfile>> = MutableStateFlow(
            listOf(
                ACCOUNT_PROFILE_RED_1,
                ACCOUNT_PROFILE_RED_2,
            ),
        )
        val testSubject = AccountColorPicker(
            repository = FakeAccountProfileRepository(profiles),
            accountColors = BATCH_ACCOUNT_COLORS,
        )

        // Act
        val result = testSubject.pickColors(3)

        // Assert
        assertThat(result).hasSize(3)
        assertThat(result.toSet()).hasSize(3)
        assertThat(result).containsOnly(COLOR_GREEN, COLOR_BLUE, COLOR_YELLOW)
    }

    @Test
    fun `should pick a different color for every account of a batch`() = runTest {
        // Arrange
        val profiles: MutableStateFlow<List<AccountProfile>> = MutableStateFlow(
            listOf(
                ACCOUNT_PROFILE_RED_1,
                ACCOUNT_PROFILE_GREEN_1,
                ACCOUNT_PROFILE_BLUE_1,
            ),
        )
        val testSubject = AccountColorPicker(
            repository = FakeAccountProfileRepository(profiles),
            accountColors = ACCOUNT_COLORS,
        )

        // Act
        val result = testSubject.pickColors(2)

        // Assert
        assertThat(result).hasSize(2)
        assertThat(result.toSet()).hasSize(2)
        assertThat(result).each { color -> color.isIn(COLOR_RED, COLOR_GREEN, COLOR_BLUE) }
    }

    private companion object {
        const val COLOR_RED = 0xFF0000
        const val COLOR_GREEN = 0x00FF00
        const val COLOR_BLUE = 0x0000FF
        const val COLOR_YELLOW = 0xFFFF00

        val ACCOUNT_COLORS = persistentListOf(
            COLOR_RED,
            COLOR_GREEN,
            COLOR_BLUE,
        )

        val BATCH_ACCOUNT_COLORS = persistentListOf(
            COLOR_RED,
            COLOR_GREEN,
            COLOR_BLUE,
            COLOR_YELLOW,
        )

        val ACCOUNT_PROFILE_RED_1 = AccountProfile(
            id = AccountIdFactory.create(),
            name = "Account Red 1",
            color = COLOR_RED,
            avatar = Avatar.Icon(name = "icon1"),
        )
        val ACCOUNT_PROFILE_RED_2 = AccountProfile(
            id = AccountIdFactory.create(),
            name = "Account Red 2",
            color = COLOR_RED,
            avatar = Avatar.Icon(name = "icon4"),
        )

        val ACCOUNT_PROFILE_GREEN_1 = AccountProfile(
            id = AccountIdFactory.create(),
            name = "Account Green 1",
            color = COLOR_GREEN,
            avatar = Avatar.Icon(name = "icon2"),
        )

        val ACCOUNT_PROFILE_GREEN_2 = AccountProfile(
            id = AccountIdFactory.create(),
            name = "Account Green 2",
            color = COLOR_GREEN,
            avatar = Avatar.Icon(name = "icon5"),
        )

        val ACCOUNT_PROFILE_BLUE_1 = AccountProfile(
            id = AccountIdFactory.create(),
            name = "Account Blue 1",
            color = COLOR_BLUE,
            avatar = Avatar.Icon(name = "icon3"),
        )
    }
}
