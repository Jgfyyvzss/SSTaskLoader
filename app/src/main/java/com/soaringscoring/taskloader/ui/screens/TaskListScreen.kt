package com.soaringscoring.taskloader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.soaringscoring.taskloader.util.dateOnly

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    contest: Contest,
    state: AppUiState,
    onBack: () -> Unit,
    onDownload: (TaskRow) -> Unit,
    onDismissStatus: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(contest.name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            SelectedFoldersSummary(state)
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
private fun SelectedFoldersSummary(state: AppUiState) {
    val selectedNames = state.targetFolders.filter { it.selected }.mapNotNull { it.doc.name }
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            if (selectedNames.isEmpty()) "No folders selected — pick some on the contest list screen"
            else "Saving to: ${selectedNames.joinToString(", ")}",
            style = MaterialTheme.typography.bodySmall
        )
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
                append(dateOnly(task.date))
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
