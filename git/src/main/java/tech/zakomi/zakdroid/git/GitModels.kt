package tech.zakomi.zakdroid.git

enum class ChangeKind { Modified, Added, Deleted, Untracked, Conflict }

/** One changed file; [path] is relative to the repository root and uses '/'. */
data class GitChange(val path: String, val kind: ChangeKind)

data class GitCommitInfo(val shortId: String, val message: String, val author: String, val timeMillis: Long)

/** What the Source Control view shows for a folder. */
data class GitState(
    val isRepository: Boolean,
    val branch: String = "",
    val changes: List<GitChange> = emptyList(),
    /** URL of the remote `origin`, or null when none is set. */
    val remoteUrl: String? = null,
    /** Commits not pushed yet / not pulled yet, or null when the branch has no upstream. */
    val ahead: Int? = null,
    val behind: Int? = null,
    val log: List<GitCommitInfo> = emptyList(),
)

data class GitIdentity(val name: String, val email: String) {
    val isComplete: Boolean get() = name.isNotBlank() && email.isNotBlank()
}

/** Why a Git operation failed, so the UI can show a translated message. [detail] is Git's own text. */
class GitException(val kind: Kind, val detail: String, cause: Throwable? = null) : Exception(detail, cause) {
    enum class Kind { Authentication, Network, NoRemote, NothingToCommit, Rejected, Conflict, InvalidUrl, TargetExists, Other }
}
