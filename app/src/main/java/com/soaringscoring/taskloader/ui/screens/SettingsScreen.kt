package com.soaringscoring.taskloader.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.soaringscoring.taskloader.ui.AppUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: AppUiState,
    onBack: () -> Unit,
    onSave: (String) -> Unit,
    onChooseMediaFolder: () -> Unit
) {
    var text by remember(state.personalKeyOverride) { mutableStateOf(state.personalKeyOverride) }
    var reveal by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            MediaFolderAccessSetting(state, onChooseMediaFolder)
            HorizontalDivider()

            Column(Modifier.padding(16.dp)) {
                Text(
                    "This app ships with a built-in SoaringScoring API key, so most people " +
                        "don't need to do anything here. Only set your own key below if you've " +
                        "been issued a personal one to test with.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("API key override (optional)") },
                    singleLine = true,
                    visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { reveal = !reveal }) {
                            Text(if (reveal) "Hide" else "Show")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onSave(text.trim()) },
                    modifier = Modifier.align(androidx.compose.ui.Alignment.End)
                ) {
                    Text("Save")
                }
            }
        }
    }
}
