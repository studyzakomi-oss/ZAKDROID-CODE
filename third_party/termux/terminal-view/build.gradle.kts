plugins {
    alias(libs.plugins.zakdroid.android.library)
}

android {
    namespace = "com.termux.view"
}

dependencies {
    implementation(libs.androidx.annotation)
    api(project(":termux-terminal-emulator"))
}
