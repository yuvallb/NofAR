plugins {
    alias(libs.plugins.nofar.android.feature)
}

android {
    namespace = "com.nofar.feature.prepare"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:location"))
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.osmdroid.android)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
