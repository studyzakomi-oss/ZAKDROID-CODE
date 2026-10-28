package tech.zakomi.zakdroid.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tech.zakomi.zakdroid.core.workspace.Workspace
import tech.zakomi.zakdroid.core.workspace.WorkspaceRepository
import javax.inject.Inject
import javax.inject.Singleton

/** The workspace open in the workbench, shared by the explorer, editor and terminal. */
@Singleton
class WorkspaceSession @Inject constructor(private val repository: WorkspaceRepository) {
    private val _current = MutableStateFlow<Workspace?>(null)
    val current: StateFlow<Workspace?> = _current.asStateFlow()

    suspend fun open(workspace: Workspace) {
        repository.markOpened(workspace)
        _current.value = workspace
    }

    fun close() {
        _current.value = null
    }
}
