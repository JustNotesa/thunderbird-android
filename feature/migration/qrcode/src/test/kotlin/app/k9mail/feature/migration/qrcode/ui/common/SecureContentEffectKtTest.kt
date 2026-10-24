package app.k9mail.feature.migration.qrcode.ui.common

import android.app.Activity
import android.app.Application
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SecureContentEffectKtTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `when the effect is composed, the window should be secure`() {
        composeTestRule.setContent {
            SecureContentEffect()
        }

        assertThat(composeTestRule.activity.isSecure()).isTrue()
    }

    @Test
    fun `when the effect leaves the composition, the window should not be secure anymore`() {
        var isComposed by mutableStateOf(true)

        composeTestRule.setContent {
            if (isComposed) {
                SecureContentEffect()
            }
        }

        assertThat(composeTestRule.activity.isSecure()).isTrue()

        composeTestRule.runOnIdle { isComposed = false }
        composeTestRule.waitForIdle()

        assertThat(composeTestRule.activity.isSecure()).isFalse()
    }

    @Test
    fun `when the window was secure before, it should stay secure after the effect left the composition`() {
        val activity = composeTestRule.activity
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        var isComposed by mutableStateOf(true)

        composeTestRule.setContent {
            if (isComposed) {
                SecureContentEffect()
            }
        }

        composeTestRule.runOnIdle { isComposed = false }
        composeTestRule.waitForIdle()

        assertThat(activity.isSecure()).isTrue()
    }
}

private fun Activity.isSecure(): Boolean {
    return window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
}
