package tech.zakomi.zakdroid.runtime

/** Ordered install steps. [weight] is the share of the overall progress bar. */
enum class InstallStep(val weight: Int) {
    CheckSpace(1),
    Download(10),
    Verify(2),
    Extract(10),
    Configure(1),
    InstallPackages(74),
    VerifyToolchain(2);

    /** Overall progress (0..100) when this step is [fraction] (0..1) done. */
    fun overallPercent(fraction: Float): Int {
        val before = entries.takeWhile { it != this }.sumOf { it.weight }
        return (before + weight * fraction.coerceIn(0f, 1f)).toInt().coerceIn(0, 100)
    }
}

/** [Locked]: the package database stayed locked even after leftover installer processes were killed. */
enum class RuntimeErrorKind { NotEnoughSpace, Network, Checksum, Extract, Packages, Verification, UnsupportedDevice, Locked, Unknown }

sealed interface RuntimeState {
    /** Nothing installed, or an install was cancelled ([canResume] = true). */
    data class NotInstalled(val canResume: Boolean = false) : RuntimeState

    data class Installing(val step: InstallStep, val percent: Int, val detail: String? = null) : RuntimeState

    /** A run was cancelled or stopped and is still killing its processes; no new run may start yet. */
    data object Stopping : RuntimeState

    data object Ready : RuntimeState

    data class Error(val kind: RuntimeErrorKind, val message: String, val step: InstallStep?) : RuntimeState
}

/** Install, Resume, Retry, Repair and Reinstall are only accepted when no run is active or stopping. */
fun RuntimeState.acceptsInstallRequest(): Boolean = this !is RuntimeState.Installing && this != RuntimeState.Stopping
