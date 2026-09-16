package app.sourcescribe.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import app.sourcescribe.core.AppSettings
import app.sourcescribe.core.JobConfig
import app.sourcescribe.core.JobLimits
import app.sourcescribe.core.KeytermSets
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * One DataStore per settings file in this process, which DataStore requires. The `preferencesDataStore` delegate this
 * replaces kept a single one for the whole process, on the files of whichever context used it first, so a store made
 * for a context with files of its own read and wrote those instead: instrumentation tests changed the app's settings.
 */
private val stores = HashMap<String, DataStore<Preferences>>()

private fun settingsDataStore(context: Context): DataStore<Preferences> {
    val file = context.applicationContext.preferencesDataStoreFile("settings")
    return synchronized(stores) {
        stores.getOrPut(file.canonicalPath) { PreferenceDataStoreFactory.create(produceFile = { file }) }
    }
}

/** Settings contain credential references only; corruption is surfaced instead of silently resetting policy. */
@Singleton
class SettingsStore @Inject constructor(@ApplicationContext context: Context) {
    private val store = settingsDataStore(context)
    private val key = stringPreferencesKey("settings_v1")
    private val json = Json { encodeDefaults = true }
    val settings: Flow<AppSettings> = store.data.map { preferences ->
        preferences[key]?.let { json.decodeFromString<AppSettings>(it).also(::validate) } ?: AppSettings()
    }

    suspend fun update(change: (AppSettings) -> AppSettings) {
        store.edit { preferences ->
            val current = preferences[key]?.let { json.decodeFromString<AppSettings>(it) } ?: AppSettings()
            val updated = change(current).also(::validate)
            preferences[key] = json.encodeToString(updated)
        }
    }

    private fun validate(settings: AppSettings) {
        require(settings.parallelJobs in 1..4)
        require(settings.storageLimitBytes in 256L * 1024 * 1024..32L * 1024 * 1024 * 1024)
        require(settings.theme in setOf("SYSTEM", "LIGHT", "DARK"))
        require(settings.presets.size <= 30)
        settings.presets.forEach { (name, config) ->
            require(name.isNotBlank() && name.length <= 80 && name.none(Char::isISOControl))
            validateConfig(config)
        }
        validateConfig(settings.defaults)
        // The same bounds a job's own terms live under, so a saved set can always be loaded into a draft
        // this file will then still accept. `KeytermSets.saved` is what produces them.
        require(settings.keytermSets.size <= KeytermSets.MAX_SETS)
        settings.keytermSets.forEach { (name, terms) ->
            require(KeytermSets.saved(emptyMap(), name, terms) == mapOf(name to terms))
        }
    }

    private fun validateConfig(config: JobConfig) {
        require(config.maxAudioSeconds in 1..JobLimits.MAX_AUDIO_SECONDS)
        require(config.maxCostMicrousd.let { it == null || it >= 0 })
        require(config.preferredLanguages.size <= 20 && config.preferredLanguages.all { it.matches(Regex("[A-Za-z0-9-]{1,35}")) })
        require(config.contextTerms.size <= 1000 && config.contextTerms.all { it.length <= 500 && it.none(Char::isISOControl) })
        require(config.exportTreeUri.let { it == null || it.startsWith("content://") })
        require(config.exportTreeUris.values.all { it.startsWith("content://") })
        require(config.exportFormats.isNotEmpty())
    }
}
