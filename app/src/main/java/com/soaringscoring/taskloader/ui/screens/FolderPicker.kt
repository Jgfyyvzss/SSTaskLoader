package com.soaringscoring.taskloader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.soaringscoring.taskloader.ui.AppUiState
import com.soaringscoring.taskloader.ui.TargetFolder

/**
 * Lets the user grant access to Android/media once, then tick which
 * XCSoar-variant folders (XCSoar, XCSoar Jet, ...) new tasks get written to.
 * Selection lives in AppViewModel so it's shared across every screen.
 */
@Composable
fun FolderPicker(
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
                "Grant access to the Android/media folder once — that's where XCSoar and " +
                    "XCSoar Jet each keep their own Tasks folder. If you only have one variant " +
                    "installed, picking that app's folder directly also works.",
                style = MaterialTheme.typography.bodySmall
            )
        } else if (state.targetFolders.isEmpty()) {
            Text(
                "No XCSoar-like folders found there. Make sure you picked Android/media " +
                    "(or an XCSoar app's own folder) — not some other folder.",
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
