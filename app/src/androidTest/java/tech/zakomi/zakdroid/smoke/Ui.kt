package tech.zakomi.zakdroid.smoke

import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import tech.zakomi.zakdroid.R
import tech.zakomi.zakdroid.smoke.Smoke.hooks
import tech.zakomi.zakdroid.smoke.Smoke.onMain
import tech.zakomi.zakdroid.smoke.Smoke.str

/**
 * UI steps that act only on nodes that are on screen. On phones the Explorer lives in a
 * closed navigation drawer just left of the screen, and it repeats some labels.
 */
class Ui(private val compose: ComposeTestRule) {

    private fun visibleIndex(nodes: SemanticsNodeInteractionCollection): Int? =
        nodes.fetchSemanticsNodes().indexOfFirst { it.boundsInRoot.left >= 0f && it.boundsInRoot.width > 0f }.takeIf { it >= 0 }

    fun hasText(text: String) = visibleIndex(compose.onAllNodesWithText(text)) != null
    fun hasDescription(text: String) = visibleIndex(compose.onAllNodesWithContentDescription(text)) != null

    fun waitForText(text: String, timeoutMs: Long = 20_000) = compose.waitUntil(timeoutMs) { hasText(text) }
    fun waitForDescription(text: String, timeoutMs: Long = 20_000) = compose.waitUntil(timeoutMs) { hasDescription(text) }

    fun clickText(text: String) {
        waitForText(text)
        val nodes = compose.onAllNodesWithText(text)
        nodes[visibleIndex(nodes)!!].performClick()
    }

    fun clickDescription(text: String) {
        waitForDescription(text)
        val nodes = compose.onAllNodesWithContentDescription(text)
        nodes[visibleIndex(nodes)!!].performClick()
    }

    /** Replaces the text of the (single) visible text field, e.g. in a dialog. */
    fun typeInField(value: String) {
        compose.waitUntil(10_000) { visibleIndex(compose.onAllNodes(hasSetTextAction())) != null }
        val nodes = compose.onAllNodes(hasSetTextAction())
        nodes[visibleIndex(nodes)!!].performTextReplacement(value)
    }

    /** Closes any open workspace so the Welcome pane shows. */
    fun goToWelcome() {
        onMain { hooks().session().close() }
        waitForText(str(R.string.welcome_new_project, "en"))
    }

    /** Creates an internal project from the Welcome pane; returns its name. */
    fun createProject(prefix: String, @StringRes templateLabel: Int): String {
        val name = Smoke.uniqueName(prefix)
        clickText(str(R.string.welcome_new_project, "en"))
        waitForText(str(R.string.project_new_title, "en"))
        typeInField(name)
        clickText(str(templateLabel, "en"))
        clickText(str(R.string.dialog_create, "en"))
        Smoke.waitFor("project $name open", 20_000) { onMain { hooks().session().current.value?.name } == name }
        return name
    }
}
