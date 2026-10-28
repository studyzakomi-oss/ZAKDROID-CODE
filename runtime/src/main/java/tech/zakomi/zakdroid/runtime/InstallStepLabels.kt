package tech.zakomi.zakdroid.runtime

import androidx.annotation.StringRes

@get:StringRes
val InstallStep.labelRes: Int
    get() = when (this) {
        InstallStep.CheckSpace -> R.string.runtime_step_check_space
        InstallStep.Download -> R.string.runtime_step_download
        InstallStep.Verify -> R.string.runtime_step_verify
        InstallStep.Extract -> R.string.runtime_step_extract
        InstallStep.Configure -> R.string.runtime_step_configure
        InstallStep.InstallPackages -> R.string.runtime_step_packages
        InstallStep.VerifyToolchain -> R.string.runtime_step_verify_toolchain
    }
