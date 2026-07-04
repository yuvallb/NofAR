import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.nofar.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "org.jetbrains.kotlin.plugin.compose")

            extensions.findByType(ApplicationExtension::class.java)?.buildFeatures?.compose = true
            extensions.findByType(LibraryExtension::class.java)?.buildFeatures?.compose = true

            dependencies {
                val bom = libs.findLibrary("androidx-compose-bom").get()
                add("implementation", platform(bom))
                add("androidTestImplementation", platform(bom))
                add("implementation", libs.findLibrary("androidx-compose-ui").get())
                add("implementation", libs.findLibrary("androidx-compose-ui-tooling-preview").get())
                add("implementation", libs.findLibrary("androidx-compose-material3").get())
                add("debugImplementation", libs.findLibrary("androidx-compose-ui-tooling").get())
            }
        }
    }
}
