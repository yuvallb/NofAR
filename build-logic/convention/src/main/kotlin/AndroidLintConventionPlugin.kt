import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class AndroidLintConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            extensions.findByType(ApplicationExtension::class.java)?.lint {
                abortOnError = true
                warningsAsErrors = false
                checkDependencies = true
                baseline = file("lint-baseline.xml")
            }
            extensions.findByType(LibraryExtension::class.java)?.lint {
                abortOnError = true
                warningsAsErrors = false
                checkDependencies = true
                baseline = file("lint-baseline.xml")
            }
        }
    }
}
