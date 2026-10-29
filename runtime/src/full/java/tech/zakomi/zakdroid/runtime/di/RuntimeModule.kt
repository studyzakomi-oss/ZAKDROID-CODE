package tech.zakomi.zakdroid.runtime.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import tech.zakomi.zakdroid.runtime.ProotRuntimeManager
import tech.zakomi.zakdroid.runtime.RuntimeManager
import tech.zakomi.zakdroid.runtime.install.InstallGuard
import tech.zakomi.zakdroid.runtime.install.InstallLog
import tech.zakomi.zakdroid.runtime.install.RuntimePaths
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RuntimeModule {
    @Binds
    abstract fun runtimeManager(impl: ProotRuntimeManager): RuntimeManager

    companion object {
        /** One guard per app process: shared by every install worker. */
        @Provides
        @Singleton
        fun installGuard(paths: RuntimePaths): InstallGuard = InstallGuard(paths.installLock)

        @Provides
        @Singleton
        fun installLog(paths: RuntimePaths): InstallLog = InstallLog(paths.installLog, paths.previousInstallLog)
    }
}
