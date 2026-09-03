package com.soaringscoring.taskloader.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
    }

    val apiKey: Flow<String> = context.dataStore.data.map { it[Keys.API_KEY].orEmpty() }
    val lastContestId: Flow<String?> = context.dataStore.data.map { it[Keys.LAST_CONTEST_ID] }
    val lastContestName: Flow<String?> = context.dataStore.data.map { it[Keys.LAST_CONTEST_NAME] }
    val mediaTreeUri: Flow<String?> = context.dataStore.data.map { it[Keys.MEDIA_TREE_URI] }

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
}
