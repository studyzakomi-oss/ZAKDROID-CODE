package tech.zakomi.zakdroid.ui.explorer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import tech.zakomi.zakdroid.R
import tech.zakomi.zakdroid.core.fs.FileNames
import tech.zakomi.zakdroid.core.workspace.ProjectTemplate

@Composable
fun NewProjectDialog(onDismiss: () -> Unit, onCreate: (name: String, template: ProjectTemplate?) -> Unit) {
    var name by rememberSaveable { mutableStateOf("hello") }
    var templateIndex by rememberSaveable { mutableIntStateOf(0) }
    val options: List<Pair<ProjectTemplate?, Int>> = listOf(
        ProjectTemplate.CHello to R.string.template_c,
        ProjectTemplate.CppHello to R.string.template_cpp,
        ProjectTemplate.PythonScript to R.string.template_python,
        null to R.string.template_empty,
    )
    val error = FileNames.validate(name.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.project_new_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.project_name)) },
                    singleLine = true,
                    isError = name.isNotEmpty() && error != null,
                    supportingText = if (name.isNotEmpty() && error != null) ({ Text(error) }) else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.project_template), style = MaterialTheme.typography.labelLarge)
                Column(Modifier.selectableGroup()) {
                    options.forEachIndexed { index, (_, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .selectable(selected = templateIndex == index, onClick = { templateIndex = index }, role = Role.RadioButton),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = templateIndex == index, onClick = null)
                            Text(stringResource(label))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = error == null, onClick = { onCreate(name.trim(), options[templateIndex].first) }) {
                Text(stringResource(R.string.dialog_create))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}
