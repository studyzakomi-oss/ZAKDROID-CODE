package tech.zakomi.zakdroid.ui.git

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tech.zakomi.zakdroid.core.fs.FileNames
import tech.zakomi.zakdroid.core.fs.FileRef
import tech.zakomi.zakdroid.core.workspace.StoragePaths
import tech.zakomi.zakdroid.core.workspace.Workspace
import tech.zakomi.zakdroid.core.workspace.WorkspaceKind
import tech.zakomi.zakdroid.git.GitAccountStore
import tech.zakomi.zakdroid.git.GitException
import tech.zakomi.zakdroid.git.GitIdentity
import tech.zakomi.zakdroid.git.GitService
import tech.zakomi.zakdroid.git.GitState
import tech.zakomi.zakdroid.state.WorkspaceSession
import java.io.File
import javax.inject.Inject

data class SourceControlUiState(
    val hasWorkspace: Boolean = false,
    /** False for folders opened from device storage: Git needs a real file path. */
    val supported: Boolean = true,
    val git: GitState = GitState(isRepository = false),
    val busy: Boolean = false,
    val identity: GitIdentity = GitIdentity("", ""),
    val hasToken: Boolean = false,
)

enum class GitOperation { Init, Commit, Push, Pull, Clone, Remote, Account }

sealed interface SourceControlEvent {
    data class Done(val operation: GitOperation, val detail: String = "") : SourceControlEvent
    data class Failed(val kind: GitException.Kind, val detail: String) : SourceControlEvent
    data object IdentityNeeded : SourceControlEvent

    /** Files on disk changed (pull): open editors must be reloaded and the Explorer refreshed. */
    data object FilesChanged : SourceControlEvent
    data class Cloned(val workspace: Workspace) : SourceControlEvent
}

/** The Source Control view: local Git with JGit, and GitHub over HTTPS with an access token. */
@HiltViewModel
class SourceControlViewModel @Inject constructor(
    private val git: GitService,
    private val account: GitAccountStore,
    private val session: WorkspaceSession,
    private val paths: StoragePaths,
) : ViewModel() {

    private val _state = MutableStateFlow(SourceControlUiState())
    val state: StateFlow<SourceControlUiState> = _state.asStateFlow()

    private val _events = Channel<SourceControlEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var dir: File? = null

    init {
        viewModelScope.launch {
            _state.update { it.copy(identity = account.identity(), hasToken = account.hasToken()) }
        }
        viewModelScope.launch {
            session.current.collect { ws ->
                dir = (ws?.root as? FileRef.Local)?.let { File(it.path) }
                _state.update { it.copy(hasWorkspace = ws != null, supported = ws == null || dir != null, git = GitState(isRepository = false)) }
                refresh()
            }
        }
    }

    fun refresh() {
        val folder = dir ?: return
        viewModelScope.launch {
            try {
                val now = git.state(folder)
                if (dir == folder) _state.update { it.copy(git = now) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: GitException) {
                _events.send(SourceControlEvent.Failed(e.kind, e.detail))
            }
        }
    }

    fun initialize() = operation(GitOperation.Init) { git.init(it) }

    /** Commits all changes. The caller saves open editors first. */
    fun commit(message: String) {
        if (!_state.value.identity.isComplete) {
            viewModelScope.launch { _events.send(SourceControlEvent.IdentityNeeded) }
            return
        }
        operation(GitOperation.Commit) { git.commitAll(it, message, _state.value.identity) }
    }

    fun setRemote(url: String) = operation(GitOperation.Remote) { git.setRemote(it, url) }

    fun push() = operation(GitOperation.Push) { git.push(it, account.token()) }

    fun pull() = operation(GitOperation.Pull, filesChange = true) { git.pull(it, account.token()) }

    /** Clones into a new project in the app's storage and opens it. */
    fun clone(url: String, name: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                FileNames.validate(name.trim())?.let { throw GitException(GitException.Kind.Other, it) }
                val target = File(paths.projectsDir, name.trim())
                git.clone(url, target, account.token())
                val workspace = Workspace(target.name, FileRef.Local(target.absolutePath), WorkspaceKind.Internal)
                session.open(workspace)
                _events.send(SourceControlEvent.Cloned(workspace))
            } catch (e: CancellationException) {
                throw e
            } catch (e: GitException) {
                _events.send(SourceControlEvent.Failed(e.kind, e.detail))
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    /** Saves the commit identity; [token] replaces the stored one only when it is not blank. */
    fun saveAccount(name: String, email: String, token: String, removeToken: Boolean) {
        viewModelScope.launch {
            val identity = GitIdentity(name.trim(), email.trim())
            account.setIdentity(identity)
            if (removeToken) account.setToken("") else if (token.isNotBlank()) account.setToken(token)
            _state.update { it.copy(identity = identity, hasToken = account.hasToken()) }
            _events.send(SourceControlEvent.Done(GitOperation.Account))
        }
    }

    private fun operation(operation: GitOperation, filesChange: Boolean = false, block: suspend (File) -> Any?) {
        val folder = dir ?: return
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                val result = block(folder)
                _events.send(SourceControlEvent.Done(operation, (result as? String).orEmpty()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: GitException) {
                _events.send(SourceControlEvent.Failed(e.kind, e.detail))
            } finally {
                // A pull that stops at a conflict has changed files too.
                if (filesChange) _events.send(SourceControlEvent.FilesChanged)
                _state.update { it.copy(busy = false) }
                refresh()
            }
        }
    }
}
