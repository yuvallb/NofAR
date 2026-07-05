plugins {
    alias(libs.plugins.nofar.android.feature)
}

android {
    namespace = "com.nofar.feature.explore"

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:location"))
    implementation(project(":core:sensors"))
    implementation(libs.androidx.compose.material.icons.extended)
}
