package tech.zakomi.zakdroid.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallRequestGateTest {
    @Test
    fun `install buttons are only accepted when no run is active or stopping`() {
        assertTrue(RuntimeState.NotInstalled().acceptsInstallRequest())
        assertTrue(RuntimeState.NotInstalled(canResume = true).acceptsInstallRequest())
        assertTrue(RuntimeState.Error(RuntimeErrorKind.Locked, "x", InstallStep.InstallPackages).acceptsInstallRequest())
        assertTrue(RuntimeState.Ready.acceptsInstallRequest())
        assertFalse(RuntimeState.Installing(InstallStep.Download, 5).acceptsInstallRequest())
        assertFalse(RuntimeState.Stopping.acceptsInstallRequest())
    }
}
