plugins {
    alias(libs.plugins.nofar.android.library)
    alias(libs.plugins.nofar.android.lint)
    alias(libs.plugins.nofar.detekt)
}

android {
    namespace = "com.nofar.core.network"
}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.moshi)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
