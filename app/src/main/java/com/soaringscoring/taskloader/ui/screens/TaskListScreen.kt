package com.soaringscoring.taskloader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.soaringscoring.taskloader.api.Contest
import com.soaringscoring.taskloader.api.TaskRow
import com.soaringscoring.taskloader.ui.AppUiState
import com.soaringscoring.taskloader.ui.TargetFolder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    contest: Contest,
    state: AppUiState,
    onBack: () -> Unit,
    onChooseMediaFolder: () -> Unit,
    onToggleFolder: (TargetFolder) -> Unit,
    onDownload: (TaskRow) -> Unit,
    onDismissStatus: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(contest.name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = {
            state.statusMessage?.let { msg ->
                Snackbar(
                    modifier = Modifier.padding(12.dp),
                    action = { TextButton(onClick = onDismissStatus) { Text("OK") } }
                ) { Text(msg) }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            FolderPicker(state, onChooseMediaFolder, onToggleFolder)
            HorizontalDivider()

            when {
                state.tasksLoading -> Box(Modifier.fillMaxSize()) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                state.tasksError != null -> Box(Modifier.fillMaxSize().padding(24.dp)) {
                    Text(state.tasksError, modifier = Modifier.align(Alignment.Center))
                }
                state.tasks.isEmpty() -> Box(Modifier.fillMaxSize()) {
                    Text("No published tasks yet.", Modifier.align(Alignment.Center))
                }
                else -> LazyColumn {
                    items(state.tasks) { task ->
                        TaskRowItem(
                            task = task,
                            isDownloading = state.downloadingTaskId == task.taskId,
                            onDownload = { onDownload(task) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderPicker(
    state: AppUiState,
    onChooseMediaFolder: () -> Unit,
    onToggleFolder: (TargetFolder) -> Unit
) {
    Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Folder, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("XCSoar folders", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onChooseMediaFolder) {
                Text(if (state.mediaTreeUri == null) "Choose Android/media" else "Change")
            }
        }
        if (state.mediaTreeUri == null) {
            Text(
                "Grant access to the Android/media folder once — that's where XCSoar and XCSoar Jet keep their Tasks folder.",
                style = MaterialTheme.typography.bodySmall
            )
        } else if (state.targetFolders.isEmpty()) {
            Text(
                "No XCSoar-like folders found under Android/media. Is XCSoar installed?",
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            state.targetFolders.forEach { folder ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onToggleFolder(folder) }
                ) {
                    Checkbox(checked = folder.selected, onCheckedChange = { onToggleFolder(folder) })
                    Text(folder.doc.name ?: "(unnamed)")
                }
            }
        }
    }
}

@Composable
private fun TaskRowItem(task: TaskRow, isDownloading: Boolean, onDownload: () -> Unit) {
    ListItem(
        headlineContent = {
            Text("Day ${task.dayNumber} — ${task.className ?: task.displayLabel}")
        },
        supportingContent = {
            val extra = buildString {
                append(task.date)
                if (task.isOfficialTask) append(" · official")
                task.dhtHandicap?.let { append(" · handicap $it") }
            }
            Text(extra)
        },
        trailingContent = {
            if (isDownloading) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onDownload) {
                    Icon(Icons.Filled.Download, contentDescription = "Load into XCSoar")
                }
            }
        }
    )
}
