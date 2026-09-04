package com.soaringscoring.taskloader.ui

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soaringscoring.taskloader.BuildConfig
import com.soaringscoring.taskloader.api.ApiResult
import com.soaringscoring.taskloader.api.Contest
import com.soaringscoring.taskloader.api.ContestClass
import com.soaringscoring.taskloader.api.SoaringScoringApi
import com.soaringscoring.taskloader.api.TaskRow
import com.soaringscoring.taskloader.api.UploadResult
import com.soaringscoring.taskloader.data.SettingsRepository
import com.soaringscoring.taskloader.storage.IgcFile
import com.soaringscoring.taskloader.storage.XcsoarFolderStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TargetFolder(val doc: DocumentFile, val selected: Boolean)

sealed class UploadOutcome {
    data class Success(val result: UploadResult) : UploadOutcome()
    data class Failure(val message: String) : UploadOutcome()
}

data class AppUiState(
    val apiKey: String = "",
    val personalKeyOverride: String = "",
    val mediaTreeUri: Uri? = null,
    val targetFolders: List<TargetFolder> = emptyList(),

    val contests: List<Contest> = emptyList(),
    val contestsLoading: Boolean = false,
    val contestsError: String? = null,
    val selectedTimeFrame: ContestTimeFrame = ContestTimeFrame.CURRENT,

    val selectedContest: Contest? = null,
    val tasks: List<TaskRow> = emptyList(),
    val tasksLoading: Boolean = false,
    val tasksError: String? = null,

    val classes: List<ContestClass> = emptyList(),
    val classesLoading: Boolean = false,
    val classesError: String? = null,
    val selectedClass: ContestClass? = null,

    val downloadingTaskId: String? = null,
    val downloadingWaypoints: Boolean = false,
    val statusMessage: String? = null,

    // --- Flight upload ---
    val uploadApiKey: String = "",
    val entryAddress: String = "",
    val igcFiles: List<IgcFile> = emptyList(),
    val igcFilesLoading: Boolean = false,
    val pendingUploadFile: IgcFile? = null,
    val isUploading: Boolean = false,
    val uploadOutcome: UploadOutcome? = null
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
            val uploadKey = settings.uploadApiKey.first()
            val address = settings.entryAddress.first()
            _uiState.value = _uiState.value.copy(
                apiKey = effectiveKey,
                personalKeyOverride = savedKey,
                mediaTreeUri = treeUriString?.let(Uri::parse),
                uploadApiKey = uploadKey,
                entryAddress = address
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
            tasksError = null,
            classes = emptyList(),
            classesError = null,
            selectedClass = null
        )
        viewModelScope.launch { settings.setLastContest(contest.id, contest.name) }
        loadTasks(contest)
        loadClasses(contest)
    }

    fun clearSelectedContest() {
        _uiState.value = _uiState.value.copy(
            selectedContest = null,
            tasks = emptyList(),
            tasksError = null,
            classes = emptyList(),
            classesError = null,
            selectedClass = null
        )
    }

    fun selectTimeFrame(timeFrame: ContestTimeFrame) {
        _uiState.value = _uiState.value.copy(selectedTimeFrame = timeFrame)
    }

    fun loadClasses(contest: Contest) {
        val key = _uiState.value.apiKey.ifBlank { null }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(classesLoading = true, classesError = null)
            when (val result = api.getClasses(contest.id, key)) {
                is ApiResult.Success -> {
                    val classes = result.data
                    _uiState.value = _uiState.value.copy(
                        classes = classes,
                        classesLoading = false,
                        // Auto-select when there's only one class - saves a tap for
                        // single-class contests, matching common practice.
                        selectedClass = classes.singleOrNull()
                    )
                }
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(
                    classesLoading = false,
                    classesError = describeError(result)
                )
            }
        }
    }

    fun selectClass(contestClass: ContestClass) {
        _uiState.value = _uiState.value.copy(selectedClass = contestClass)
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
        loadContests()
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

    /**
     * Downloads the SeeYou .cup waypoint file, once per contest rather than once
     * per task - the underlying turnpoint set is the same all week even though
     * the API bundles it with a specific day's task in the file itself. Any task
     * row's `files.seeyouCup` URL points at the same waypoint database, so we
     * just need one - the earliest day, for a stable/predictable choice.
     */
    fun downloadWaypoints() {
        val state = _uiState.value
        val key = state.apiKey
        val selectedFolders = state.targetFolders.filter { it.selected }
        val sourceTask = state.tasks.minByOrNull { it.dayNumber }

        if (key.isBlank()) {
            _uiState.value = state.copy(statusMessage = "Add an API key in Settings first.")
            return
        }
        if (selectedFolders.isEmpty()) {
            _uiState.value = state.copy(statusMessage = "Choose at least one XCSoar folder first.")
            return
        }
        if (sourceTask == null) {
            _uiState.value = state.copy(statusMessage = "No tasks loaded yet for this contest.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(downloadingWaypoints = true, statusMessage = null)
            when (val result = api.downloadTaskFile(sourceTask.files.seeyouCup, key)) {
                is ApiResult.Success -> {
                    var okCount = 0
                    selectedFolders.forEach { folder ->
                        val ok = XcsoarFolderStore.writeWaypointFile(
                            getApplication(),
                            folder.doc,
                            "soaringscoring_waypoint.cup",
                            result.data
                        )
                        if (ok) okCount++
                    }
                    _uiState.value = _uiState.value.copy(
                        downloadingWaypoints = false,
                        statusMessage = if (okCount == selectedFolders.size)
                            "Waypoints loaded into $okCount folder(s)."
                        else
                            "Loaded into $okCount of ${selectedFolders.size} folder(s) — check permissions."
                    )
                }
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(
                    downloadingWaypoints = false,
                    statusMessage = "Download failed: ${describeError(result)}"
                )
            }
        }
    }

    fun clearStatusMessage() {
        _uiState.value = _uiState.value.copy(statusMessage = null)
    }

    // --- Flight upload ---

    fun saveUploadSettings(uploadApiKey: String, entryAddress: String) {
        _uiState.value = _uiState.value.copy(uploadApiKey = uploadApiKey, entryAddress = entryAddress)
        viewModelScope.launch { settings.setUploadSettings(uploadApiKey, entryAddress) }
    }

    /** Scans every selected XCSoar folder's logs (recent versions) for .igc files. */
    fun refreshIgcFiles() {
        val selectedFolders = _uiState.value.targetFolders.filter { it.selected }
        if (selectedFolders.isEmpty()) {
            _uiState.value = _uiState.value.copy(igcFiles = emptyList())
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(igcFilesLoading = true)
            val found = withContext(Dispatchers.IO) {
                selectedFolders.flatMap { XcsoarFolderStore.findIgcFiles(getApplication(), it.doc) }
            }
            _uiState.value = _uiState.value.copy(
                igcFiles = found.sortedByDescending { it.doc.lastModified() },
                igcFilesLoading = false
            )
        }
    }

    fun selectFileForUpload(file: IgcFile) {
        _uiState.value = _uiState.value.copy(pendingUploadFile = file)
    }

    fun cancelPendingUpload() {
        _uiState.value = _uiState.value.copy(pendingUploadFile = null)
    }

    fun confirmUpload() {
        val state = _uiState.value
        val file = state.pendingUploadFile ?: return
        val key = state.uploadApiKey
        val address = state.entryAddress

        if (key.isBlank() || address.isBlank()) {
            _uiState.value = state.copy(
                pendingUploadFile = null,
                uploadOutcome = UploadOutcome.Failure("Set your upload API key and entry address in Settings first.")
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(pendingUploadFile = null, isUploading = true)
            val bytes = try {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(file.doc.uri)?.use { it.readBytes() }
                }
            } catch (e: Exception) {
                null
            }

            if (bytes == null) {
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    uploadOutcome = UploadOutcome.Failure("Could not read that file.")
                )
                return@launch
            }

            when (val result = api.uploadFlight(address, key, bytes, file.doc.name ?: "flight.igc")) {
                is ApiResult.Success -> _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    uploadOutcome = UploadOutcome.Success(result.data)
                )
                is ApiResult.Failure -> _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    uploadOutcome = UploadOutcome.Failure(describeUploadError(result))
                )
            }
        }
    }

    private fun describeUploadError(failure: ApiResult.Failure): String = when (failure.code) {
        "MISSING_API_KEY" -> "No upload API key set. Add one in Settings."
        "INVALID_API_KEY" -> "That upload API key is invalid or has been revoked."
        "INSUFFICIENT_SCOPE" -> "This key doesn't have the flights:write scope."
        "INVALID_ADDRESS" -> "That entry address doesn't look right - check it against the pilot downloads page."
        "ENTRY_NOT_FOUND" -> "No contest entry matches that address - check the competition number and contest key."
        "NO_OFFICIAL_TASK" -> "No official task is set yet for your class today."
        else -> failure.message
    }

    fun dismissUploadOutcome() {
        _uiState.value = _uiState.value.copy(uploadOutcome = null)
    }
}
