import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLintConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            extensions.configure<CommonExtension<*, *, *, *, *, *, *>> {
                lint {
                    abortOnError = true
                    warningsAsErrors = false
                    checkDependencies = true
                    baseline = file("lint-baseline.xml")
                }
            }
        }
    }
}
