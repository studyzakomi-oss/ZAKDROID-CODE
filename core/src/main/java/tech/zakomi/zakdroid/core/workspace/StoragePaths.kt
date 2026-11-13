package tech.zakomi.zakdroid.core.workspace

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-private storage layout. [homeDir] is bind-mounted as /home/user inside the
 * Linux runtime, so internal projects can be built in place.
 */
@Singleton
class StoragePaths(val filesDir: File) {
    /** The file-based constructor exists for unit tests. */
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.filesDir)

    val homeDir: File = File(filesDir, "home")
    val projectsDir: File = File(homeDir, "projects")

    /** Copies of SAF workspaces, synced before each run (the toolchain cannot read content:// URIs). */
    val stagingDir: File = File(homeDir, ".zakdroid/staging")

    /** Scratch space for unpacking a project archive; on the same file system as [projectsDir]. */
    val importDir: File = File(homeDir, ".zakdroid/import")

    companion object {
        const val GUEST_HOME = "/home/user"
    }
}
