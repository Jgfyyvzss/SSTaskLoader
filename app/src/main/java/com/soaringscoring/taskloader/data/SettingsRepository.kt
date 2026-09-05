package com.soaringscoring.taskloader.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ss_task_loader_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val API_KEY = stringPreferencesKey("api_key")
        val LAST_CONTEST_ID = stringPreferencesKey("last_contest_id")
        val LAST_CONTEST_NAME = stringPreferencesKey("last_contest_name")
        val MEDIA_TREE_URI = stringPreferencesKey("media_tree_uri")
        val UPLOAD_API_KEY = stringPreferencesKey("upload_api_key")
        val ENTRY_ADDRESS = stringPreferencesKey("entry_address")
        val SELECTED_FOLDER_URIS = stringSetPreferencesKey("selected_folder_uris")
    }

    val apiKey: Flow<String> = context.dataStore.data.map { it[Keys.API_KEY].orEmpty() }
    val lastContestId: Flow<String?> = context.dataStore.data.map { it[Keys.LAST_CONTEST_ID] }
    val lastContestName: Flow<String?> = context.dataStore.data.map { it[Keys.LAST_CONTEST_NAME] }
    val mediaTreeUri: Flow<String?> = context.dataStore.data.map { it[Keys.MEDIA_TREE_URI] }
    val uploadApiKey: Flow<String> = context.dataStore.data.map { it[Keys.UPLOAD_API_KEY].orEmpty() }
    val entryAddress: Flow<String> = context.dataStore.data.map { it[Keys.ENTRY_ADDRESS].orEmpty() }
    val selectedFolderUris: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.SELECTED_FOLDER_URIS] ?: emptySet() }

    suspend fun setApiKey(value: String) {
        context.dataStore.edit { it[Keys.API_KEY] = value }
    }

    suspend fun setLastContest(id: String, name: String) {
        context.dataStore.edit {
            it[Keys.LAST_CONTEST_ID] = id
            it[Keys.LAST_CONTEST_NAME] = name
        }
    }

    suspend fun setMediaTreeUri(uri: String) {
        context.dataStore.edit { it[Keys.MEDIA_TREE_URI] = uri }
    }

    suspend fun setUploadSettings(uploadApiKey: String, entryAddress: String) {
        context.dataStore.edit {
            it[Keys.UPLOAD_API_KEY] = uploadApiKey
            it[Keys.ENTRY_ADDRESS] = entryAddress
        }
    }

    suspend fun setSelectedFolderUris(uris: Set<String>) {
        context.dataStore.edit { it[Keys.SELECTED_FOLDER_URIS] = uris }
    }
}
