import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        extensions.configure<ApplicationExtension> {
            compileSdk = versionOf("compileSdk").toInt()
            ndkVersion = versionOf("ndk")
            defaultConfig {
                minSdk = versionOf("minSdk").toInt()
                targetSdk = versionOf("targetSdk").toInt()
                ndk { abiFilters += SUPPORTED_ABIS }
            }
            compileOptions {
                sourceCompatibility = JAVA_VERSION
                targetCompatibility = JAVA_VERSION
            }
        }
        configureKotlinJvmTarget()
    }
}
