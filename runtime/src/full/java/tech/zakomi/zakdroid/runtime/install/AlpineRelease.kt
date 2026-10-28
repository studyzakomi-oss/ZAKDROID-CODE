package tech.zakomi.zakdroid.runtime.install

import android.os.Build

/**
 * The pinned Alpine Linux release. Checksums are those published by Alpine
 * (cross-checked against alpinelinux/docker-alpine, branch v3.24, which ships
 * the same tarballs). Bumping the release means updating all values together.
 */
object AlpineRelease {
    const val VERSION = "3.24.3"
    const val BRANCH = "v3.24"
    private const val MIRROR = "https://dl-cdn.alpinelinux.org/alpine"

    /** Packages installed into the rootfs. Phase 2 adds clang-extra-tools (clangd) and LSP servers. */
    val PACKAGES = listOf("build-base", "clang", "python3", "py3-pip")

    /** Free space required before installing: download + rootfs + packages, with headroom. */
    const val REQUIRED_FREE_BYTES = 400L * 1024 * 1024

    val REPOSITORIES = listOf("$MIRROR/$BRANCH/main", "$MIRROR/$BRANCH/community")

    enum class Arch(val abi: String, val alpineArch: String, val sha256: String, val size: Long) {
        Aarch64("arm64-v8a", "aarch64", "4d3f80afaebd447c5591a63a73bde380a149fe8b2d85488cafabb2ff97551f50", 4_029_999),
        X86_64("x86_64", "x86_64", "f8e1cafba9b1b283b5d30d06f9885c8d2bc161ffe9083ade8e2e157813a53407", 3_704_080);

        val fileName get() = "alpine-minirootfs-$VERSION-$alpineArch.tar.gz"
        val url get() = "$MIRROR/$BRANCH/releases/$alpineArch/$fileName"
    }

    /** The architecture for this device, or null if the ABI is unsupported. */
    fun deviceArch(): Arch? = Build.SUPPORTED_ABIS.firstNotNullOfOrNull { abi -> Arch.entries.firstOrNull { it.abi == abi } }
}
