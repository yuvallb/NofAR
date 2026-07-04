plugins {
    alias(libs.plugins.nofar.jvm.library)
    alias(libs.plugins.nofar.detekt)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
