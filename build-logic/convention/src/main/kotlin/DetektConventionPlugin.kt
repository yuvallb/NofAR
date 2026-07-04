import dev.detekt.gradle.Detekt
import dev.detekt.gradle.DetektCreateBaselineTask
import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

class DetektConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "dev.detekt")

            extensions.configure<DetektExtension> {
                toolVersion.set("2.0.0-alpha.5")
                config.setFrom(files("${rootProject.projectDir}/config/detekt/detekt.yml"))
                buildUponDefaultConfig.set(true)
                allRules.set(false)
            }

            // Match Kotlin compile target (JVM 17), not the JDK running Gradle.
            tasks.withType<Detekt>().configureEach {
                jvmTarget.set("17")
            }
            tasks.withType<DetektCreateBaselineTask>().configureEach {
                jvmTarget.set("17")
            }
        }
    }
}
