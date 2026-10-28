plugins {
    alias(libs.plugins.zakdroid.android.library)
    alias(libs.plugins.zakdroid.android.compose)
    alias(libs.plugins.zakdroid.hilt)
}

android {
    namespace = "tech.zakomi.zakdroid.core"
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    api(libs.androidx.core.ktx)
    api(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
