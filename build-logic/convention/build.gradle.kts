plugins {
    `kotlin-dsl`
}

group = "tech.zakomi.zakdroid.buildlogic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "zakdroid.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "zakdroid.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "zakdroid.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("hilt") {
            id = "zakdroid.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("distribution") {
            id = "zakdroid.distribution"
            implementationClass = "DistributionConventionPlugin"
        }
    }
}
