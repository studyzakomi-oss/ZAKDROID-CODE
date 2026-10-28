plugins {
    alias(libs.plugins.zakdroid.android.library)
    alias(libs.plugins.zakdroid.android.compose)
    alias(libs.plugins.zakdroid.hilt)
}

android {
    namespace = "tech.zakomi.zakdroid.editor"
}

dependencies {
    api(project(":core"))
    api(platform(libs.sora.bom))
    api(libs.sora.editor)
    implementation(libs.sora.language.textmate)

    testImplementation(libs.junit)
}
