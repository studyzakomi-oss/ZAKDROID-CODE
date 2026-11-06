package tech.zakomi.zakdroid.smoke

import android.view.KeyEvent
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
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

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ScreenshotOnFailure()).around(compose)

    @Before
    fun startOnWelcome() {
        onMain { hooks().session().close() }
        waitForText(str(R.string.welcome_new_project, "en"))
    }

    @After
    fun noCrash() {
        Smoke.setAppLocale("")
        Smoke.assertNoCrashLog()
    }

    @Test
    fun launchShowsWorkbench() {
        for (label in listOf(R.string.action_explorer, R.string.action_run, R.string.action_terminal, R.string.action_settings)) {
            compose.onNodeWithContentDescription(str(label, "en")).assertExists()
        }
        compose.onNodeWithText(str(R.string.welcome_open_folder, "en")).assertExists()
    }

    @Test
    fun createProjectEditSaveAndRunWithoutToolchain() {
        val name = createProject("smoke_c", R.string.template_c)
        waitFor("main.c open in the editor") { onMain { hooks().documents().active?.name } == "main.c" }
        assertTrue("main.c tab shown", compose.onAllNodesWithText("main.c").fetchSemanticsNodes().isNotEmpty())

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
            compose.onNodeWithContentDescription(str(R.string.action_run, "en")).performClick()
            waitForText(str(R.string.runtime_install, "en"))
            compose.onNodeWithText(str(R.string.dialog_close, "en")).performClick()
        }
    }

    @Test
    fun newFileAndLocalizedErrorMessages() {
        createProject("smoke_txt", R.string.template_empty)

        // Create and open a file through the Explorer (a drawer on phones).
        compose.onNodeWithContentDescription(str(R.string.action_explorer, "en")).performClick()
        waitForContentDescription(str(R.string.explorer_new_file, "en"))
        compose.onNodeWithContentDescription(str(R.string.explorer_new_file, "en")).performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("notes.txt")
        compose.onNodeWithText(str(R.string.dialog_create, "en")).performClick()
        waitFor("notes.txt open in the editor") { onMain { hooks().documents().active?.name } == "notes.txt" }

        // A plain-text file cannot be run: the message must be in the UI language.
        val english = str(R.string.run_not_supported, "en", "Plain Text")
        val arabic = str(R.string.run_not_supported, "ar", "Plain Text")
        assertNotEquals("Arabic resources missing", english, arabic)

        compose.onNodeWithContentDescription(str(R.string.action_run, "en")).performClick()
        waitForText(english)

        Smoke.setAppLocale("ar")
        waitForContentDescription(str(R.string.action_run, "ar"))
        compose.onNodeWithContentDescription(str(R.string.action_run, "ar")).performClick()
        waitForText(arabic)
        assertEquals("notes.txt", onMain { hooks().documents().active?.name })
    }

    // ---- helpers ---------------------------------------------------------------------------

    /** Creates an internal project from the Welcome pane; returns its name. */
    private fun createProject(prefix: String, templateLabel: Int): String {
        val name = Smoke.uniqueName(prefix)
        compose.onNodeWithText(str(R.string.welcome_new_project, "en")).performClick()
        waitForText(str(R.string.project_new_title, "en"))
        compose.onNode(hasSetTextAction()).performTextReplacement(name)
        compose.onNodeWithText(str(templateLabel, "en")).performClick()
        compose.onNodeWithText(str(R.string.dialog_create, "en")).performClick()
        waitFor("project $name open") { onMain { hooks().session().current.value?.name } == name }
        return name
    }

    private fun waitForText(text: String, timeoutMs: Long = 20_000) =
        compose.waitUntil(timeoutMs) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForContentDescription(text: String, timeoutMs: Long = 20_000) =
        compose.waitUntil(timeoutMs) { compose.onAllNodesWithContentDescription(text).fetchSemanticsNodes().isNotEmpty() }

    private fun waitFor(what: String, condition: () -> Boolean) = Smoke.waitFor(what, 20_000, condition = condition)
}
