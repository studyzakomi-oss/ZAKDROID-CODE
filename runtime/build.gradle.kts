plugins {
    alias(libs.plugins.zakdroid.android.library)
    alias(libs.plugins.zakdroid.distribution)
    alias(libs.plugins.zakdroid.hilt)
}

android {
    namespace = "tech.zakomi.zakdroid.runtime"
}

dependencies {
    api(project(":core"))
    api(project(":terminal"))
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.okhttp)
    implementation(libs.commons.compress)

    testImplementation(libs.junit)
}

// PRoot binaries are built from source (scripts/build-proot.sh) and never committed.
val buildProot = tasks.register<Exec>("buildProot") {
    description = "Builds PRoot and its loader for arm64-v8a and x86_64 with the NDK."
    group = "build"
    val outputs = listOf("arm64-v8a", "x86_64").flatMap { abi ->
        listOf("libproot.so", "libproot-loader.so").map { layout.projectDirectory.file("src/full/jniLibs/$abi/$it") }
    }
    onlyIf { outputs.any { !it.asFile.exists() } }
    workingDir = rootDir
    commandLine("bash", "scripts/build-proot.sh")
}
tasks.named("preBuild") { dependsOn(buildProot) }
