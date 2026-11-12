plugins {
    alias(libs.plugins.zakdroid.android.library)
    alias(libs.plugins.zakdroid.hilt)
}

android {
    namespace = "tech.zakomi.zakdroid.git"
}

dependencies {
    api(project(":core"))
    implementation(libs.jgit)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
