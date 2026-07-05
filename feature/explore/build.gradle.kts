plugins {
    alias(libs.plugins.nofar.android.feature)
}

android {
    namespace = "com.nofar.feature.explore"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:location"))
    implementation(project(":core:sensors"))
    implementation(project(":core:visibility"))
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.truth)
}
