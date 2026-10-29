package tech.zakomi.zakdroid.runtime.install

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import tech.zakomi.zakdroid.runtime.InstallStep
import tech.zakomi.zakdroid.runtime.R
import tech.zakomi.zakdroid.runtime.RuntimeErrorKind
import tech.zakomi.zakdroid.runtime.labelRes

/** Runs [RuntimeInstaller] as a foreground job so it survives leaving the app. */
@HiltWorker
class RuntimeInstallWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val paths: RuntimePaths,
    private val platform: AndroidInstallPlatform,
    private val guard: InstallGuard,
    private val log: InstallLog,
) : CoroutineWorker(context, params) {

    private var lastReported = -1
    private var lastStep: InstallStep? = null
    private var lastDetail: String? = null

    override suspend fun doWork(): Result {
        val mode = InstallMode.valueOf(inputData.getString(KEY_MODE) ?: InstallMode.Install.name)
        promoteToForeground(InstallStep.CheckSpace, 0)
        return try {
            guard.withInstallLock(
                onLockBusy = { log.line("install lock busy; killed ${platform.killJobProcesses()} process(es)") },
                describe = { "app pid=${android.os.Process.myPid()} work=$id mode=$mode attempt=$runAttemptCount\n" },
            ) {
                log.startRun("install mode=$mode attempt=$runAttemptCount work=$id")
                RuntimeInstaller(paths, platform, log) { step, fraction, detail -> report(step, fraction, detail) }.run(mode)
            }
            Result.success()
        } catch (e: CancellationException) {
            log.line("cancelled at $lastStep (stopReason=${if (Build.VERSION.SDK_INT >= 31) stopReason else -1})")
            throw e
        } catch (e: InstallException) {
            Log.w(TAG, "install failed at $lastStep", e)
            log.line("FAILED (${e.kind}) at $lastStep: ${e.message}")
            Result.failure(failure(e.kind, e.message ?: "Install failed"))
        } catch (e: Exception) {
            Log.e(TAG, "install crashed at $lastStep", e)
            log.line("CRASHED at $lastStep: ${Log.getStackTraceString(e)}")
            Result.failure(failure(RuntimeErrorKind.Unknown, e.message ?: e.javaClass.simpleName))
        }
    }

    private fun failure(kind: RuntimeErrorKind, message: String) = workDataOf(
        KEY_ERROR_KIND to kind.name,
        KEY_ERROR_MESSAGE to message,
        KEY_STEP to lastStep?.name,
    )

    /** Called from the installer's threads; must not suspend. */
    private fun report(step: InstallStep, fraction: Float, detail: String?) {
        val percent = step.overallPercent(fraction)
        val stepChanged = step != lastStep
        if (percent == lastReported && !stepChanged && detail == lastDetail) return
        lastDetail = detail
        lastStep = step
        setProgressAsync(workDataOf(KEY_STEP to step.name, KEY_PERCENT to percent, KEY_DETAIL to detail))
        if (percent != lastReported || stepChanged) {
            lastReported = percent
            promoteToForeground(step, percent)
        }
    }

    private fun promoteToForeground(step: InstallStep, percent: Int) {
        try {
            val future = setForegroundAsync(foregroundInfo(step, percent))
            // Refusals (e.g. ForegroundServiceStartNotAllowedException) arrive through the future.
            future.addListener({
                runCatching { future.get() }.onFailure { log.line("foreground notification refused: ${it.cause ?: it}") }
            }, Runnable::run)
        } catch (e: IllegalStateException) {
            // Android 12+ refuses foreground starts from the background; the work continues regardless.
            Log.i(TAG, "running without foreground notification: ${e.message}")
            log.line("foreground notification refused: ${e.message}")
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(InstallStep.CheckSpace, 0)

    private fun foregroundInfo(step: InstallStep, percent: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, applicationContext.getString(R.string.runtime_notification_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
        val cancel = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_zakdroid)
            .setContentTitle(applicationContext.getString(R.string.runtime_notification_title))
            .setContentText("${applicationContext.getString(step.labelRes)} · $percent%")
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, applicationContext.getString(R.string.runtime_notification_cancel), cancel)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val UNIQUE_NAME = "runtime-install"
        const val KEY_MODE = "mode"
        const val KEY_STEP = "step"
        const val KEY_PERCENT = "percent"
        const val KEY_DETAIL = "detail"
        const val KEY_ERROR_KIND = "errorKind"
        const val KEY_ERROR_MESSAGE = "errorMessage"
        private const val CHANNEL_ID = "runtime-install"
        private const val NOTIFICATION_ID = 4201
        private const val TAG = "RuntimeInstall"
    }
}
