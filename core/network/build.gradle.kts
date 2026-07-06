plugins {
    alias(libs.plugins.nofar.android.library)
    alias(libs.plugins.nofar.android.hilt)
    alias(libs.plugins.nofar.android.lint)
    alias(libs.plugins.nofar.detekt)
}

android {
    namespace = "com.nofar.core.network"
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.okhttp)
    implementation(libs.moshi)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
