package tech.zakomi.zakdroid.system

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Battery-management checks. Honor (MagicOS) and Huawei (EMUI/HarmonyOS) stop background apps
 * aggressively unless the app is exempted from battery optimization and set to "Manage manually"
 * in the vendor's App launch screen. Long installs and builds need both.
 */
object BackgroundRestrictions {

    enum class Vendor { HonorOrHuawei, Other }

    val vendor: Vendor
        get() = when (Build.MANUFACTURER.lowercase()) {
            "honor", "huawei" -> Vendor.HonorOrHuawei
            else -> Vendor.Other
        }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    /** Asks Android to exempt the app from battery optimization (system dialog), with a fallback to the list. */
    @SuppressLint("BatteryLife") // Distributed via GitHub/F-Droid; keeping user-started builds alive is the core use case.
    fun requestIgnoreBatteryOptimizations(context: Context) {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())
        if (!start(context, direct)) start(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    /**
     * Opens the vendor's App launch / startup manager if one is found on this device, otherwise
     * the app's own settings page. Returns true if the vendor screen was opened.
     */
    fun openVendorLaunchSettings(context: Context): Boolean {
        val candidates = listOf(
            ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        )
        for (component in candidates) {
            if (start(context, Intent().setComponent(component))) return true
        }
        start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
        return false
    }

    /** One line for diagnostics. */
    fun describe(context: Context): String =
        "battery optimization ${if (isIgnoringBatteryOptimizations(context)) "ignored (exempt)" else "ACTIVE (restricted)"}, vendor=$vendor"

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
