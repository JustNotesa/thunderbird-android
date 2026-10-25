plugins {
    id(ThunderbirdPlugins.Library.kmp)
}

kotlin {
    android {
        namespace = "net.thunderbird.feature.updater.api"
    }
}

codeCoverage {
    branchCoverage = 0
    lineCoverage = 0
}
