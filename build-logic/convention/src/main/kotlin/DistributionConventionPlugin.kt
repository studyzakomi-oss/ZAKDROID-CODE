import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Distribution channels of zakdroid-code, as one flavor dimension shared by every
 * module that has channel-specific code (today :app and :runtime).
 *
 * - full: GitHub Releases + F-Droid. Ships PRoot and installs the Alpine toolchain.
 *
 * Phase 4 adds `play` (Remote SSH only, no downloaded executables) by appending it
 * to [DISTRIBUTIONS] and providing `src/play/` source sets; nothing else moves.
 */
class DistributionConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.withPlugin("com.android.application") {
            extensions.configure<ApplicationExtension> {
                flavorDimensions += DIMENSION
                productFlavors {
                    DISTRIBUTIONS.forEach { name -> create(name) { dimension = DIMENSION } }
                }
            }
        }
        pluginManager.withPlugin("com.android.library") {
            extensions.configure<LibraryExtension> {
                flavorDimensions += DIMENSION
                productFlavors {
                    DISTRIBUTIONS.forEach { name -> create(name) { dimension = DIMENSION } }
                }
            }
        }
    }

    private companion object {
        const val DIMENSION = "distribution"
        val DISTRIBUTIONS = listOf("full")
    }
}
