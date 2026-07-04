package com.nofar.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.downloadPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "download_preferences"
)

interface DownloadPreferences {
    val wifiOnlyDownloads: Flow<Boolean>

    suspend fun setWifiOnlyDownloads(enabled: Boolean)
}

@Singleton
class DefaultDownloadPreferences
@Inject
constructor(@ApplicationContext context: Context) : DownloadPreferences {
    private val dataStore = context.downloadPreferencesDataStore

    override val wifiOnlyDownloads: Flow<Boolean> =
        dataStore.data.map { prefs ->
            prefs[WIFI_ONLY_KEY] ?: false
        }

    override suspend fun setWifiOnlyDownloads(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[WIFI_ONLY_KEY] = enabled
        }
    }

    companion object {
        private val WIFI_ONLY_KEY = booleanPreferencesKey("wifi_only_downloads")
    }
}
