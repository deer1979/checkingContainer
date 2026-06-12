import com.android.build.api.dsl.LibraryExtension
import com.checkingcontainer.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("checkingcontainer.android.library")
                apply("org.jetbrains.kotlin.plugin.compose")
            }
            extensions.configure<LibraryExtension> {
                buildFeatures { compose = true }
            }
            extensions.configure<ComposeCompilerGradlePluginExtension> {
                stabilityConfigurationFiles.add(
                    rootProject.layout.projectDirectory.file("compose-stability.conf")
                )
                // Diagnóstico de recomposiciones: ./gradlew ... -PcomposeMetrics
                // genera reportes en build/compose-metrics y build/compose-reports.
                if (providers.gradleProperty("composeMetrics").isPresent) {
                    metricsDestination.set(layout.buildDirectory.dir("compose-metrics"))
                    reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
                }
            }
            dependencies {
                val bom = libs.findLibrary("androidx-compose-bom").get()
                add("implementation", platform(bom))
                add("androidTestImplementation", platform(bom))
                add("implementation", libs.findLibrary("androidx-ui").get())
                add("implementation", libs.findLibrary("androidx-ui-graphics").get())
                add("implementation", libs.findLibrary("androidx-ui-tooling-preview").get())
                add("implementation", libs.findLibrary("androidx-material3").get())
                add("debugImplementation", libs.findLibrary("androidx-ui-tooling").get())
                add("debugImplementation", libs.findLibrary("androidx-ui-test-manifest").get())
            }
        }
    }
}
