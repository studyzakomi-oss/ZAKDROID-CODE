package tech.zakomi.zakdroid.runtime.install

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import tech.zakomi.zakdroid.core.workspace.StoragePaths
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** File layout of the local runtime. The file-based constructor exists for unit tests. */
@Singleton
class RuntimePaths(
    nativeLibDir: File,
    filesDir: File,
    homeDir: File,
) {
    @Inject
    constructor(@ApplicationContext context: Context, storage: StoragePaths) :
        this(File(context.applicationInfo.nativeLibraryDir), storage.filesDir, storage.homeDir)

    /** Executables shipped as lib*.so, so Android extracts them to an exec-allowed directory. */
    val proot = File(nativeLibDir, "libproot.so")
    val prootLoader = File(nativeLibDir, "libproot-loader.so")

    val runtimeDir = File(filesDir, "runtime")
    val rootfs = File(runtimeDir, "rootfs")
    val prootTmp = File(runtimeDir, "tmp")
    val downloads = File(runtimeDir, "downloads")
    val journal = File(runtimeDir, "install-journal.properties")

    /** Held (OS file lock) by the one running installer; contains its PIDs for diagnostics. */
    val installLock = File(runtimeDir, "install.lock")
    val installLog = File(runtimeDir, "install.log")
    val previousInstallLog = File(runtimeDir, "install.previous.log")

    /** Written last; its presence (with the right version) means the runtime is usable. */
    val readyMarker = File(rootfs, ".zakdroid-ready")

    val home = homeDir
}
