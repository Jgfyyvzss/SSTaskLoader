package com.soaringscoring.taskloader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.soaringscoring.taskloader.ui.AppViewModel
import com.soaringscoring.taskloader.ui.screens.ContestListScreen
import com.soaringscoring.taskloader.ui.screens.SettingsScreen
import com.soaringscoring.taskloader.ui.screens.TaskListScreen
import com.soaringscoring.taskloader.ui.screens.UploadScreen

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(viewModel)
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(viewModel: AppViewModel) {
    val navController: NavHostController = rememberNavController()
    val state by viewModel.uiState.collectAsState()

    val mediaTreePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.onMediaTreeChosen(uri)
        }
    }

    NavHost(navController = navController, startDestination = "contests") {
        composable("contests") {
            ContestListScreen(
                state = state,
                onContestClick = {
                    viewModel.selectContest(it)
                    navController.navigate("tasks")
                },
                onSettingsClick = { navController.navigate("settings") },
                onUploadClick = { navController.navigate("upload") },
                onRetry = { viewModel.loadContests() },
                onToggleFolder = { viewModel.toggleFolderSelected(it.doc) },
                onSelectTimeFrame = { viewModel.selectTimeFrame(it) }
            )
        }
        composable("tasks") {
            val contest = state.selectedContest
            if (contest != null) {
                TaskListScreen(
                    contest = contest,
                    state = state,
                    onBack = {
                        viewModel.clearSelectedContest()
                        navController.popBackStack()
                    },
                    onSelectClass = { viewModel.selectClass(it) },
                    onDownload = { viewModel.downloadTask(it) },
                    onDownloadWaypoints = { viewModel.downloadWaypoints() },
                    onDismissStatus = { viewModel.clearStatusMessage() }
                )
            }
        }
        composable("settings") {
            SettingsScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onSave = {
                    viewModel.saveApiKey(it)
                    navController.popBackStack()
                },
                onChooseMediaFolder = {
                    // Point the system picker at Android/media as a starting hint.
                    mediaTreePicker.launch(null)
                },
                onSaveUploadSettings = { key, address ->
                    viewModel.saveUploadSettings(key, address)
                    navController.popBackStack()
                }
            )
        }
        composable("upload") {
            UploadScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onRefresh = { viewModel.refreshIgcFiles() },
                onSelectFile = { viewModel.selectFileForUpload(it) },
                onCancelPending = { viewModel.cancelPendingUpload() },
                onConfirmUpload = { viewModel.confirmUpload() },
                onDismissOutcome = { viewModel.dismissUploadOutcome() }
            )
        }
    }
}
