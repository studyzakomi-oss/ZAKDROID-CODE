package tech.zakomi.zakdroid

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import tech.zakomi.zakdroid.core.process.ProcessTree
import tech.zakomi.zakdroid.diagnostics.CrashLog
import javax.inject.Inject

@HiltAndroidApp
class ZakdroidApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        CrashLog.install(this)
        // Guest processes cannot outlive their sessions on purpose: anything still tagged
        // when the app process starts is an orphan from a previous process (it may hold
        // apk's database lock or keep running a program in the background).
        ProcessTree.current.killTagged(ProcessTree.GUEST_TAG)
        super.onCreate()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
