import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "com.nofar.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
    // Spotless types are referenced in SpotlessConventionPlugin and must be runtime-visible.
    implementation(libs.spotless.gradlePlugin)
}

tasks {
    validatePlugins {
        enableStricterValidation = true
        failOnWarning = true
    }
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "nofar.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "nofar.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidFeature") {
            id = "nofar.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidHilt") {
            id = "nofar.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
        register("androidCompose") {
            id = "nofar.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidLint") {
            id = "nofar.android.lint"
            implementationClass = "AndroidLintConventionPlugin"
        }
        register("detekt") {
            id = "nofar.detekt"
            implementationClass = "DetektConventionPlugin"
        }
        register("spotless") {
            id = "nofar.spotless"
            implementationClass = "SpotlessConventionPlugin"
        }
        register("jvmLibrary") {
            id = "nofar.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
