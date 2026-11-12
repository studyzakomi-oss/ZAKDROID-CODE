package tech.zakomi.zakdroid.git

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.eclipse.jgit.api.Git
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GitServiceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val git = GitService(Dispatchers.Unconfined)
    private val me = GitIdentity("Tester", "tester@example.com")

    private fun project(name: String = "project"): File = tmp.newFolder(name).also { File(it, "main.c").writeText("int main(void) { return 0; }\n") }

    private fun bareRemote(): File = tmp.newFolder("remote.git").also { Git.init().setBare(true).setInitialBranch("main").setDirectory(it).call().close() }

    private fun failsWith(kind: GitException.Kind, block: suspend () -> Unit) = runBlocking {
        try {
            block()
            fail("expected $kind")
        } catch (e: GitException) {
            assertEquals(kind, e.kind)
        }
    }

    @Test
    fun aPlainFolderIsNotARepositoryEvenInsideOne() = runBlocking {
        val outer = project("outer")
        git.init(outer)
        val inner = File(outer, "inner").apply { mkdirs() }
        assertTrue(git.state(outer).isRepository)
        assertFalse(git.state(inner).isRepository)
    }

    @Test
    fun initCommitAndStatus() = runBlocking {
        val dir = project()
        git.init(dir)
        var state = git.state(dir)
        assertEquals("main", state.branch)
        assertEquals(listOf(GitChange("main.c", ChangeKind.Untracked)), state.changes)
        assertTrue(state.log.isEmpty())
        assertNull(state.remoteUrl)

        val id = git.commitAll(dir, "  first  ", me)
        state = git.state(dir)
        assertTrue(state.changes.isEmpty())
        assertEquals(listOf("first"), state.log.map { it.message })
        assertEquals(id, state.log.single().shortId)
        assertEquals("Tester", state.log.single().author)

        File(dir, "main.c").appendText("// edit\n")
        File(dir, "new.py").writeText("print(1)\n")
        assertEquals(
            listOf(GitChange("main.c", ChangeKind.Modified), GitChange("new.py", ChangeKind.Untracked)),
            git.state(dir).changes,
        )
        git.commitAll(dir, "second", me)

        File(dir, "new.py").delete()
        assertEquals(listOf(GitChange("new.py", ChangeKind.Deleted)), git.state(dir).changes)
        git.commitAll(dir, "third", me)
        assertTrue(git.state(dir).changes.isEmpty())
        assertEquals(listOf("third", "second", "first"), git.state(dir).log.map { it.message })
    }

    @Test
    fun nothingToCommit() {
        val dir = project()
        runBlocking { git.init(dir); git.commitAll(dir, "first", me) }
        failsWith(GitException.Kind.NothingToCommit) { git.commitAll(dir, "again", me) }
    }

    @Test
    fun pushCloneAndPull() = runBlocking {
        val remote = bareRemote()
        val a = project("a")
        git.init(a)
        git.commitAll(a, "first", me)
        git.setRemote(a, remote.absolutePath)
        assertNull(git.state(a).ahead) // no upstream yet
        git.push(a, null)
        assertEquals(0, git.state(a).ahead)

        val b = File(tmp.root, "b")
        git.clone(remote.absolutePath, b, null)
        assertEquals("int main(void) { return 0; }\n", File(b, "main.c").readText())
        assertEquals(remote.absolutePath, git.state(b).remoteUrl)

        File(a, "main.c").appendText("// from a\n")
        git.commitAll(a, "second", me)
        assertEquals(1, git.state(a).ahead)
        git.push(a, null)

        git.pull(b, null)
        assertTrue(File(b, "main.c").readText().endsWith("// from a\n"))
        assertEquals(listOf("second", "first"), git.state(b).log.map { it.message })
    }

    @Test
    fun pushIsRejectedWhenTheRemoteIsAheadAndPullReportsConflicts() {
        val remote = bareRemote()
        val a = project("a")
        val b = File(tmp.root, "b")
        runBlocking {
            git.init(a); git.commitAll(a, "first", me); git.setRemote(a, remote.absolutePath); git.push(a, null)
            git.clone(remote.absolutePath, b, null)
            File(a, "main.c").writeText("int a;\n"); git.commitAll(a, "a", me); git.push(a, null)
            File(b, "main.c").writeText("int b;\n"); git.commitAll(b, "b", me)
        }
        failsWith(GitException.Kind.Rejected) { git.push(b, null) }
        failsWith(GitException.Kind.Conflict) { git.pull(b, null) }
        assertEquals(listOf(GitChange("main.c", ChangeKind.Conflict)), runBlocking { git.state(b).changes })
    }

    @Test
    fun aRepositoryThatDisablesTlsChecksIsRefused() {
        val remote = bareRemote()
        val dir = project()
        runBlocking { git.init(dir); git.commitAll(dir, "first", me); git.setRemote(dir, remote.absolutePath) }
        File(dir, ".git/config").appendText("[http]\n\tsslVerify = false\n")
        failsWith(GitException.Kind.Other) { git.push(dir, null) }
        failsWith(GitException.Kind.Other) { git.pull(dir, null) }
        File(dir, ".git/config").writeText(File(dir, ".git/config").readText().replace("sslVerify = false", "sslVerify = true"))
        File(dir, ".git/config").appendText("[http \"https://example.com\"]\n\tsslVerify = false\n")
        failsWith(GitException.Kind.Other) { git.push(dir, null) }
    }

    @Test
    fun errorsAreTyped() {
        val dir = project()
        runBlocking { git.init(dir); git.commitAll(dir, "first", me) }
        failsWith(GitException.Kind.NoRemote) { git.push(dir, null) }
        failsWith(GitException.Kind.NoRemote) { git.pull(dir, null) }
        failsWith(GitException.Kind.InvalidUrl) { git.setRemote(dir, "ssh://git@github.com/a/b.git") }
        failsWith(GitException.Kind.InvalidUrl) { git.setRemote(dir, "  ") }
        failsWith(GitException.Kind.TargetExists) { git.clone(dir.absolutePath, dir, null) }
        val missing = File(tmp.root, "clone-of-nothing")
        failsWith(GitException.Kind.NoRemote) { git.clone(File(tmp.root, "nope").absolutePath, missing, null) }
        assertFalse(missing.exists())
        runBlocking { git.setRemote(dir, "https://github.com/someone/project.git") }
        assertEquals("https://github.com/someone/project.git", runBlocking { git.state(dir).remoteUrl })
    }
}
