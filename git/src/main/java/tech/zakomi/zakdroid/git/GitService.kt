package tech.zakomi.zakdroid.git

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.api.errors.InvalidRemoteException
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.errors.NoRemoteRepositoryException
import org.eclipse.jgit.lib.BranchTrackingStatus
import org.eclipse.jgit.lib.ConfigConstants
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import tech.zakomi.zakdroid.core.di.IoDispatcher
import java.io.File
import java.io.IOException
import java.net.URISyntaxException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Git on plain folders, with JGit (pure Java: no toolchain needed). The repository root
 * is the folder itself; a `.git` in a parent folder is never used, so a project cannot
 * commit into something above it.
 */
@Singleton
class GitService @Inject constructor(@IoDispatcher private val io: CoroutineDispatcher) {

    init {
        IsolatedSystemReader.install()
    }

    suspend fun state(dir: File): GitState = run(dir.takeIf { isRepository(it) }) { git ->
        if (git == null) return@run GitState(isRepository = false)
        val repo = git.repository
        val status = git.status().call()
        val changes = LinkedHashMap<String, ChangeKind>()
        status.conflicting.forEach { changes[it] = ChangeKind.Conflict }
        (status.untracked).forEach { changes.putIfAbsent(it, ChangeKind.Untracked) }
        (status.added).forEach { changes.putIfAbsent(it, ChangeKind.Added) }
        (status.missing + status.removed).forEach { changes.putIfAbsent(it, ChangeKind.Deleted) }
        (status.modified + status.changed).forEach { changes.putIfAbsent(it, ChangeKind.Modified) }
        val tracking = runCatching { BranchTrackingStatus.of(repo, repo.branch) }.getOrNull()
        val log = if (repo.resolve(Constants.HEAD) == null) emptyList() else git.log().setMaxCount(LOG_SIZE).call().map {
            GitCommitInfo(it.name.take(7), it.shortMessage, it.authorIdent.name, it.commitTime * 1000L)
        }
        GitState(
            isRepository = true,
            branch = repo.branch.orEmpty(),
            changes = changes.map { GitChange(it.key, it.value) }.sortedBy { it.path.lowercase() },
            remoteUrl = repo.config.getString(ConfigConstants.CONFIG_REMOTE_SECTION, ORIGIN, ConfigConstants.CONFIG_KEY_URL),
            ahead = tracking?.aheadCount,
            behind = tracking?.behindCount,
            log = log,
        )
    }

    suspend fun init(dir: File) = withContext(io) {
        guarded { Git.init().setDirectory(dir).setInitialBranch(DEFAULT_BRANCH).call().close() }
    }

    /** Stages every change (new, modified and deleted files) and commits. Returns the short commit id. */
    suspend fun commitAll(dir: File, message: String, identity: GitIdentity): String = run(dir) { git ->
        git!!
        git.add().addFilepattern(".").call()
        git.add().addFilepattern(".").setUpdate(true).call()
        val status = git.status().call()
        if (!status.hasUncommittedChanges() && status.isClean) throw GitException(GitException.Kind.NothingToCommit, "Nothing to commit")
        val who = PersonIdent(identity.name, identity.email)
        git.commit().setMessage(message.trim()).setAuthor(who).setCommitter(who).setSign(false).call().name.take(7)
    }

    /** Sets (or replaces) the remote `origin`. */
    suspend fun setRemote(dir: File, url: String) = run(dir) { git ->
        val uri = parseUrl(url)
        val config = git!!.repository.config
        config.setString(ConfigConstants.CONFIG_REMOTE_SECTION, ORIGIN, ConfigConstants.CONFIG_KEY_URL, uri.toString())
        config.setString(ConfigConstants.CONFIG_REMOTE_SECTION, ORIGIN, "fetch", "+refs/heads/*:refs/remotes/$ORIGIN/*")
        config.save()
    }

