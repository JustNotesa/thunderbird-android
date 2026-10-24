package app.k9mail.feature.onboarding.welcome.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.thunderbird.components.ui.bolt.template.Scaffold
import net.thunderbird.core.common.provider.AppNameProvider

@Composable
fun WelcomeScreen(
    options: WelcomeOptions,
    appNameProvider: AppNameProvider,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
    ) { innerPadding ->
        WelcomeContent(
            options = options,
            appName = appNameProvider.appName,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
