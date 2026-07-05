plugins {
    alias(libs.plugins.nofar.android.feature)
}

android {
    namespace = "com.nofar.feature.explore"
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.compose.material.icons.extended)
}
