package tech.zakomi.zakdroid.runtime.install

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tech.zakomi.zakdroid.runtime.RuntimeErrorKind
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Guarantees that at most one installer touches the rootfs at a time:
 * - an in-process [Mutex]: a new run (e.g. Retry right after Cancel) waits until the
 *   previous run has finished its cleanup;
 * - an OS file lock on [lockFile]: also excludes any other process of the app. The OS
 *   releases it when its holder dies, so a lock left by a killed app never blocks.
 *
 * [active] is true from the moment a run asks for the guard until it has fully cleaned up.
 */
class InstallGuard(private val lockFile: File) {
    private val mutex = Mutex()
    private val waiting = AtomicInteger(0)
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    suspend fun <T> withInstallLock(onLockBusy: () -> Unit, describe: () -> String, block: suspend () -> T): T {
        _active.value = waiting.incrementAndGet() > 0
        try {
            return mutex.withLock {
                lockFile.parentFile?.mkdirs()
                RandomAccessFile(lockFile, "rw").use { file ->
                    val lock = acquire(file, onLockBusy)
                    try {
                        file.setLength(0)
                        file.write(describe().toByteArray())
                        block()
                    } finally {
                        lock.release()
                    }
                }
            }
        } finally {
            _active.value = waiting.decrementAndGet() > 0
        }
    }

    private suspend fun acquire(file: RandomAccessFile, onLockBusy: () -> Unit): FileLock {
        repeat(LOCK_ATTEMPTS) {
            val lock = try {
                file.channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
            if (lock != null) return lock
            onLockBusy()
            delay(LOCK_RETRY_MS)
        }
        throw InstallException(RuntimeErrorKind.Locked, "Another installation is still running")
    }

    private companion object {
        const val LOCK_ATTEMPTS = 20
        const val LOCK_RETRY_MS = 500L
    }
}
