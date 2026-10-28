plugins {
    alias(libs.plugins.zakdroid.android.library)
    alias(libs.plugins.zakdroid.android.compose)
    alias(libs.plugins.zakdroid.hilt)
}

android {
    namespace = "tech.zakomi.zakdroid.terminal"
}

dependencies {
    api(project(":core"))
    api(project(":termux-terminal-view"))
}
