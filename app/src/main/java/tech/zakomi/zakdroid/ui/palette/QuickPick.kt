package tech.zakomi.zakdroid.ui.palette

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tech.zakomi.zakdroid.R
import tech.zakomi.zakdroid.core.designsystem.JetBrainsMono
import tech.zakomi.zakdroid.core.designsystem.WorkbenchTheme
import tech.zakomi.zakdroid.core.search.FuzzyMatcher
import tech.zakomi.zakdroid.core.search.WorkspaceFile
import tech.zakomi.zakdroid.editor.extensions.Snippet
import tech.zakomi.zakdroid.editor.outline.DocumentSymbol
import tech.zakomi.zakdroid.editor.outline.SymbolKind

/** What the picker shows; chosen by the first character typed, as in VS Code. */
enum class QuickPickMode(val prefix: String) {
    Files(""),
    Commands(">"),
    Line(":"),
    Symbols("@"),
    Snippets("$");

    companion object {
        fun of(text: String): QuickPickMode = when {
            text.startsWith(Commands.prefix) -> Commands
            text.startsWith(Line.prefix) -> Line
            text.startsWith(Symbols.prefix) -> Symbols
            text.startsWith(Snippets.prefix) -> Snippets
            else -> Files
        }
    }
}

/** Parses "12" or "12:5" (1-based). Returns null if [text] is not a line number. */
internal fun parseLineTarget(text: String): Pair<Int, Int>? {
    val parts = text.trim().split(':', ',')
    if (parts.isEmpty() || parts.size > 2) return null
    val line = parts[0].trim().toIntOrNull()?.takeIf { it >= 1 } ?: return null
    val column = if (parts.size == 2 && parts[1].isNotBlank()) parts[1].trim().toIntOrNull()?.takeIf { it >= 1 } ?: return null else 1
    return line to column
}

private class PickRow(
    val title: AnnotatedString,
    val detail: String? = null,
    val trailing: String? = null,
    val enabled: Boolean = true,
    val onPick: () -> Unit,
)

/**
 * One input for four jobs, as in VS Code: file names (Ctrl+P), `>` commands
 * (Ctrl+Shift+P), `:` line numbers (Ctrl+G) and `@` symbols (Ctrl+Shift+O).
 */
