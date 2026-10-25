plugins {
    id(ThunderbirdPlugins.Library.androidCompose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "net.thunderbird.feature.updater.internal"
    resourcePrefix = "updater_"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    api(projects.feature.updater.api)

    implementation(projects.core.common)
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.work.testing)
}

codeCoverage {
    branchCoverage = 0
    lineCoverage = 0
}
