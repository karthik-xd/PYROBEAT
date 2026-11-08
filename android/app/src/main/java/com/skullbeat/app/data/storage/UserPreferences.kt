package com.skullbeat.app.data.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "skullbeat_prefs")

enum class RepeatState {
    OFF, ALL, ONE
}

data class PlayerSettings(
    val lastTrackId: String?,
    val lastPositionMs: Long,
    val shuffle: Boolean,
    val repeatMode: RepeatState,
    val volume: Float
)

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val LAST_TRACK_ID = stringPreferencesKey("last_track_id")
    private val LAST_POSITION_MS = longPreferencesKey("last_position_ms")
    private val SHUFFLE_ENABLED = booleanPreferencesKey("shuffle_enabled")
    private val REPEAT_MODE = intPreferencesKey("repeat_mode")
    private val VOLUME = floatPreferencesKey("volume")

    val settingsFlow: Flow<PlayerSettings> = context.dataStore.data.map { prefs ->
        val repeatInt = prefs[REPEAT_MODE] ?: RepeatState.OFF.ordinal
        val repeatMode = RepeatState.values().getOrElse(repeatInt) { RepeatState.OFF }
        PlayerSettings(
            lastTrackId = prefs[LAST_TRACK_ID],
            lastPositionMs = prefs[LAST_POSITION_MS] ?: 0L,
            shuffle = prefs[SHUFFLE_ENABLED] ?: false,
            repeatMode = repeatMode,
            volume = prefs[VOLUME] ?: 1.0f
        )
    }

    suspend fun savePlaybackState(trackId: String?, positionMs: Long) {
        context.dataStore.edit { prefs ->
            if (trackId != null) {
                prefs[LAST_TRACK_ID] = trackId
            }
            prefs[LAST_POSITION_MS] = positionMs
        }
    }

    suspend fun setShuffle(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SHUFFLE_ENABLED] = enabled
        }
    }

    suspend fun setRepeatMode(repeatState: RepeatState) {
        context.dataStore.edit { prefs ->
            prefs[REPEAT_MODE] = repeatState.ordinal
        }
    }

    suspend fun setVolume(volume: Float) {
        context.dataStore.edit { prefs ->
            prefs[VOLUME] = volume
        }
    }
}
