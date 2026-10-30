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
        versionCode = 3
        versionName = "0.1.2"
    }

    buildFeatures {
        buildConfig = true
    }

    androidResources {
        localeFilters += listOf("en", "ar")
    }

    // One APK per ABI (smaller download) plus a universal APK, e.g.
    // app-full-arm64-v8a-debug.apk, app-full-x86_64-debug.apk, app-full-universal-debug.apk.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    signingConfigs {
        // Release signing comes from the environment (CI decodes the KEYSTORE_BASE64 secret into
        // KEYSTORE_FILE). Without all four values the release APK is built unsigned.
        val keystore = System.getenv("KEYSTORE_FILE")
        val storePass = System.getenv("KEYSTORE_PASSWORD")
        val alias = System.getenv("KEY_ALIAS")
        val keyPass = System.getenv("KEY_PASSWORD")
        if (!keystore.isNullOrEmpty() && !storePass.isNullOrEmpty() && !alias.isNullOrEmpty() && !keyPass.isNullOrEmpty()) {
            create("release") {
                storeFile = file(keystore)
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
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
