import com.android.build.api.dsl.LibraryExtension
import com.nofar.buildlogic.configureKotlinAndroid
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

class NofarRustConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.android.library")

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                testOptions.targetSdk = 35
                lint.targetSdk = 35

                defaultConfig {
                    ndk {
                        abiFilters += listOf("arm64-v8a", "x86_64")
                    }
                }
            }

            val rustDir = rootProject.layout.projectDirectory.dir("rust")
            val generatedKotlin = layout.projectDirectory.dir("src/generated/kotlin")
            val hostLibDir = layout.buildDirectory.dir("rust/host")
            val androidLibDir = layout.buildDirectory.dir("rust/android")

            target.afterEvaluate {
                extensions.configure<LibraryExtension> {
                    sourceSets {
                        getByName("main") {
                            java.srcDir(generatedKotlin.asFile)
                            jniLibs.srcDir(
                                target.layout.buildDirectory.dir("rust/android").get().asFile
                            )
                        }
                    }
                }
            }

            val generateUniffiBindings =
                tasks.register<Exec>("generateUniffiBindings") {
                    group = "rust"
                    description = "Generate Kotlin UniFFI bindings for :core:ffi"
                    workingDir = rustDir.asFile
                    environment("NOFAR_CARGO_TARGET_DIR", rustDir.asFile.resolve("target").absolutePath)
                    commandLine(
                        "cargo",
                        "run",
                        "--quiet",
                        "--package",
                        "xtask",
                        "--",
                        "kotlin-bindings",
                        "--out-dir",
                        generatedKotlin.asFile.absolutePath
                    )
                    outputs.dir(generatedKotlin.asFile)
                }

            val cargoBuildHost =
                tasks.register<Exec>("cargoBuildHost") {
                    group = "rust"
                    description = "Build host nofar_ffi for JVM unit tests"
                    workingDir = rustDir.asFile
                    environment("NOFAR_CARGO_TARGET_DIR", rustDir.asFile.resolve("target").absolutePath)
                    commandLine(
                        "cargo",
                        "run",
                        "--quiet",
                        "--package",
                        "xtask",
                        "--",
                        "host-lib",
                        "--out-dir",
                        hostLibDir.get().asFile.absolutePath
                    )
                    outputs.dir(hostLibDir)
                }

            val cargoBuildAndroid =
                tasks.register<Exec>("cargoBuildAndroid") {
                    group = "rust"
                    description = "Build Android nofar_ffi shared libraries"
                    workingDir = rustDir.asFile
                    environment("NOFAR_CARGO_TARGET_DIR", rustDir.asFile.resolve("target").absolutePath)
                    commandLine(
                        "cargo",
                        "run",
                        "--quiet",
                        "--package",
                        "xtask",
                        "--",
                        "android",
                        "--out-dir",
                        androidLibDir.get().asFile.absolutePath
                    )
                    outputs.dir(androidLibDir)
                    onlyIf {
                        System.getenv("ANDROID_NDK_HOME")?.isNotBlank() == true ||
                            System.getenv("NDK_HOME")?.isNotBlank() == true ||
                            ndkFromLocalProperties(rootProject) != null
                    }
                    doFirst {
                        if (System.getenv("ANDROID_NDK_HOME").isNullOrBlank()) {
                            ndkFromLocalProperties(rootProject)?.let { ndk ->
                                environment("ANDROID_NDK_HOME", ndk.absolutePath)
                            }
                        }
                    }
                }

            tasks.named("preBuild").configure {
                dependsOn(generateUniffiBindings, cargoBuildHost)
            }

            listOf("Debug", "Release").forEach { variant ->
                tasks.matching { it.name == "compile${variant}Kotlin" }.configureEach {
                    dependsOn(generateUniffiBindings)
                }
            }

            tasks.matching { it.name.contains("merge") && it.name.contains("JniLibFolders") }.configureEach {
                dependsOn(cargoBuildAndroid)
            }

            tasks.withType<Test>().configureEach {
                dependsOn(cargoBuildHost)
                val libDir = hostLibDir.get().asFile.absolutePath
                systemProperty("jna.library.path", libDir)
                systemProperty("java.library.path", libDir)
            }
        }
    }
}

private fun ndkFromLocalProperties(rootProject: org.gradle.api.Project): java.io.File? {
    val localProps = rootProject.layout.projectDirectory.file("local.properties").asFile
    if (!localProps.exists()) {
        return null
    }
    val sdkDir =
        localProps
            .readLines()
            .firstOrNull { it.startsWith("sdk.dir=") }
            ?.substringAfter("sdk.dir=")
            ?.trim()
            ?.replace("\\\\", "/")
            ?: return null
    val ndkRoot = java.io.File(sdkDir, "ndk")
    if (!ndkRoot.isDirectory) {
        return null
    }
    return ndkRoot.listFiles()?.filter { it.isDirectory }?.maxByOrNull { it.name }
}
