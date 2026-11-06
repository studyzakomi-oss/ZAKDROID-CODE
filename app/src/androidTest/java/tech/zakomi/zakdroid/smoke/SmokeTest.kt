package tech.zakomi.zakdroid.smoke

import android.view.KeyEvent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rosemoe.sora.widget.CodeEditor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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
import java.io.File

/**
 * Fast smoke tests: no network and no toolchain. They drive the real UI of a debug build
 * (real Hilt graph, real storage) on an emulator.
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {

    private val compose = createAndroidComposeRule<MainActivity>()
    private val ui = Ui(compose)

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ScreenshotOnFailure()).around(compose)

    @Before
    fun startOnWelcome() {
        ui.goToWelcome()
    }

    @After
    fun noCrash() {
        Smoke.setAppLocale("")
        Smoke.assertNoCrashLog()
    }

    @Test
    fun launchShowsWorkbench() {
        for (label in listOf(R.string.action_explorer, R.string.action_run, R.string.action_terminal, R.string.action_settings)) {
            assertTrue("Activity bar item missing: ${str(label, "en")}", ui.hasDescription(str(label, "en")))
        }
        assertTrue("Welcome pane missing", ui.hasText(str(R.string.welcome_open_folder, "en")))
    }

    @Test
    fun createProjectEditSaveAndRunWithoutToolchain() {
        val name = ui.createProject("smoke_c", R.string.template_c)
        waitFor("main.c open in the editor") { onMain { hooks().documents().active?.name } == "main.c" }
        ui.waitForText("main.c")

        // Type into the code editor (sora CodeEditor, an Android View) and save with Ctrl+S.
        onView(isAssignableFrom(CodeEditor::class.java)).perform(click())
        Smoke.instrumentation.sendStringSync("zksmoke42")
        waitFor("typed text in the document") { onMain { hooks().documents().active?.document?.content?.toString() }.orEmpty().contains("zksmoke42") }
        waitFor("tab marked unsaved") { onMain { hooks().documents().active?.dirty } == true }
        Smoke.sendCtrl(KeyEvent.KEYCODE_S)
        val file = File(onMain { hooks().paths().projectsDir }, "$name/main.c")
        waitFor("saved file contains the typed text") { file.readText().contains("zksmoke42") }
        waitFor("tab marked saved") { onMain { hooks().documents().active?.dirty } == false }

        // Without a toolchain, Run opens the toolchain sheet instead of failing.
        if (onMain { hooks().runtime().state.value } !is RuntimeState.Ready) {
            ui.clickDescription(str(R.string.action_run, "en"))
            ui.waitForText(str(R.string.runtime_install, "en"))
            ui.clickText(str(R.string.dialog_close, "en"))
        }
    }

    @Test
    fun newFileAndLocalizedErrorMessages() {
        ui.createProject("smoke_txt", R.string.template_empty)

        // Create and open a file through the Explorer (a drawer on phones).
        ui.clickDescription(str(R.string.action_explorer, "en"))
        ui.clickDescription(str(R.string.explorer_new_file, "en"))
        ui.typeInField("notes.txt")
        ui.clickText(str(R.string.dialog_create, "en"))
        waitFor("notes.txt open in the editor") { onMain { hooks().documents().active?.name } == "notes.txt" }

        // A plain-text file cannot be run: the message must be in the UI language.
        val english = str(R.string.run_not_supported, "en", "Plain Text")
        val arabic = str(R.string.run_not_supported, "ar", "Plain Text")
        assertNotEquals("Arabic resources missing", english, arabic)

        ui.clickDescription(str(R.string.action_run, "en"))
        ui.waitForText(english)

        Smoke.setAppLocale("ar")
        ui.clickDescription(str(R.string.action_run, "ar"))
        ui.waitForText(arabic)
        assertEquals("notes.txt", onMain { hooks().documents().active?.name })
    }

    private fun waitFor(what: String, condition: () -> Boolean) = Smoke.waitFor(what, 20_000, condition = condition)
}
