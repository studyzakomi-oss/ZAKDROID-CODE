package tech.zakomi.zakdroid.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.zakomi.zakdroid.R
import tech.zakomi.zakdroid.core.designsystem.JetBrainsMono
import tech.zakomi.zakdroid.core.designsystem.WorkbenchTheme
import tech.zakomi.zakdroid.core.search.TextMatch
import tech.zakomi.zakdroid.core.search.WorkspaceFile

/** The Search view of the Side Bar: find text in all files of the open folder. */
@Composable
fun SearchPane(
    state: SearchUiState,
    viewModel: SearchViewModel,
    /** Changes whenever the view is opened by shortcut, so the query field takes focus again. */
    focusRequest: Int,
    onOpenMatch: (WorkspaceFile, TextMatch) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WorkbenchTheme
    val focus = remember { FocusRequester() }
    LaunchedEffect(focusRequest) { if (focusRequest > 0 && state.hasWorkspace) focus.requestFocus() }

    Column(modifier.fillMaxSize().background(colors.sideBar)) {
        Row(Modifier.fillMaxWidth().height(40.dp).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.search_title),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (state.hasWorkspace) {
                IconButton(onClick = viewModel::searchNow, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.Refresh, stringResource(R.string.search_refresh), tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                }
            }
        }
        if (!state.hasWorkspace) {
            Text(stringResource(R.string.search_no_folder), color = colors.textSecondary, modifier = Modifier.padding(16.dp))
            return@Column
        }

        val hint = stringResource(R.string.search_hint)
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val shape = RoundedCornerShape(4.dp)
            BasicTextField(
                value = state.query.text,
                onValueChange = viewModel::setText,
                singleLine = true,
                textStyle = TextStyle(color = colors.textPrimary, fontFamily = JetBrainsMono, fontSize = 14.sp),
                cursorBrush = SolidColor(colors.accentText),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onSearch = { viewModel.searchNow() }),
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .background(colors.editor, shape)
                    .border(1.dp, if (state.patternError != null) colors.error else colors.border, shape)
                    .focusRequester(focus)
                    .semantics { contentDescription = hint },
                decorationBox = { inner ->
                    Box(Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
                        if (state.query.text.isEmpty()) Text(hint, color = colors.textSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        inner()
                    }
                },
            )
        }
        Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            Toggle("Aa", stringResource(R.string.search_match_case), state.query.matchCase, onClick = viewModel::toggleMatchCase)
            Toggle("ab", stringResource(R.string.search_whole_word), state.query.wholeWord, underline = true, onClick = viewModel::toggleWholeWord)
            Toggle(".*", stringResource(R.string.search_regex), state.query.regex, onClick = viewModel::toggleRegex)
        }
        if (state.searching) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = colors.accentText, trackColor = Color.Transparent)
        } else {
            Spacer(Modifier.height(2.dp))
        }

        val results = state.results
        val summary = when {
            state.patternError != null -> stringResource(R.string.search_bad_pattern, state.patternError)
            results == null -> null
            results.isEmpty() && !state.searching -> stringResource(R.string.search_no_results)
            results.isEmpty() -> null
            else -> {
                val matches = pluralStringResource(R.plurals.search_matches, state.matchCount, state.matchCount)
                val files = pluralStringResource(R.plurals.search_files, results.size, results.size)
                stringResource(if (state.truncated) R.string.search_summary_truncated else R.string.search_summary, matches, files)
            }
        }
        if (summary != null) {
            Text(
                summary,
                color = if (state.patternError != null) colors.error else colors.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        val highlight = SpanStyle(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline, color = colors.textPrimary)
        LazyColumn(Modifier.fillMaxSize()) {
            results.orEmpty().forEach { group ->
                val key = group.file.entry.ref.key
                val open = key !in state.collapsed
                item(key = key) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 36.dp)
                            .clickable { viewModel.toggleCollapsed(key) }
                            .semantics { role = Role.Button }
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (open) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                        Column(Modifier.weight(1f).padding(start = 4.dp)) {
                            Text(group.file.entry.name, color = colors.textPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val folder = group.file.relativePath.substringBeforeLast('/', "")
                            if (folder.isNotEmpty()) {
                                Text(folder, color = colors.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.StartEllipsis)
                            }
                        }
                        Text(
                            group.matches.size.toString(),
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.background(colors.editor, RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
                if (open) {
                    items(group.matches.size, key = { i -> "$key#${group.matches[i].line}:${group.matches[i].startColumn}" }) { i ->
                        val match = group.matches[i]
                        // Code is left-to-right also when the UI is Arabic.
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 32.dp)
                                    .clickable { onOpenMatch(group.file, match) }
                                    .padding(start = 28.dp, end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    (match.line + 1).toString(),
                                    color = colors.textSecondary,
                                    fontFamily = JetBrainsMono,
                                    fontSize = 11.sp,
                                    modifier = Modifier.width(36.dp),
                                )
                                Text(
                                    preview(match, highlight),
                                    color = colors.textSecondary,
                                    fontFamily = JetBrainsMono,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The matched line, cut so the match stays visible in a narrow side bar. */
private fun preview(match: TextMatch, highlight: SpanStyle) = buildAnnotatedString {
    val text = match.lineText
    val start = (match.startColumn - match.previewOffset).coerceIn(0, text.length)
    val end = (match.endColumn - match.previewOffset).coerceIn(start, text.length)
    val lead = text.substring(0, start).trimStart()
    append(if (lead.length > LEAD_CHARS) "…" + lead.takeLast(LEAD_CHARS) else lead)
    withStyle(highlight) { append(text.substring(start, end)) }
    append(text.substring(end).take(TAIL_CHARS))
}

private const val LEAD_CHARS = 24
private const val TAIL_CHARS = 160

@Composable
private fun Toggle(label: String, description: String, checked: Boolean, underline: Boolean = false, onClick: () -> Unit) {
    val colors = WorkbenchTheme
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .size(width = 40.dp, height = 36.dp)
            .background(if (checked) colors.selection else colors.sideBar, RoundedCornerShape(4.dp))
            .semantics {
                role = Role.Switch
                selected = checked
                contentDescription = description
            },
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(
            label,
            color = if (checked) colors.selectionText else colors.textSecondary,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            textDecoration = if (underline) TextDecoration.Underline else null,
        )
    }
}
