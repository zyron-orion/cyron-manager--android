package com.cyron.manager.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import com.cyron.manager.domain.model.DownloadSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "cyron_settings")

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val keys = Keys()

    val settings: Flow<DownloadSettings> = context.settingsDataStore.data
        .catch { error -> if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error }
        .map { prefs ->
            DownloadSettings(
                maxConcurrentDownloads = prefs[keys.maxConcurrent]?.coerceIn(1, 8) ?: 3,
                defaultConnections = prefs[keys.connections]?.coerceIn(1, 16) ?: 8,
                darkTheme = prefs[keys.dark] ?: true,
                followSystemTheme = prefs[keys.followSystem] ?: false,
                notificationsEnabled = prefs[keys.notifications] ?: true,
                wifiOnly = prefs[keys.wifiOnly] ?: false,
                historyRetentionDays = prefs[keys.historyRetention]?.coerceIn(1, 365) ?: 30,
                defaultFolderUri = prefs[keys.folder],
                language = prefs[keys.language]?.takeIf { it in setOf("en", "fa") }
                    ?: if (java.util.Locale.getDefault().language.equals("fa", true)) "fa" else "en"
            )
        }

    suspend fun setMaxConcurrent(value: Int) = context.settingsDataStore.edit { it[keys.maxConcurrent] = value.coerceIn(1, 8) }
    suspend fun setConnections(value: Int) = context.settingsDataStore.edit { it[keys.connections] = value.coerceIn(1, 16) }
    suspend fun setDarkTheme(value: Boolean) = context.settingsDataStore.edit { it[keys.dark] = value }
    suspend fun setFollowSystemTheme(value: Boolean) = context.settingsDataStore.edit { it[keys.followSystem] = value }
    suspend fun setLanguage(value: String) = context.settingsDataStore.edit { it[keys.language] = value.takeIf { it in setOf("en", "fa") } ?: "en" }
    suspend fun setNotifications(value: Boolean) = context.settingsDataStore.edit { it[keys.notifications] = value }
    suspend fun setWifiOnly(value: Boolean) = context.settingsDataStore.edit { it[keys.wifiOnly] = value }
    suspend fun setHistoryRetentionDays(value: Int) = context.settingsDataStore.edit { it[keys.historyRetention] = value.coerceIn(1, 365) }
    suspend fun setFolder(uri: String) = context.settingsDataStore.edit { it[keys.folder] = uri }

    private class Keys {
        val maxConcurrent = intPreferencesKey("max_concurrent_downloads")
        val connections = intPreferencesKey("default_connections")
        val dark = booleanPreferencesKey("dark_theme")
        val followSystem = booleanPreferencesKey("follow_system_theme")
        val notifications = booleanPreferencesKey("notifications_enabled")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val historyRetention = intPreferencesKey("history_retention_days")
        val folder = stringPreferencesKey("destination_folder_uri")
        val language = stringPreferencesKey("interface_language")
    }
}
