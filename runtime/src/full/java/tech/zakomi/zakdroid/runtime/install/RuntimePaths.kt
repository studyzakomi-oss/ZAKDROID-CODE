package tech.zakomi.zakdroid.runtime.install

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import tech.zakomi.zakdroid.core.workspace.StoragePaths
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuntimePaths @Inject constructor(
    @ApplicationContext context: Context,
    storage: StoragePaths,
) {
    private val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)

    /** Executables shipped as lib*.so, so Android extracts them to an exec-allowed directory. */
    val proot = File(nativeLibDir, "libproot.so")
    val prootLoader = File(nativeLibDir, "libproot-loader.so")

    val runtimeDir = File(storage.filesDir, "runtime")
    val rootfs = File(runtimeDir, "rootfs")
    val prootTmp = File(runtimeDir, "tmp")
    val downloads = File(runtimeDir, "downloads")
    val journal = File(runtimeDir, "install-journal.properties")

    /** Written last; its presence (with the right version) means the runtime is usable. */
    val readyMarker = File(rootfs, ".zakdroid-ready")

    val home = storage.homeDir
}
