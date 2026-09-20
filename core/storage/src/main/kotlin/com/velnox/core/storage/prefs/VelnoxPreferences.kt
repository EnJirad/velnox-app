package com.velnox.core.storage.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Single app-wide DataStore instance. */
private val Context.velnoxDataStore: DataStore<Preferences> by preferencesDataStore(name = "velnox_prefs")

/**
 * Non-sensitive, device-local preferences.
 *
 * Deliberately holds nothing that is commerce state: per `docs/ai/ARCHITECTURE.md`
 * the source of truth for anything financial or catalogue-related is Neon, reached
 * through the API. Cached catalogue rows live in `core:database` where they can be
 * invalidated and dated; UI preferences live here.
 *
 * The language key exists because the Velnox web clients default to Thai while the
 * device locale may be neither Thai nor English, and the user's explicit choice must
 * outlive the process.
 */
@Singleton
class VelnoxPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val store: DataStore<Preferences> get() = context.velnoxDataStore

    val languageTag: Flow<String> = store.data.map { it[KEY_LANGUAGE] ?: DEFAULT_LANGUAGE }

    val hasSeenWelcome: Flow<Boolean> = store.data.map { it[KEY_HAS_SEEN_WELCOME] ?: false }

    /**
     * Epoch millis of the last successful catalogue sync.
     * Used to label cached content as cached — never to authorise showing it as fresh.
     */
    val lastCatalogueSyncAt: Flow<Long> = store.data.map { it[KEY_LAST_CATALOGUE_SYNC] ?: 0L }

    suspend fun setLanguageTag(tag: String) {
        store.edit { it[KEY_LANGUAGE] = tag }
    }

    suspend fun setHasSeenWelcome(seen: Boolean) {
        store.edit { it[KEY_HAS_SEEN_WELCOME] = seen }
    }

    suspend fun markCatalogueSynced(atEpochMillis: Long) {
        store.edit { it[KEY_LAST_CATALOGUE_SYNC] = atEpochMillis }
    }

    /** Developer-facing diagnostics for the About screen; contains no secrets. */
    val installedApiBaseUrl: String get() = com.velnox.core.storage.BuildConfig.VELNOX_API_BASE_URL

    private companion object {
        val KEY_LANGUAGE = stringPreferencesKey("language_tag")
        val KEY_HAS_SEEN_WELCOME = booleanPreferencesKey("has_seen_welcome")
        val KEY_LAST_CATALOGUE_SYNC = longPreferencesKey("last_catalogue_sync_at")

        /** Velnox default UI language, matching the web clients. */
        const val DEFAULT_LANGUAGE = "th"
    }
}
