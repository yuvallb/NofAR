import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

/** Navigation 2.8.x lint checks crash under AGP 9+ (b/371926651). */
private val disabledNavigationLintChecks =
    setOf(
        "WrongNavigateRouteType",
        "WrongStartDestinationType",
        "DeepLinkInActivityDestination"
    )

class AndroidLintConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            extensions.findByType(ApplicationExtension::class.java)?.lint {
                abortOnError = true
                warningsAsErrors = false
                checkDependencies = true
                baseline = file("lint-baseline.xml")
                disable += disabledNavigationLintChecks
            }
            extensions.findByType(LibraryExtension::class.java)?.lint {
                abortOnError = true
                warningsAsErrors = false
                checkDependencies = true
                baseline = file("lint-baseline.xml")
                disable += disabledNavigationLintChecks
            }
        }
    }
}
