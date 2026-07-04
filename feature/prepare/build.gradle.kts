plugins {
    alias(libs.plugins.nofar.android.feature)
}

android {
    namespace = "com.nofar.feature.prepare"
}

dependencies {
    implementation(project(":core:model"))
}
