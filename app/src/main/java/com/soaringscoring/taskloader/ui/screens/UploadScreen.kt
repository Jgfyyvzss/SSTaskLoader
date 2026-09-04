package com.soaringscoring.taskloader.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.soaringscoring.taskloader.storage.IgcFile
import com.soaringscoring.taskloader.ui.AppUiState
import com.soaringscoring.taskloader.ui.UploadOutcome
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(
    state: AppUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectFile: (IgcFile) -> Unit,
    onCancelPending: () -> Unit,
    onConfirmUpload: () -> Unit,
    onDismissOutcome: () -> Unit
) {
    LaunchedEffect(Unit) { onRefresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upload flight") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.uploadApiKey.isBlank() || state.entryAddress.isBlank() -> Box(
                    Modifier.fillMaxSize().padding(24.dp)
                ) {
                    Text(
                        "Set your upload API key and entry address in Settings first — " +
                            "these are personal to you, separate from the app's built-in key.",
                        modifier = Modifier.align(Alignment.Center),
                        textAlign = TextAlign.Center
                    )
                }
                state.igcFilesLoading -> Box(Modifier.fillMaxSize()) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                state.igcFiles.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp)) {
                    Text(
                        "No .igc flight logs found in the logs folder of your selected XCSoar app(s).",
                        modifier = Modifier.align(Alignment.Center),
                        textAlign = TextAlign.Center
                    )
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.igcFiles) { file ->
                        IgcFileCard(file = file, onClick = { onSelectFile(file) })
                    }
                }
            }

            if (state.isUploading) {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Card {
                        Row(
                            Modifier.padding(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Uploading…")
                        }
                    }
                }
            }
        }
    }

    state.pendingUploadFile?.let { file ->
        AlertDialog(
            onDismissRequest = onCancelPending,
            title = { Text("Upload this flight?") },
            text = {
                Text("${file.doc.name}\n\nSends to entry ${state.entryAddress}.")
            },
            confirmButton = {
                TextButton(onClick = onConfirmUpload) { Text("Upload") }
            },
            dismissButton = {
                TextButton(onClick = onCancelPending) { Text("Cancel") }
            }
        )
    }

    state.uploadOutcome?.let { outcome ->
        UploadOutcomeDialog(outcome = outcome, onDismiss = onDismissOutcome)
    }
}

@Composable
private fun IgcFileCard(file: IgcFile, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.UploadFile, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(file.doc.name ?: "flight.igc", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                val modified = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(Date(file.doc.lastModified()))
                val sizeKb = file.doc.length() / 1024
                Text(
                    "$modified · ${sizeKb}KB · ${file.sourceFolderName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun UploadOutcomeDialog(outcome: UploadOutcome, onDismiss: () -> Unit) {
    when (outcome) {
        is UploadOutcome.Success -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Uploaded") },
            text = {
                Column {
                    if (outcome.result.validationOk) {
                        Text("Flight uploaded successfully.")
                    } else {
                        Text("Flight uploaded, but validation found issues:")
                        Spacer(Modifier.height(8.dp))
                        outcome.result.validationIssues.forEach { issue ->
                            Text("• $issue", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
        )
        is UploadOutcome.Failure -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                Icon(
                    Icons.Filled.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Upload failed") },
            text = { Text(outcome.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
        )
    }
}
