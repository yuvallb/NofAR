package com.nofar.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nofar.core.model.AppConfig
import com.nofar.core.model.AppLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences"
)

interface UserPreferencesRepository {
    val wifiOnlyDownloads: Flow<Boolean>

    val demCacheLimitBytes: Flow<Long>

    val showRawSensorOverlay: Flow<Boolean>

    val keepRawGeoTiff: Flow<Boolean>

    suspend fun setWifiOnlyDownloads(enabled: Boolean)

    suspend fun setDemCacheLimitBytes(bytes: Long)

    suspend fun setShowRawSensorOverlay(enabled: Boolean)

    suspend fun setKeepRawGeoTiff(enabled: Boolean)

    /**
     * One-time migration from the legacy DataStore language key to AppCompat locale storage.
     * Returns the stored language and removes the key so AppCompat remains the source of truth.
     */
    suspend fun consumeLegacyAppLanguage(): AppLanguage?
}

@Singleton
class DefaultUserPreferencesRepository
@Inject
constructor(@ApplicationContext context: Context) :
    UserPreferencesRepository {
    private val dataStore = context.userPreferencesDataStore

    override val wifiOnlyDownloads: Flow<Boolean> =
        dataStore.data.map { prefs ->
            prefs[WIFI_ONLY_KEY] ?: false
        }

    override val demCacheLimitBytes: Flow<Long> =
        dataStore.data.map { prefs ->
            prefs[DEM_CACHE_LIMIT_KEY] ?: AppConfig.DEM_CACHE_DEFAULT_LIMIT_BYTES
        }

    override val showRawSensorOverlay: Flow<Boolean> =
        dataStore.data.map { prefs ->
            prefs[SHOW_RAW_SENSOR_KEY] ?: false
        }

    override val keepRawGeoTiff: Flow<Boolean> =
        dataStore.data.map { prefs ->
            prefs[KEEP_RAW_GEOTIFF_KEY] ?: false
        }

    override suspend fun setWifiOnlyDownloads(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[WIFI_ONLY_KEY] = enabled
        }
    }

    override suspend fun setDemCacheLimitBytes(bytes: Long) {
        dataStore.edit { prefs ->
            prefs[DEM_CACHE_LIMIT_KEY] = bytes
        }
    }

    override suspend fun setShowRawSensorOverlay(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[SHOW_RAW_SENSOR_KEY] = enabled
        }
    }

    override suspend fun setKeepRawGeoTiff(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEEP_RAW_GEOTIFF_KEY] = enabled
        }
    }

    override suspend fun consumeLegacyAppLanguage(): AppLanguage? {
        var legacy: AppLanguage? = null
        dataStore.edit { prefs ->
            val stored = prefs.remove(APP_LANGUAGE_KEY)
            legacy = stored?.let { AppLanguage.fromStorageValue(it) }
        }
        return legacy
    }

    companion object {
        private val WIFI_ONLY_KEY = booleanPreferencesKey("wifi_only_downloads")
        private val DEM_CACHE_LIMIT_KEY = longPreferencesKey("dem_cache_limit_bytes")
        private val SHOW_RAW_SENSOR_KEY = booleanPreferencesKey("show_raw_sensor_overlay")
        private val KEEP_RAW_GEOTIFF_KEY = booleanPreferencesKey("keep_raw_geotiff")
        private val APP_LANGUAGE_KEY = stringPreferencesKey("app_language")
    }
}
