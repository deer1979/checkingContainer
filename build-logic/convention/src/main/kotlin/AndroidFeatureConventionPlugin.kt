import com.checkingcontainer.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Plugin for feature modules (:feature:*). Composes compose + hilt and pulls
 * common compose runtime dependencies that every feature needs.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("checkingcontainer.android.library.compose")
                apply("checkingcontainer.android.hilt")
            }
            dependencies {
                add("implementation", libs.findLibrary("androidx-lifecycle-runtime-compose").get())
                add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
                add("implementation", libs.findLibrary("hilt-navigation-compose").get())
                add("implementation", libs.findLibrary("androidx-navigation-compose").get())
                add("implementation", libs.findLibrary("androidx-material-icons-extended").get())
                add("implementation", libs.findLibrary("kotlinx-coroutines-android").get())
            }
        }
    }
}
