import java.util.Properties

plugins {
    alias(libs.plugins.nofar.android.application)
    alias(libs.plugins.nofar.android.hilt)
    alias(libs.plugins.nofar.android.compose)
    alias(libs.plugins.nofar.android.lint)
    alias(libs.plugins.nofar.detekt)
}

// applicationId is the public store identity — do not change after first Play/F-Droid upload.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties =
    Properties().apply {
        if (keystorePropertiesFile.exists()) {
            keystorePropertiesFile.inputStream().use(::load)
        }
    }
val hasReleaseKeystore =
    keystorePropertiesFile.exists() &&
        keystoreProperties.getProperty("storeFile") != null &&
        keystoreProperties.getProperty("storePassword") != null &&
        keystoreProperties.getProperty("keyAlias") != null &&
        keystoreProperties.getProperty("keyPassword") != null

android {
    namespace = "com.nofar.app"
    defaultConfig {
        applicationId = "com.nofar.app"
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // CI without secrets keeps assembleRelease working via debug signing.
            // Play uploads must use keystore.properties (never commit it).
            signingConfig =
                if (hasReleaseKeystore) {
                    signingConfigs.getByName("release")
                } else {
                    signingConfigs.getByName("debug")
                }
        }
    }
}

// Merged AndroidX baseline profiles are non-deterministic, and their startup ordering also
// perturbs classes.dex, which breaks F-Droid reproducible-build verification.
tasks.matching { it.name.contains("ArtProfile") }.configureEach {
    enabled = false
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

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.sqlite.bundled)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
