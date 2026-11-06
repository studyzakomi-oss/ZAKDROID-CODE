package tech.zakomi.zakdroid.testing

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import tech.zakomi.zakdroid.core.workspace.StoragePaths
import tech.zakomi.zakdroid.runtime.RuntimeManager
import tech.zakomi.zakdroid.state.OpenDocumentsStore
import tech.zakomi.zakdroid.state.WorkspaceSession
import tech.zakomi.zakdroid.terminal.TerminalSessionManager

/**
 * Access to app singletons for the emulator smoke tests (app/src/androidTest).
 * Debug builds only: this source set is not part of release APKs.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DebugTestHooks {
    fun terminals(): TerminalSessionManager
    fun runtime(): RuntimeManager
    fun documents(): OpenDocumentsStore
    fun paths(): StoragePaths
    fun session(): WorkspaceSession
}
