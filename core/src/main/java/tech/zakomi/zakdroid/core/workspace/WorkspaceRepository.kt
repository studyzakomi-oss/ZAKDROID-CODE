package tech.zakomi.zakdroid.core.workspace

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import tech.zakomi.zakdroid.core.data.OpenTabDao
import tech.zakomi.zakdroid.core.data.OpenTabEntity
import tech.zakomi.zakdroid.core.data.RecentWorkspaceDao
import tech.zakomi.zakdroid.core.data.RecentWorkspaceEntity
import tech.zakomi.zakdroid.core.di.IoDispatcher
import tech.zakomi.zakdroid.core.fs.FileNames
import tech.zakomi.zakdroid.core.fs.FileOperationException
import tech.zakomi.zakdroid.core.fs.FileRef
import tech.zakomi.zakdroid.core.fs.SafFileSystem
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkspaceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val paths: StoragePaths,
    private val recentDao: RecentWorkspaceDao,
    private val tabDao: OpenTabDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    val recentWorkspaces: Flow<List<Workspace>> = recentDao.observeRecent().map { list ->
        list.map { Workspace(it.name, FileRef.fromKey(it.rootKey), WorkspaceKind.valueOf(it.kind), it.lastOpened) }
    }

    suspend fun markOpened(workspace: Workspace) {
        recentDao.upsert(
            RecentWorkspaceEntity(workspace.id, workspace.name, workspace.kind.name, System.currentTimeMillis()),
        )
    }

    suspend fun forget(workspace: Workspace) {
        recentDao.delete(workspace.id)
        tabDao.clear(workspace.id)
        if (workspace.kind == WorkspaceKind.Saf) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(workspace.root.key), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
    }

    /** Builds a workspace for a folder picked with ACTION_OPEN_DOCUMENT_TREE and keeps access across reboots. */
    fun fromSafTree(treeUri: Uri, displayName: String): Workspace {
        context.contentResolver.takePersistableUriPermission(
            treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        return Workspace(displayName, SafFileSystem.rootDocument(treeUri), WorkspaceKind.Saf)
    }

    /** True while the app still holds a persisted grant for this SAF workspace. */
    fun hasAccess(workspace: Workspace): Boolean {
        if (workspace.kind == WorkspaceKind.Internal) return File((workspace.root as FileRef.Local).path).isDirectory
        val key = workspace.root.key
        return context.contentResolver.persistedUriPermissions.any { perm ->
            perm.isWritePermission && key.startsWith(perm.uri.toString())
        }
    }

    suspend fun createInternalProject(name: String, template: ProjectTemplate?): Workspace = withContext(io) {
        FileNames.validate(name)?.let { throw FileOperationException(it) }
        val dir = File(paths.projectsDir, name)
        if (dir.exists()) throw FileOperationException("A project named \"$name\" already exists")
        if (!dir.mkdirs()) throw FileOperationException("Cannot create project folder")
        template?.files?.forEach { (path, content) -> File(dir, path).apply { parentFile?.mkdirs() }.writeText(content) }
        Workspace(name, FileRef.Local(dir.absolutePath), WorkspaceKind.Internal)
    }

    suspend fun internalProjects(): List<Workspace> = withContext(io) {
        paths.projectsDir.listFiles { f -> f.isDirectory }.orEmpty()
            .sortedBy { it.name.lowercase() }
            .map { Workspace(it.name, FileRef.Local(it.absolutePath), WorkspaceKind.Internal) }
    }

    suspend fun savedTabs(workspace: Workspace): List<OpenTabEntity> = tabDao.tabsFor(workspace.id)

    suspend fun saveTabs(workspace: Workspace, tabs: List<OpenTabEntity>) = tabDao.replaceAll(workspace.id, tabs)
}
