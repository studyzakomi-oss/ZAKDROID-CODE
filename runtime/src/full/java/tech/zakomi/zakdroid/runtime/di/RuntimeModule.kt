package tech.zakomi.zakdroid.runtime.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import tech.zakomi.zakdroid.runtime.ProotRuntimeManager
import tech.zakomi.zakdroid.runtime.RuntimeManager

@Module
@InstallIn(SingletonComponent::class)
abstract class RuntimeModule {
    @Binds
    abstract fun runtimeManager(impl: ProotRuntimeManager): RuntimeManager
}
