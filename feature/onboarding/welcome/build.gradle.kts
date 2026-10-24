plugins {
    id(ThunderbirdPlugins.Library.androidCompose)
}

android {
    namespace = "app.k9mail.feature.onboarding.welcome"
    resourcePrefix = "onboarding_welcome_"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.feature.onboarding.migration.api)

    testImplementation(projects.core.ui.compose.testing)
}

codeCoverage {
    branchCoverage = 0
    lineCoverage = 0
}
