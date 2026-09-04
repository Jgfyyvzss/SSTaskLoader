package com.soaringscoring.taskloader.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
    onChooseMediaFolder: () -> Unit,
    onSaveUploadSettings: (String, String) -> Unit
) {
    var text by remember(state.personalKeyOverride) { mutableStateOf(state.personalKeyOverride) }
    var reveal by remember { mutableStateOf(false) }

    var uploadKeyText by remember(state.uploadApiKey) { mutableStateOf(state.uploadApiKey) }
    var uploadKeyReveal by remember { mutableStateOf(false) }
    var entryAddressText by remember(state.entryAddress) { mutableStateOf(state.entryAddress) }

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
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
        ) {
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
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Save")
                }
            }
            HorizontalDivider()

            Column(Modifier.padding(16.dp)) {
                Text("Flight upload", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Uploading is a personal action tied to you, not the app - it needs your " +
                        "own API key (flights:write scope) and your entry address for the " +
                        "contest, both from SoaringScoring's pilot downloads page.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = uploadKeyText,
                    onValueChange = { uploadKeyText = it },
                    label = { Text("Upload API key") },
                    singleLine = true,
                    visualTransformation = if (uploadKeyReveal) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { uploadKeyReveal = !uploadKeyReveal }) {
                            Text(if (uploadKeyReveal) "Hide" else "Show")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = entryAddressText,
                    onValueChange = { entryAddressText = it },
                    label = { Text("Entry address (e.g. c2-skyrace)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onSaveUploadSettings(uploadKeyText.trim(), entryAddressText.trim()) },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Save")
                }
            }
        }
    }
}
