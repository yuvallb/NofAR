import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

class SpotlessConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.diffplug.spotless")

            extensions.configure<SpotlessExtension> {
                kotlin {
                    target("**/*.kt")
                    targetExclude("**/build/**")
                    ktlint().editorConfigOverride(
                        mapOf(
                            "max_line_length" to "120",
                            "ktlint_code_style" to "android_studio",
                            "ktlint_function_naming_ignore_when_annotated_with" to "Composable,Preview"
                        )
                    )
                }
                kotlinGradle {
                    target("**/*.gradle.kts")
                    targetExclude("**/build/**")
                    ktlint().editorConfigOverride(
                        mapOf(
                            "max_line_length" to "120"
                        )
                    )
                }
            }
        }
    }
}
