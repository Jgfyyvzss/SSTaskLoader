package com.soaringscoring.taskloader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.soaringscoring.taskloader.api.Contest
import com.soaringscoring.taskloader.ui.AppUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContestListScreen(
    state: AppUiState,
    onContestClick: (Contest) -> Unit,
    onSettingsClick: () -> Unit,
    onRetry: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SS Task Loader") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.contestsLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.contestsError != null -> ErrorWithRetry(state.contestsError, onRetry)
                state.contests.isEmpty() -> Text(
                    "No contests found.",
                    Modifier.align(Alignment.Center)
                )
                else -> LazyColumn {
                    items(state.contests) { contest ->
                        ListItem(
                            headlineContent = { Text(contest.name) },
                            supportingContent = {
                                Text("${contest.startDate} – ${contest.endDate}")
                            },
                            modifier = Modifier.clickable { onContestClick(contest) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorWithRetry(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}
