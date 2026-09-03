package com.soaringscoring.taskloader.ui

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soaringscoring.taskloader.BuildConfig
import com.soaringscoring.taskloader.api.ApiResult
import com.soaringscoring.taskloader.api.Contest
import com.soaringscoring.taskloader.api.SoaringScoringApi
import com.soaringscoring.taskloader.api.TaskRow
import com.soaringscoring.taskloader.data.SettingsRepository
import com.soaringscoring.taskloader.storage.XcsoarFolderStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class TargetFolder(val doc: DocumentFile, val selected: Boolean)

data class AppUiState(
    val apiKey: String = "",
    val personalKeyOverride: String = "",
    val mediaTreeUri: Uri? = null,
    val targetFolders: List<TargetFolder> = emptyList(),

    val contests: List<Contest> = emptyList(),
    val contestsLoading: Boolean = false,
    val contestsError: String? = null,

    val selectedContest: Contest? = null,
    val tasks: List<TaskRow> = emptyList(),
    val tasksLoading: Boolean = false,
    val tasksError: String? = null,

    val downloadingTaskId: String? = null,
    val statusMessage: String? = null
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val api = SoaringScoringApi()
    private val settings = SettingsRepository(application)

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val savedKey = settings.apiKey.first()
            val effectiveKey = savedKey.ifBlank { BuildConfig.SS_API_KEY }
            val treeUriString = settings.mediaTreeUri.first()
            _uiState.value = _uiState.value.copy(
                apiKey = effectiveKey,
                personalKeyOverride = savedKey,
                mediaTreeUri = treeUriString?.let(Uri::parse)
            )
            treeUriString?.let { refreshTargetFolders(Uri.parse(it)) }
            loadContests()
        }
    }

    fun loadContests() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(contestsLoading = true, contestsError = null)
            val key = _uiState.value.apiKey.ifBlank { null }
            when (val result = api.getContests(key)) {
                is ApiResult.Success -> _uiState.value = _uiState.value.copy(
                    contests = result.data.sortedByDescending { it.startDate },
                    contestsLoading = false
                )
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(
                    contestsLoading = false,
                    contestsError = describeError(result)
                )
            }
        }
    }

    fun selectContest(contest: Contest) {
        _uiState.value = _uiState.value.copy(
            selectedContest = contest,
            tasks = emptyList(),
            tasksError = null
        )
        viewModelScope.launch { settings.setLastContest(contest.id, contest.name) }
        loadTasks(contest)
    }

    fun clearSelectedContest() {
        _uiState.value = _uiState.value.copy(selectedContest = null, tasks = emptyList(), tasksError = null)
    }

    fun loadTasks(contest: Contest) {
        val key = _uiState.value.apiKey
        if (key.isBlank()) {
            _uiState.value = _uiState.value.copy(
                tasksError = "Add an API key with the tasks:read scope in Settings first."
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(tasksLoading = true, tasksError = null)
            when (val result = api.getTasks(contest.id, key)) {
                is ApiResult.Success -> _uiState.value = _uiState.value.copy(
                    tasks = result.data.tasks.sortedWith(
                        compareBy({ it.dayNumber }, { it.className ?: "" })
                    ),
                    tasksLoading = false
                )
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(
                    tasksLoading = false,
                    tasksError = describeError(result)
                )
            }
        }
    }

    private fun describeError(failure: ApiResult.Failure): String = when (failure.code) {
        "MISSING_API_KEY" -> "No API key set. Add one in Settings."
        "INVALID_API_KEY" -> "That API key is invalid or has been revoked. Check it in Settings."
        "INSUFFICIENT_SCOPE" -> "This key doesn't have the tasks:read scope. Ask SoaringScoring to add it."
        else -> failure.message
    }

    fun saveApiKey(key: String) {
        val effectiveKey = key.ifBlank { BuildConfig.SS_API_KEY }
        _uiState.value = _uiState.value.copy(apiKey = effectiveKey, personalKeyOverride = key)
        viewModelScope.launch { settings.setApiKey(key) }
    }

    // --- Folder selection (SAF) ---

    /** Call after ACTION_OPEN_DOCUMENT_TREE returns a uri for Android/media. */
    fun onMediaTreeChosen(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        resolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        _uiState.value = _uiState.value.copy(mediaTreeUri = uri)
        viewModelScope.launch { settings.setMediaTreeUri(uri.toString()) }
        refreshTargetFolders(uri)
    }

    private fun refreshTargetFolders(uri: Uri) {
        val found = XcsoarFolderStore.findXcsoarFolders(getApplication(), uri)
        _uiState.value = _uiState.value.copy(
            targetFolders = found.map { TargetFolder(it, selected = true) }
        )
    }

    fun toggleFolderSelected(doc: DocumentFile) {
        _uiState.value = _uiState.value.copy(
            targetFolders = _uiState.value.targetFolders.map {
                if (it.doc.uri == doc.uri) it.copy(selected = !it.selected) else it
            }
        )
    }

    // --- Download ---

    fun downloadTask(task: TaskRow) {
        val state = _uiState.value
        val key = state.apiKey
        val selectedFolders = state.targetFolders.filter { it.selected }
        if (key.isBlank()) {
            _uiState.value = state.copy(statusMessage = "Add an API key in Settings first.")
            return
        }
        if (selectedFolders.isEmpty()) {
            _uiState.value = state.copy(statusMessage = "Choose at least one XCSoar folder first.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(downloadingTaskId = task.taskId, statusMessage = null)
            when (val result = api.downloadTaskFile(task.files.xcsoarTsk, key)) {
                is ApiResult.Success -> {
                    var okCount = 0
                    selectedFolders.forEach { folder ->
                        val ok = XcsoarFolderStore.writeTaskFile(
                            getApplication(),
                            folder.doc,
                            "soaringscoring_task.tsk",
                            result.data
                        )
                        if (ok) okCount++
                    }
                    _uiState.value = _uiState.value.copy(
                        downloadingTaskId = null,
                        statusMessage = if (okCount == selectedFolders.size)
                            "Task loaded into $okCount folder(s)."
                        else
                            "Loaded into $okCount of ${selectedFolders.size} folder(s) — check permissions."
                    )
                }
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(
                    downloadingTaskId = null,
                    statusMessage = "Download failed: ${describeError(result)}"
                )
            }
        }
    }

    fun clearStatusMessage() {
        _uiState.value = _uiState.value.copy(statusMessage = null)
    }
}
