package tech.zakomi.zakdroid.editor.ui

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.subscribeAlways
import tech.zakomi.zakdroid.core.R as CoreR
import tech.zakomi.zakdroid.editor.EditorLanguage
import tech.zakomi.zakdroid.editor.document.EditorDocument
import tech.zakomi.zakdroid.editor.textmate.ZakdroidColorScheme

data class EditorViewSettings(
    val fontSizeSp: Int,
    val ligatures: Boolean,
    val wordWrap: Boolean,
    val tabSize: Int,
    val lineNumbers: Boolean = true,
    val showWhitespace: Boolean = false,
    val stickyScroll: Boolean = true,
)

/**
 * Hosts one sora [CodeEditor] and swaps the shown [EditorDocument] in place, so
 * tab switches keep each document's cursor and undo history. Code always lays
 * out left-to-right, also when the UI runs in Arabic (RTL).
 */
@Composable
fun CodeEditorPane(
    document: EditorDocument,
    settings: EditorViewSettings,
    controller: EditorController,
    languageFactory: (EditorLanguage) -> Language,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                CodeEditor(context).apply {
                    layoutDirection = View.LAYOUT_DIRECTION_LTR
                    textDirection = View.TEXT_DIRECTION_LTR
                    colorScheme = ZakdroidColorScheme(ThemeRegistry.getInstance())
                    typefaceText = ResourcesCompat.getFont(context, CoreR.font.jetbrains_mono_regular)
                    typefaceLineNumber = typefaceText
                    isLineNumberEnabled = true
                    setPinLineNumber(true)
                    isHighlightCurrentLine = true
                    isHighlightBracketPair = true
                    props.stickyScroll = true
                    props.stickyScrollMaxLines = 3
                    isScalable = true
                    setCursorAnimationEnabled(true)
                    subscribeAlways<SelectionChangeEvent> { event ->
                        controller.onCursorMoved(event.left.line, event.left.column, event.right.index - event.left.index)
                    }
                    subscribeAlways<ContentChangeEvent> { controller.refreshHistory() }
                    subscribeAlways<PublishSearchResultEvent> { controller.onSearchResultPublished() }
                    controller.attach(this)
                }
            },
            update = { editor ->
                if (editor.text !== document.content) {
                    editor.searcher.stopSearch()
                    editor.setText(document.content, true, null)
                    editor.setEditorLanguage(languageFactory(document.language))
                    controller.onCursorMoved(editor.cursor.leftLine, editor.cursor.leftColumn, editor.cursor.right - editor.cursor.left)
                    controller.refreshHistory()
                    controller.onDocumentShown(document)
                }
                editor.setTextSize(settings.fontSizeSp.toFloat())
                editor.setLigatureEnabled(settings.ligatures)
                if (editor.isWordwrap != settings.wordWrap) editor.setWordwrap(settings.wordWrap)
                editor.tabWidth = settings.tabSize
                if (editor.isLineNumberEnabled != settings.lineNumbers) editor.isLineNumberEnabled = settings.lineNumbers
                val whitespace = if (settings.showWhitespace) WHITESPACE_FLAGS else 0
                if (editor.nonPrintablePaintingFlags != whitespace) editor.nonPrintablePaintingFlags = whitespace
                if (editor.props.stickyScroll != settings.stickyScroll) {
                    editor.props.stickyScroll = settings.stickyScroll
                    editor.invalidate()
                }
            },
            onRelease = { editor ->
                controller.detach(editor)
                editor.release()
            },
        )
    }
}

private const val WHITESPACE_FLAGS = CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or
    CodeEditor.FLAG_DRAW_WHITESPACE_INNER or
    CodeEditor.FLAG_DRAW_WHITESPACE_TRAILING or
    CodeEditor.FLAG_DRAW_WHITESPACE_IN_SELECTION

/** Keeps one [EditorController] for the lifetime of the composition. */
@Composable
fun rememberEditorController(): EditorController = remember { EditorController() }
