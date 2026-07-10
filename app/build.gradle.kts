plugins {
    alias(libs.plugins.nofar.android.application)
    alias(libs.plugins.nofar.android.hilt)
    alias(libs.plugins.nofar.android.compose)
    alias(libs.plugins.nofar.android.lint)
    alias(libs.plugins.nofar.detekt)
}

android {
    namespace = "com.nofar.app"
    defaultConfig {
        applicationId = "com.nofar.app"
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations += listOf("en", "iw")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:location"))
    implementation(project(":core:sensors"))
    implementation(project(":core:visibility"))
    implementation(project(":core:ui"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:home"))
    implementation(project(":feature:prepare"))
    implementation(project(":feature:explore"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
