plugins {
    alias(libs.plugins.nofar.android.library)
    alias(libs.plugins.nofar.android.compose)
    alias(libs.plugins.nofar.android.lint)
    alias(libs.plugins.nofar.detekt)
}

android {
    namespace = "com.nofar.core.designsystem"
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.material.icons.extended)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
