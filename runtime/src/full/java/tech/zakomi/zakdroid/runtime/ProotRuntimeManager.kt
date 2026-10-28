package tech.zakomi.zakdroid.runtime

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tech.zakomi.zakdroid.core.di.ApplicationScope
import tech.zakomi.zakdroid.core.workspace.StoragePaths
import tech.zakomi.zakdroid.runtime.install.AlpineRelease
import tech.zakomi.zakdroid.runtime.install.InstallJournal
import tech.zakomi.zakdroid.runtime.install.InstallMode
import tech.zakomi.zakdroid.runtime.install.ProotCommand
import tech.zakomi.zakdroid.runtime.install.RuntimeInstallWorker
import tech.zakomi.zakdroid.runtime.install.RuntimePaths
import tech.zakomi.zakdroid.terminal.ShellSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Local runtime: Alpine Linux under PRoot, installed by [RuntimeInstallWorker]. */
@Singleton
class ProotRuntimeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val paths: RuntimePaths,
    @ApplicationScope scope: CoroutineScope,
) : RuntimeManager {

    private val workManager = WorkManager.getInstance(context)
    private val proot = ProotCommand(paths)

    override val state: StateFlow<RuntimeState> =
        workManager.getWorkInfosForUniqueWorkFlow(RuntimeInstallWorker.UNIQUE_NAME)
            .map { infos -> stateFrom(infos.lastOrNull()) }
            .stateIn(scope, SharingStarted.Eagerly, diskState())

    override val description: String
        get() {
            val arch = AlpineRelease.deviceArch()?.alpineArch ?: "unsupported CPU"
            return "Alpine Linux ${AlpineRelease.VERSION} ($arch) · PRoot $PROOT_VERSION"
        }

    override val packages: List<String> = AlpineRelease.PACKAGES

    override fun install() = enqueue(InstallMode.Install)

    override fun repair() = enqueue(InstallMode.Repair)

    override fun reinstall() = enqueue(InstallMode.Reinstall)

    override fun cancel() {
        workManager.cancelUniqueWork(RuntimeInstallWorker.UNIQUE_NAME)
    }

    private fun enqueue(mode: InstallMode) {
        val request = OneTimeWorkRequestBuilder<RuntimeInstallWorker>()
            .setInputData(workDataOf(RuntimeInstallWorker.KEY_MODE to mode.name))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager.enqueueUniqueWork(RuntimeInstallWorker.UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
    }

    private fun stateFrom(info: WorkInfo?): RuntimeState {
        if (info == null) return diskState()
        return when (info.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> RuntimeState.Installing(InstallStep.CheckSpace, 0)
            WorkInfo.State.RUNNING -> {
                val step = info.progress.getString(RuntimeInstallWorker.KEY_STEP)?.let(InstallStep::valueOf) ?: InstallStep.CheckSpace
                RuntimeState.Installing(step, info.progress.getInt(RuntimeInstallWorker.KEY_PERCENT, 0), info.progress.getString(RuntimeInstallWorker.KEY_DETAIL))
            }
            WorkInfo.State.FAILED -> {
                val data = info.outputData
                RuntimeState.Error(
                    kind = data.getString(RuntimeInstallWorker.KEY_ERROR_KIND)?.let(RuntimeErrorKind::valueOf) ?: RuntimeErrorKind.Unknown,
                    message = data.getString(RuntimeInstallWorker.KEY_ERROR_MESSAGE) ?: "Install failed",
                    step = data.getString(RuntimeInstallWorker.KEY_STEP)?.let(InstallStep::valueOf),
                )
            }
            WorkInfo.State.SUCCEEDED, WorkInfo.State.CANCELLED -> diskState()
        }
    }

    private fun diskState(): RuntimeState = when {
        paths.readyMarker.isFile && paths.readyMarker.readText().trim() == AlpineRelease.VERSION && paths.proot.exists() -> RuntimeState.Ready
        else -> RuntimeState.NotInstalled(canResume = InstallJournal(paths.journal).hasProgress())
    }

    override fun shell(title: String, guestWorkDir: String): ShellSpec = spec(
        title = title,
        guestWorkDir = guestWorkDir,
        command = listOf("/bin/sh", "-l"),
        kind = ShellSpec.Kind.Shell,
    )

    override fun run(target: RunTarget): ShellSpec = spec(
        title = target.fileName,
        guestWorkDir = target.guestWorkDir,
        command = listOf("/bin/sh", "-c", RunScript.build(target)),
        kind = ShellSpec.Kind.Run,
    )

    private fun spec(title: String, guestWorkDir: String, command: List<String>, kind: ShellSpec.Kind): ShellSpec {
        check(state.value == RuntimeState.Ready) { "Runtime is not ready" }
        paths.prootTmp.mkdirs()
        val argv = proot.argv(guestWorkDir.ifEmpty { StoragePaths.GUEST_HOME }, command)
        return ShellSpec(
            title = title,
            executable = argv.first(),
            argv = listOf("proot") + argv.drop(1),
            cwd = paths.runtimeDir.absolutePath,
            env = proot.hostEnv(),
            kind = kind,
        )
    }

    private companion object {
        const val PROOT_VERSION = "5.1.107.101"
    }
}
