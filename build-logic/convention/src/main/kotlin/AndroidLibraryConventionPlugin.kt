import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            compileSdk = versionOf("compileSdk").toInt()
            ndkVersion = versionOf("ndk")
            defaultConfig {
                minSdk = versionOf("minSdk").toInt()
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
