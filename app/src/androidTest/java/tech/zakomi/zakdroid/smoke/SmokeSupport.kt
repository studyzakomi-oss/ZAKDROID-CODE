package tech.zakomi.zakdroid.smoke

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.LocaleList
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.EntryPointAccessors
import org.junit.Assert.assertNull
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import tech.zakomi.zakdroid.testing.DebugTestHooks
import java.io.File
import java.util.Locale

/** Helpers shared by the emulator smoke tests. */
object Smoke {
    const val TAG = "ZakdroidSmoke"

    val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    val context: Context get() = instrumentation.targetContext

    fun hooks(): DebugTestHooks = EntryPointAccessors.fromApplication(context.applicationContext, DebugTestHooks::class.java)

    /** Runs [block] on the main thread and returns its result (app state is main-thread only). */
    fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    /** The app's string [id] in [locale] ("en" or "ar"), as the app itself resolves it. */
    fun str(@StringRes id: Int, locale: String, vararg args: Any): String {
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(locale)) }
        return context.createConfigurationContext(config).getString(id, *args)
    }

    /** Sets the app's own language (Android 13+ per-app locale); empty = follow the system. */
    fun setAppLocale(tag: String) {
        check(Build.VERSION.SDK_INT >= 33) { "Per-app locales need Android 13+" }
        onMain {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        }
    }

    /** Polls [condition] off the main thread until it holds or [timeoutMs] passes. */
    fun waitFor(what: String, timeoutMs: Long, pollMs: Long = 500, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (condition()) return
            Thread.sleep(pollMs)
        }
        throw AssertionError("Timed out after ${timeoutMs / 1000} s waiting for: $what")
    }

    /** Ctrl+<key> as from a hardware keyboard (the app's shortcuts). */
    fun sendCtrl(keyCode: Int) {
        val now = SystemClock.uptimeMillis()
        val meta = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        instrumentation.sendKeySync(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
        instrumentation.sendKeySync(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, meta))
    }

    /** The app's crash log (written by CrashLog); must stay absent. */
    fun assertNoCrashLog() {
        val crash = File(context.filesDir, "crashes/last-crash.txt")
        assertNull("The app recorded a crash:\n" + crash.takeIf { it.exists() }?.readText().orEmpty(), crash.takeIf { it.exists() })
    }

    fun uniqueName(prefix: String) = prefix + "_" + (System.currentTimeMillis() % 1_000_000)

    /** The first laid-out view of type [cls] in [activity]'s window, waiting up to 10 s for it to appear. */
    fun <T : View> findView(activity: Activity, cls: Class<T>): T {
        fun search(v: View): View? =
            if (cls.isInstance(v) && v.isShown && v.width > 0) v
            else (v as? ViewGroup)?.let { g -> (0 until g.childCount).firstNotNullOfOrNull { search(g.getChildAt(it)) } }
        fun all(v: View): List<View> = (if (cls.isInstance(v)) listOf(v) else emptyList()) +
            ((v as? ViewGroup)?.let { g -> (0 until g.childCount).flatMap { all(g.getChildAt(it)) } } ?: emptyList())
        var found: View? = null
        try {
            waitFor("${cls.simpleName} on screen", 10_000, pollMs = 200) { found = onMain { search(activity.window.decorView) }; found != null }
        } catch (e: AssertionError) {
            val views = onMain { all(activity.window.decorView).map { "shown=${it.isShown} ${it.width}x${it.height}" } }
            throw AssertionError("${e.message}; instances: $views")
        }
        return cls.cast(found)!!
    }

    /**
     * Taps the middle of [view] like a finger would, then waits until its window has input
     * focus, so that typed keys reach it. Fails with the focused window's name otherwise.
     */
    fun tapAndFocus(view: View) {
        val (x, y) = onMain {
            val loc = IntArray(2).also(view::getLocationOnScreen)
            (loc[0] + view.width / 2f) to (loc[1] + view.height / 2f)
        }
        val down = SystemClock.uptimeMillis()
        instrumentation.sendPointerSync(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0))
        instrumentation.sendPointerSync(MotionEvent.obtain(down, down + 60, MotionEvent.ACTION_UP, x, y, 0))
        try {
            waitFor("input focus on ${view.javaClass.simpleName}", 10_000, pollMs = 200) { onMain { view.hasWindowFocus() } }
        } catch (e: AssertionError) {
            throw AssertionError("${e.message}; focused window: ${focusedWindow()}")
        }
    }

    /** `mCurrentFocus` from `dumpsys window`, for failure messages. */
    fun focusedWindow(): String = runCatching {
        val pfd = instrumentation.uiAutomation.executeShellCommand("dumpsys window displays")
        ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().readLines()
            .filter { "mCurrentFocus" in it || "mFocusedApp" in it }.joinToString(" | ") { it.trim() }
    }.getOrElse { "unknown (${it.message})" }
}

/** On failure, saves a screenshot to the app's external files dir (CI pulls `test-screenshots/`). */
class ScreenshotOnFailure : TestWatcher() {
    override fun failed(e: Throwable?, description: Description) {
        runCatching {
            val dir = File(Smoke.context.getExternalFilesDir(null), "test-screenshots").apply { mkdirs() }
            val bitmap = Smoke.instrumentation.uiAutomation.takeScreenshot() ?: return
            File(dir, "${description.testClass.simpleName}_${description.methodName}.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }.onFailure { Log.w(Smoke.TAG, "Screenshot failed", it) }
    }
}