@Composable
fun QuickPick(
    initialMode: QuickPickMode,
    commands: List<WorkbenchCommand>,
    /** Lines of the active file, or 0 when no file is open. */
    lineCount: Int,
    loadSymbols: suspend () -> List<DocumentSymbol>,
    loadSnippets: suspend () -> List<Snippet>,
    onInsertSnippet: (Snippet) -> Unit,
    onOpenFile: (WorkspaceFile) -> Unit,
    onGotoLine: (line: Int, column: Int) -> Unit,
    onGotoSymbol: (DocumentSymbol) -> Unit,
    onDismiss: () -> Unit,
    viewModel: QuickOpenViewModel = hiltViewModel(),
) {
    val colors = WorkbenchTheme
    var input by remember { mutableStateOf(TextFieldValue(initialMode.prefix, TextRange(initialMode.prefix.length))) }
    var selected by remember { mutableIntStateOf(0) }
    val mode = QuickPickMode.of(input.text)
    val query = input.text.removePrefix(mode.prefix).trim()
    val files by viewModel.state.collectAsStateWithLifecycle()
    val symbols by produceState<List<DocumentSymbol>?>(null) { value = loadSymbols() }
    val snippets by produceState<List<Snippet>?>(null) { value = loadSnippets() }
    val focus = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.refresh()
        focus.requestFocus()
    }
    LaunchedEffect(mode, query) {
        if (mode == QuickPickMode.Files) viewModel.setQuery(query)
        selected = 0
    }

    val highlight = SpanStyle(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)
    val kindLabels = SymbolKind.entries.associateWith { stringResource(it.labelRes) }
    val lineHint = stringResource(R.string.quick_line_hint, lineCount.coerceAtLeast(1))
    val noFile = stringResource(R.string.quick_no_file)
    val rows: List<PickRow> = when (mode) {
        QuickPickMode.Files -> files.hits.map { hit ->
            val nameStart = hit.file.relativePath.lastIndexOf('/') + 1
            PickRow(
                title = highlighted(hit.file.entry.name, hit.positions.filter { it >= nameStart }.map { it - nameStart }, highlight),
                detail = hit.file.relativePath.substring(0, (nameStart - 1).coerceAtLeast(0)).ifEmpty { null },
                onPick = { onOpenFile(hit.file) },
            )
        }
        QuickPickMode.Commands -> commands
            .mapNotNull { command -> FuzzyMatcher.match(query, command.title)?.let { command to it } }
            .let { hits -> if (query.isEmpty()) hits else hits.sortedByDescending { it.second.score } }
            .map { (command, hit) -> PickRow(highlighted(command.title, hit.positions, highlight), trailing = command.shortcut, enabled = command.enabled, onPick = command.run) }
        QuickPickMode.Line -> {
            val target = parseLineTarget(query)
            when {
                lineCount <= 0 -> listOf(PickRow(AnnotatedString(noFile), enabled = false, onPick = {}))
                target == null -> listOf(PickRow(AnnotatedString(lineHint), enabled = false, onPick = {}))
                else -> listOf(
                    PickRow(
                        AnnotatedString(stringResource(R.string.quick_go_to_line, target.first.coerceAtMost(lineCount), target.second)),
                        onPick = { onGotoLine(target.first, target.second) },
                    ),
                )
            }
        }
        QuickPickMode.Symbols -> symbols.orEmpty()
            .mapNotNull { symbol -> FuzzyMatcher.match(query, symbol.name)?.let { symbol to it } }
            .let { hits -> if (query.isEmpty()) hits else hits.sortedByDescending { it.second.score } }
            .map { (symbol, hit) ->
                PickRow(
                    title = buildAnnotatedString {
                        append("  ".repeat(if (query.isEmpty()) symbol.depth else 0))
                        append(highlighted(symbol.name, hit.positions, highlight))
                    },
                    detail = kindLabels.getValue(symbol.kind),
                    trailing = ":${symbol.line + 1}",
                    onPick = { onGotoSymbol(symbol) },
                )
            }
        QuickPickMode.Snippets -> snippets.orEmpty()
            .mapNotNull { snippet -> FuzzyMatcher.match(query, snippet.prefix + " " + snippet.name)?.let { snippet to it } }
            .let { hits -> if (query.isEmpty()) hits else hits.sortedByDescending { it.second.score } }
            .map { (snippet, _) ->
                PickRow(
                    title = AnnotatedString(snippet.name),
                    detail = snippet.description.ifEmpty { null },
                    trailing = snippet.prefix.ifEmpty { null },
                    onPick = { onInsertSnippet(snippet) },
                )
            }
    }
    val current = selected.coerceIn(0, (rows.size - 1).coerceAtLeast(0))
    LaunchedEffect(current, rows.size) { if (rows.isNotEmpty()) listState.scrollToItem(current) }

    fun accept(index: Int) {
        val row = rows.getOrNull(index) ?: return
        if (!row.enabled) return
        onDismiss()
        row.onPick()
    }

    val placeholder = stringResource(
        when (mode) {
            QuickPickMode.Files -> R.string.quick_hint_files
            QuickPickMode.Commands -> R.string.quick_hint_commands
            QuickPickMode.Line -> R.string.quick_hint_line
            QuickPickMode.Symbols -> R.string.quick_hint_symbols
            QuickPickMode.Snippets -> R.string.quick_hint_snippets
        },
    )
    val emptyText = when {
        mode == QuickPickMode.Files && !files.hasWorkspace -> stringResource(R.string.quick_no_folder)
        mode == QuickPickMode.Files && files.indexing -> null
        mode == QuickPickMode.Symbols && symbols == null -> null
        mode == QuickPickMode.Symbols && lineCount <= 0 -> noFile
        mode == QuickPickMode.Symbols && query.isEmpty() -> stringResource(R.string.quick_no_symbols)
        mode == QuickPickMode.Snippets && snippets == null -> null
        mode == QuickPickMode.Snippets && lineCount <= 0 -> noFile
        mode == QuickPickMode.Snippets && query.isEmpty() -> stringResource(R.string.quick_no_snippets)
        else -> stringResource(R.string.quick_no_results)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                .statusBarsPadding()
                .imePadding()
                .padding(start = 12.dp, end = 12.dp, top = 40.dp, bottom = 12.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            val shape = RoundedCornerShape(8.dp)
            Column(
                Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .background(colors.sideBar, shape)
                    .border(1.dp, colors.border, shape)
                    // Taps inside the picker must not reach the dismissing scrim.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                    .padding(8.dp),
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    textStyle = TextStyle(color = colors.textPrimary, fontSize = 15.sp),
                    cursorBrush = SolidColor(colors.accentText),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onGo = { accept(current) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(colors.editor, RoundedCornerShape(4.dp))
                        .border(1.dp, colors.accentText, RoundedCornerShape(4.dp))
                        .focusRequester(focus)
                        .semantics { contentDescription = placeholder }
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionDown -> { if (rows.isNotEmpty()) selected = (current + 1) % rows.size; true }
                                Key.DirectionUp -> { if (rows.isNotEmpty()) selected = (current - 1 + rows.size) % rows.size; true }
                                Key.Enter, Key.NumPadEnter -> { accept(current); true }
                                Key.Escape -> { onDismiss(); true }
                                else -> false
                            }
                        },
                    decorationBox = { inner ->
                        Box(Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
                            if (input.text == mode.prefix) {
                                Row {
                                    // Keep the hint to the right of a typed prefix such as ">".
                                    Text(mode.prefix, color = Color.Transparent, fontSize = 15.sp)
                                    Text(placeholder, color = colors.textSecondary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            inner()
                        }
                    },
                )
                if ((mode == QuickPickMode.Files && files.indexing) || (mode == QuickPickMode.Symbols && symbols == null)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = colors.accentText, trackColor = Color.Transparent)
                } else {
                    Spacer(Modifier.height(2.dp))
                }
                if (rows.isEmpty() && emptyText != null) {
                    Text(emptyText, color = colors.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(10.dp))
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                    itemsIndexed(rows) { index, row ->
                        val isSelected = index == current
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 40.dp)
                                .background(if (isSelected) colors.selection else Color.Transparent, RoundedCornerShape(4.dp))
                                .clickable(enabled = row.enabled) { accept(index) }
                                .semantics {
                                    role = Role.Button
                                    this.selected = isSelected
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val titleColor = when {
                                !row.enabled -> colors.textSecondary
                                isSelected -> colors.selectionText
                                else -> colors.textPrimary
                            }
                            Column(Modifier.weight(1f)) {
                                Text(row.title, color = titleColor, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                row.detail?.let {
                                    Text(it, color = if (isSelected) colors.selectionText else colors.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.StartEllipsis)
                                }
                            }
                            row.trailing?.let {
                                Spacer(Modifier.width(8.dp))
                                Text(it, color = if (isSelected) colors.selectionText else colors.textSecondary, fontFamily = JetBrainsMono, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun highlighted(text: String, positions: List<Int>, style: SpanStyle): AnnotatedString = buildAnnotatedString {
    append(text)
    for (p in positions) if (p in text.indices) addStyle(style, p, p + 1)
}

internal val SymbolKind.labelRes: Int
    get() = when (this) {
        SymbolKind.Function -> R.string.symbol_function
        SymbolKind.Class -> R.string.symbol_class
        SymbolKind.Struct -> R.string.symbol_struct
        SymbolKind.Enum -> R.string.symbol_enum
        SymbolKind.Namespace -> R.string.symbol_namespace
        SymbolKind.Macro -> R.string.symbol_macro
    }
