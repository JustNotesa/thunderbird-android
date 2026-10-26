package net.thunderbird.feature.updater.internal.install

import android.content.pm.PackageInstaller
import android.os.Build
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApkSessionWriterTest {

    @Test
    fun `on Android 12 the session does not ask the user`() {
        // Act
        val testSubject = createAppUpdateSessionParams(PACKAGE_NAME, Build.VERSION_CODES.S)

        // Assert
        val requireUserAction = ReflectionHelpers.getField<Int>(testSubject, "requireUserAction")
        assertThat(requireUserAction).isEqualTo(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
    }

    @Test
    fun `on Android 11 the session leaves the user action unspecified`() {
        // Act
        val testSubject = createAppUpdateSessionParams(PACKAGE_NAME, Build.VERSION_CODES.R)

        // Assert
        val requireUserAction = ReflectionHelpers.getField<Int>(testSubject, "requireUserAction")
        assertThat(requireUserAction).isEqualTo(PackageInstaller.SessionParams.USER_ACTION_UNSPECIFIED)
    }

    @Test
    fun `the session installs the app itself`() {
        // Act
        val testSubject = createAppUpdateSessionParams(PACKAGE_NAME, Build.VERSION_CODES.S)

        // Assert
        val appPackageName = ReflectionHelpers.getField<String>(testSubject, "appPackageName")
        assertThat(appPackageName).isEqualTo(PACKAGE_NAME)
    }

    private companion object {
        const val PACKAGE_NAME = "net.thunderbird.feature.updater.internal"
    }
}
