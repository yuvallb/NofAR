plugins {
    alias(libs.plugins.nofar.android.feature)
}

android {
    namespace = "com.nofar.feature.settings"
}

dependencies {
    implementation(project(":core:model"))
}
