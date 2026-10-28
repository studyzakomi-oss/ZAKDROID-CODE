package tech.zakomi.zakdroid.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.zakomi.zakdroid.core.designsystem.JetBrainsMono
import tech.zakomi.zakdroid.core.designsystem.WorkbenchTheme
import tech.zakomi.zakdroid.editor.R

/** VS Code-style find/replace widget with case, whole-word and regex toggles. */
@Composable
fun FindReplaceBar(controller: EditorController, modifier: Modifier = Modifier) {
    val colors = WorkbenchTheme
    val state by controller.search.collectAsState()
    var query by rememberSaveable { mutableStateOf(state.query) }
    var replacement by rememberSaveable { mutableStateOf("") }
    var showReplace by rememberSaveable { mutableStateOf(false) }
    var matchCase by rememberSaveable { mutableStateOf(false) }
    var wholeWord by rememberSaveable { mutableStateOf(false) }
    var regex by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(query, matchCase, wholeWord, regex) {
        controller.search(SearchQuery(query, regex, matchCase, wholeWord))
    }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        modifier
            .fillMaxWidth()
            .background(colors.sideBar)
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { showReplace = !showReplace }, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (showReplace) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.editor_toggle_replace),
                    tint = colors.textSecondary,
                )
            }
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.editor_find),
                isError = state.error != null,
                modifier = Modifier.weight(1f).focusRequester(focus),
                onImeAction = { controller.next() },
            )
            Toggle("Aa", stringResource(R.string.editor_match_case), matchCase) { matchCase = it }
            Toggle("ab", stringResource(R.string.editor_whole_word), wholeWord, underline = true) { wholeWord = it }
            Toggle(".*", stringResource(R.string.editor_regex), regex) { regex = it }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 36.dp)) {
            val label = when {
                state.error != null -> state.error!!
                query.isEmpty() -> ""
                state.matchCount == 0 -> stringResource(R.string.editor_no_results)
                else -> stringResource(R.string.editor_match_count, state.currentMatch, state.matchCount)
            }
            Text(
                label,
                color = if (state.error != null) colors.error else colors.textSecondary,
                style = TextStyle(fontSize = 12.sp),
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            IconButton(onClick = controller::previous, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.editor_find_previous), tint = colors.textPrimary)
            }
            IconButton(onClick = controller::next, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.editor_find_next), tint = colors.textPrimary)
            }
            IconButton(onClick = controller::closeSearch, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Close, stringResource(R.string.editor_find_close), tint = colors.textPrimary)
            }
        }
        if (showReplace) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 36.dp)) {
                SearchField(
                    value = replacement,
                    onValueChange = { replacement = it },
                    placeholder = stringResource(R.string.editor_replace),
                    isError = false,
                    modifier = Modifier.weight(1f),
                    onImeAction = { controller.replace(replacement) },
                )
                TextButton(onClick = { controller.replace(replacement) }) {
                    Text(stringResource(R.string.editor_replace_one), color = colors.accentText)
                }
                TextButton(onClick = { controller.replaceAll(replacement) }) {
                    Text(stringResource(R.string.editor_replace_all), color = colors.accentText)
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isError: Boolean,
    onImeAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WorkbenchTheme
    val shape = RoundedCornerShape(4.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = colors.textPrimary, fontFamily = JetBrainsMono, fontSize = 14.sp),
        cursorBrush = SolidColor(colors.accentText),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onImeAction() }),
        modifier = modifier
            .height(36.dp)
            .background(colors.editor, shape)
            .border(1.dp, if (isError) colors.error else colors.border, shape)
            .semantics { contentDescription = placeholder },
        decorationBox = { inner ->
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (value.isEmpty()) {
                    Text(placeholder, color = colors.textSecondary, fontSize = 14.sp)
                }
                inner()
            }
        },
    )
}

@Composable
private fun Toggle(label: String, description: String, checked: Boolean, underline: Boolean = false, onChange: (Boolean) -> Unit) {
    val colors = WorkbenchTheme
    Spacer(Modifier.width(2.dp))
    TextButton(
        onClick = { onChange(!checked) },
        modifier = Modifier
            .size(width = 40.dp, height = 36.dp)
            .background(if (checked) colors.selection else colors.sideBar, RoundedCornerShape(4.dp))
            .semantics {
                role = Role.Switch
                selected = checked
                contentDescription = description
            },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Text(
            label,
            color = if (checked) colors.selectionText else colors.textSecondary,
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            textDecoration = if (underline) androidx.compose.ui.text.style.TextDecoration.Underline else null,
        )
    }
}

