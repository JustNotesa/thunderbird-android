package net.thunderbird.feature.updater.internal.background

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import java.util.concurrent.TimeUnit
import net.thunderbird.components.core.logging.testing.TestLogger
import org.junit.Test

private const val NOW_MILLIS = 1_700_000_000_000L

class ReopenAfterUpdateTest {
    private val marker = FakeReopenMarker()
    private val launcher = FakeAppLauncher()
    private var nowMillis = NOW_MILLIS

    private val testSubject = ReopenAfterUpdate(
        marker = marker,
        launcher = launcher,
        logger = TestLogger(),
        clock = { nowMillis },
    )

    @Test
    fun `the app is opened when the marker is fresh`() {
        // Arrange
        marker.markedAt = NOW_MILLIS - TimeUnit.MINUTES.toMillis(2)

        // Act
        testSubject.onAppUpdated()

        // Assert
        assertThat(launcher.launchCount).isEqualTo(1)
        assertThat(marker.markedAt).isNull()
    }

    @Test
    fun `the app isn't opened without a marker`() {
        // Act
        testSubject.onAppUpdated()

        // Assert
        assertThat(launcher.launchCount).isEqualTo(0)
    }

    @Test
    fun `the app isn't opened when the marker is older than 15 minutes`() {
        // Arrange
        marker.markedAt = NOW_MILLIS - TimeUnit.MINUTES.toMillis(16)

        // Act
        testSubject.onAppUpdated()

        // Assert
        assertThat(launcher.launchCount).isEqualTo(0)
        assertThat(marker.markedAt).isNull()
    }

    @Test
    fun `the app isn't opened when the marker is from the future`() {
        // Arrange
        marker.markedAt = NOW_MILLIS + TimeUnit.MINUTES.toMillis(1)

        // Act
        testSubject.onAppUpdated()

        // Assert
        assertThat(launcher.launchCount).isEqualTo(0)
        assertThat(marker.markedAt).isNull()
    }

    @Test
    fun `a failing start of the app is survived and the marker is removed`() {
        // Arrange
        marker.markedAt = NOW_MILLIS
        launcher.failure = SecurityException("blocked")

        // Act
        testSubject.onAppUpdated()

        // Assert
        assertThat(launcher.launchCount).isEqualTo(1)
        assertThat(marker.markedAt).isNull()
    }

    @Test
    fun `an app without an activity to open is survived`() {
        // Arrange
        marker.markedAt = NOW_MILLIS
        launcher.result = false

        // Act
        testSubject.onAppUpdated()

        // Assert
        assertThat(launcher.launchCount).isEqualTo(1)
        assertThat(marker.markedAt).isNull()
    }
}

internal class FakeReopenMarker(var markedAt: Long? = null, private val clock: () -> Long = { NOW_MILLIS }) :
    ReopenMarker {
    override fun mark() {
        markedAt = clock()
    }

    override fun markedAtMillis(): Long? = markedAt

    override fun clear() {
        markedAt = null
    }
}

internal class FakeAppLauncher : AppLauncher {
    var launchCount = 0
        private set
    var result = true
    var failure: Exception? = null

    override fun launch(): Boolean {
        launchCount++
        failure?.let { throw it }
        return result
    }
}
