package tech.zakomi.zakdroid.core.workspace

import tech.zakomi.zakdroid.core.fs.FileRef

enum class WorkspaceKind { Internal, Saf }

data class Workspace(
    val name: String,
    val root: FileRef,
    val kind: WorkspaceKind,
    val lastOpened: Long = 0,
) {
    val id: String get() = root.key
}
