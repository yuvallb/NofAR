plugins {
    alias(libs.plugins.nofar.rust)
    alias(libs.plugins.nofar.detekt)
}

android {
    namespace = "com.nofar.core.ffi"
    lint {
        lintConfig = file("lint.xml")
    }
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    sourceSets {
        getByName("main") {
            java.srcDir("src/generated/kotlin")
            kotlin.srcDir("src/generated/kotlin")
        }
    }
}

detekt {
    source.setFrom(
        files(
            "src/main/kotlin",
            "src/test/kotlin",
            "src/androidTest/kotlin"
        )
    )
}

dependencies {
    implementation(libs.jna)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
