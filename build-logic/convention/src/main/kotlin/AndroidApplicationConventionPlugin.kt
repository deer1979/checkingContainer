import com.android.build.api.dsl.ApplicationExtension
import com.checkingcontainer.buildlogic.configureKotlin
import com.checkingcontainer.buildlogic.intVersion
import com.checkingcontainer.buildlogic.libs
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.application")
            }
            extensions.configure<ApplicationExtension> {
                compileSdk = libs.intVersion("compileSdk")
                defaultConfig {
                    minSdk = libs.intVersion("minSdk")
                    targetSdk = libs.intVersion("targetSdk")
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