    /** Pushes the current branch to `origin` and makes it the upstream. */
    suspend fun push(dir: File, token: String?) = run(dir) { git ->
        val repo = git!!.repository
        requireRemote(repo)
        val branch = repo.branch
        val results = git.push().setRemote(ORIGIN).add(branch).setCredentialsProvider(credentials(token)).call()
        for (update in results.flatMap { it.remoteUpdates }) {
            when (update.status) {
                RemoteRefUpdate.Status.OK, RemoteRefUpdate.Status.UP_TO_DATE -> Unit
                RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD, RemoteRefUpdate.Status.REJECTED_REMOTE_CHANGED ->
                    throw GitException(GitException.Kind.Rejected, "The remote has commits that are not on this device")
                else -> throw GitException(GitException.Kind.Other, "${update.status} ${update.message.orEmpty()}".trim())
            }
        }
        val config = repo.config
        config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, branch, ConfigConstants.CONFIG_KEY_REMOTE, ORIGIN)
        config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, branch, ConfigConstants.CONFIG_KEY_MERGE, Constants.R_HEADS + branch)
        config.save()
    }

    /** Fetches `origin` and merges its copy of the current branch. A conflict leaves markers in the files. */
    suspend fun pull(dir: File, token: String?) = run(dir) { git ->
        val repo = git!!.repository
        requireRemote(repo)
        val result = git.pull().setRemote(ORIGIN).setRemoteBranchName(repo.branch).setCredentialsProvider(credentials(token)).call()
        val merge = result.mergeResult
        if (merge != null && merge.mergeStatus == MergeResult.MergeStatus.CONFLICTING) {
            throw GitException(GitException.Kind.Conflict, merge.conflicts?.keys.orEmpty().joinToString(", "))
        }
        if (!result.isSuccessful) throw GitException(GitException.Kind.Other, merge?.mergeStatus?.toString() ?: "Pull failed")
    }

    /** Clones [url] into the new folder [target]. Nothing is left behind when it fails. */
    suspend fun clone(url: String, target: File, token: String?) = withContext(io) {
        val uri = parseUrl(url)
        if (target.exists()) throw GitException(GitException.Kind.TargetExists, target.name)
        try {
            guarded { Git.cloneRepository().setURI(uri.toString()).setDirectory(target).setCredentialsProvider(credentials(token)).call().close() }
        } catch (e: Exception) {
            target.deleteRecursively()
            throw e
        }
    }

    fun isRepository(dir: File): Boolean = File(dir, Constants.DOT_GIT).isDirectory

    /**
     * JGit can skip TLS certificate checks when a repository's config says `http.sslVerify = false`.
     * That would send the access token to anyone who intercepts the connection, so it is refused.
     */
    private fun requireTlsVerification(repo: Repository) {
        val config = repo.config
        val sections = listOf<String?>(null) + config.getSubsections("http")
        if (sections.any { !config.getBoolean("http", it, "sslVerify", true) }) {
            throw GitException(GitException.Kind.Other, "This repository turns off TLS certificate checks (http.sslVerify = false). Remove that setting from .git/config.")
        }
    }

    private fun requireRemote(repo: Repository) {
        requireTlsVerification(repo)
        if (repo.config.getString(ConfigConstants.CONFIG_REMOTE_SECTION, ORIGIN, ConfigConstants.CONFIG_KEY_URL).isNullOrBlank()) {
            throw GitException(GitException.Kind.NoRemote, "No remote")
        }
    }

    private fun parseUrl(url: String): URIish {
        val trimmed = url.trim()
        val uri = try {
            URIish(trimmed)
        } catch (e: URISyntaxException) {
            throw GitException(GitException.Kind.InvalidUrl, trimmed, e)
        }
        // HTTPS for real remotes; plain paths and file: URLs are for local repositories (and tests).
        val ok = uri.scheme == "https" || uri.scheme == "file" || (uri.scheme == null && uri.host == null && trimmed.startsWith("/"))
        if (trimmed.isEmpty() || !ok) throw GitException(GitException.Kind.InvalidUrl, trimmed)
        return uri
    }

    /** GitHub accepts a personal access token as the password of any user name. */
    private fun credentials(token: String?): CredentialsProvider? =
        token?.takeIf { it.isNotBlank() }?.let { UsernamePasswordCredentialsProvider("x-access-token", it) }

    private suspend fun <T> run(dir: File?, block: (Git?) -> T): T = withContext(io) {
        guarded {
            if (dir == null) return@guarded block(null)
            // setGitDir + setWorkTree pin the repository to this folder (no search in parent folders).
            val repo = FileRepositoryBuilder().setGitDir(File(dir, Constants.DOT_GIT)).setWorkTree(dir).setMustExist(true).build()
            Git(repo).use(block)
        }
    }

    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (e: GitException) {
        throw e
    } catch (e: TransportException) {
        val text = e.message.orEmpty()
        val kind = when {
            text.contains("not authorized", true) || text.contains("authentication", true) || text.contains("403") -> GitException.Kind.Authentication
            e.cause is NoRemoteRepositoryException || text.contains("not found", true) -> GitException.Kind.NoRemote
            else -> GitException.Kind.Network
        }
        throw GitException(kind, text, e)
    } catch (e: InvalidRemoteException) {
        throw GitException(GitException.Kind.NoRemote, e.message ?: "Remote not found", e)
    } catch (e: GitAPIException) {
        throw GitException(GitException.Kind.Other, e.message ?: e.javaClass.simpleName, e)
    } catch (e: IOException) {
        throw GitException(GitException.Kind.Other, e.message ?: e.javaClass.simpleName, e)
    }

    private companion object {
        const val ORIGIN = "origin"
        const val DEFAULT_BRANCH = "main"
        const val LOG_SIZE = 30
    }
}
