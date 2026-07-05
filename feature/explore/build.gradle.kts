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
    implementation(project(":core:data"))
    implementation(project(":core:location"))
    implementation(project(":core:sensors"))
    implementation(project(":core:visibility"))
    implementation(libs.androidx.compose.material.icons.extended)
}
