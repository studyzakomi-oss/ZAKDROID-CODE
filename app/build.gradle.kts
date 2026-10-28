plugins {
    alias(libs.plugins.zakdroid.android.application)
    alias(libs.plugins.zakdroid.android.compose)
    alias(libs.plugins.zakdroid.hilt)
    alias(libs.plugins.zakdroid.distribution)
}

android {
    namespace = "tech.zakomi.zakdroid"

    defaultConfig {
        applicationId = "tech.zakomi.zakdroid"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    androidResources {
        localeFilters += listOf("en", "ar")
    }

    signingConfigs {
        // Release signing comes from the environment (CI secrets); without it the release APK is unsigned.
        val keystore = System.getenv("ZAKDROID_KEYSTORE")
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("ZAKDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ZAKDROID_KEY_ALIAS")
                keyPassword = System.getenv("ZAKDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    packaging {
        jniLibs {
            // PRoot is an executable shipped as lib*.so: it must be extracted to nativeLibraryDir.
            useLegacyPackaging = true
            // The loader's symbol table is read by PRoot at build time; do not strip it.
            keepDebugSymbols += "**/libproot-loader.so"
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":editor"))
    implementation(project(":terminal"))
    implementation(project(":runtime"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit)
}
