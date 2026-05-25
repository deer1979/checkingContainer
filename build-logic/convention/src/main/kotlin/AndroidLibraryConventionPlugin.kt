import com.android.build.api.dsl.LibraryExtension
import com.testo3.buildlogic.configureKotlin
import com.testo3.buildlogic.intVersion
import com.testo3.buildlogic.libs
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
            }
            extensions.configure<LibraryExtension> {
                compileSdk = libs.intVersion("compileSdk")
                defaultConfig {
                    minSdk = libs.intVersion("minSdk")
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_21
                    targetCompatibility = JavaVersion.VERSION_21
                }
                lint {
                    warningsAsErrors = false
                    abortOnError = true
                    checkDependencies = false
                    textReport = true
                    disable += setOf("GradleDependency", "AndroidGradlePluginVersion")
                }
            }
            configureKotlin()
        }
    }
}
