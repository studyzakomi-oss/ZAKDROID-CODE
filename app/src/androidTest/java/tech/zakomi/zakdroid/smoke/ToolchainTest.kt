package tech.zakomi.zakdroid.smoke

import android.Manifest
import android.os.Build
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.termux.view.TerminalView
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import tech.zakomi.zakdroid.MainActivity
import tech.zakomi.zakdroid.R
import tech.zakomi.zakdroid.runtime.RuntimeState
import tech.zakomi.zakdroid.smoke.Smoke.hooks
import tech.zakomi.zakdroid.smoke.Smoke.onMain
import tech.zakomi.zakdroid.smoke.Smoke.str

/**
 * Heavier test: installs the real toolchain (Alpine + PRoot, about 300 MB from the Alpine
 * mirror), then uses the terminal and runs a C and a Python program. Needs network.
 * CI runs it after [SmokeTest] in the same emulator.
 */
@RunWith(AndroidJUnit4::class)
class ToolchainTest {

    private val compose = createAndroidComposeRule<MainActivity>()
    private val ui = Ui(compose)

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ScreenshotOnFailure()).around(compose)

    @Before
    fun setUp() {
        if (Build.VERSION.SDK_INT >= 33) {
            Smoke.instrumentation.uiAutomation.grantRuntimePermission(Smoke.context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        ui.goToWelcome()
    }

    @After
    fun noCrash() = Smoke.assertNoCrashLog()

    @Test
    fun installToolchainUseTerminalAndRunPrograms() {
        ui.createProject("tc_c", R.string.template_c)
        waitFor("main.c open") { onMain { hooks().documents().active?.name } == "main.c" }

        // 1. Install the toolchain through the UI (status bar → sheet → Install).
        if (onMain { hooks().runtime().state.value } !is RuntimeState.Ready) {
            ui.clickText(str(R.string.runtime_not_installed, "en"))
            ui.clickText(str(R.string.runtime_install, "en"))
            var last: RuntimeState? = null
            Smoke.waitFor("toolchain installed", timeoutMs = 30 * 60_000L, pollMs = 5_000) {
                val state = onMain { hooks().runtime().state.value }
                if (state != last) Log.i(Smoke.TAG, "Runtime state: $state").also { last = state }
                if (state is RuntimeState.Error) throw AssertionError("Toolchain install failed: ${state.kind} at ${state.step}: ${state.message}")
                state is RuntimeState.Ready
            }
            if (ui.hasText(str(R.string.dialog_close, "en"))) ui.clickText(str(R.string.dialog_close, "en"))
        }

        // 2. Terminal: open a shell, type a command, read the result from the screen buffer.
        ui.clickDescription(str(R.string.action_terminal, "en"))
        if (onMain { hooks().terminals().tabs.value.none { it.running } }) {
            ui.clickDescription(str(R.string.terminal_new, "en"))
        }
        waitFor("a running shell") { onMain { hooks().terminals().tabs.value.any { it.running } } }
        Smoke.tapAndFocus(Smoke.findView(compose.activity, TerminalView::class.java))
        Smoke.instrumentation.sendStringSync("echo zak\$((6*7))\n")
        waitForTranscript("shell output zak42", 60_000) { it.contains("zak42") }

        // 3. Run main.c (clang inside Alpine): output and exit code 0.
        runActiveFile()
        waitForTranscript("C program output and exit code", 5 * 60_000L) {
            it.contains("Hello from zakdroid-code!") && it.contains("exited with code 0")
        }

        // 4. Python: the template asks for a name on stdin.
        ui.goToWelcome()
        ui.createProject("tc_py", R.string.template_python)
        waitFor("main.py open") { onMain { hooks().documents().active?.name } == "main.py" }
        runActiveFile()
        waitForTranscript("Python prompt", 2 * 60_000L) { it.contains("Your name:") }
        Smoke.tapAndFocus(Smoke.findView(compose.activity, TerminalView::class.java))
        Smoke.instrumentation.sendStringSync("Zakomi\n")
        waitForTranscript("Python output and exit code", 60_000) {
            it.contains("Hello, Zakomi! Welcome to zakdroid-code.") && it.contains("exited with code 0")
        }
    }

    // ---- helpers ---------------------------------------------------------------------------

    /** Taps Run and waits for the new terminal tab it opens. */
    private fun runActiveFile() {
        val before = onMain { hooks().terminals().tabs.value.map { it.id }.toSet() }
        ui.clickDescription(str(R.string.action_run, "en"))
        waitFor("a new run terminal") { onMain { hooks().terminals().tabs.value.any { it.id !in before } } }
    }

    /** Waits until the active terminal's text satisfies [condition]; logs the text on timeout. */
    private fun waitForTranscript(what: String, timeoutMs: Long, condition: (String) -> Boolean) {
        var text = ""
        try {
            Smoke.waitFor(what, timeoutMs, pollMs = 1_000) {
                text = onMain {
                    val terminals = hooks().terminals()
                    terminals.activeId.value?.let { terminals.session(it)?.emulator?.screen?.transcriptText }
                }.orEmpty()
                condition(text)
            }
        } catch (e: AssertionError) {
            val report = terminalReport()
            Log.e(Smoke.TAG, report)
            throw AssertionError("${e.message}\n$report")
        }
    }

    /** State and text of every terminal tab, for failure messages. */
    private fun terminalReport(): String = onMain {
        val terminals = hooks().terminals()
        buildString {
            appendLine("--- terminals (active=${terminals.activeId.value}) ---")
            for (tab in terminals.tabs.value) {
                val session = terminals.session(tab.id)
                appendLine("tab ${tab.id} '${tab.title}' kind=${tab.kind} running=${tab.running} exit=${tab.exitCode} emulator=${session?.emulator != null}")
                session?.emulator?.screen?.transcriptText?.takeLast(1500)?.let { appendLine(it) }
            }
        }
    }

    private fun waitFor(what: String, condition: () -> Boolean) = Smoke.waitFor(what, 20_000, condition = condition)
}
