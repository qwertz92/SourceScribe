package app.sourcescribe.ui

import android.content.Intent
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import app.sourcescribe.MainViewModel
import app.sourcescribe.R
import app.sourcescribe.ScreenState
import app.sourcescribe.core.AppSettings
import app.sourcescribe.core.JobConfig
import app.sourcescribe.core.Provider
import app.sourcescribe.core.Region
import app.sourcescribe.data.CredentialInfo
import app.sourcescribe.data.JobRow
import app.sourcescribe.data.decodeStoredJobConfig
import app.sourcescribe.extractor.EngineChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun SettingsScreen(
    settings: AppSettings,
    config: JobConfig,
    state: ScreenState,
    jobs: List<JobRow>,
    change: (JobConfig) -> Unit,
    model: MainViewModel,
    openHelp: (HelpTopic) -> Unit,
) {
    var provider by rememberSaveable { mutableStateOf(Provider.ASSEMBLYAI) }
    var region by rememberSaveable { mutableStateOf(Region.US) }
    var key by remember { mutableStateOf("") }
    var replacingId by rememberSaveable { mutableStateOf<String?>(null) }
    var restoreJob by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(restoreJob, state.credentials) {
        if (restoreJob != null && state.credentials.any { it.id == replacingId }) restoreJob = null
    }
    val missingKeys = remember(jobs, state.credentials) {
        jobs.filterNot { it.deleteRequested }.mapNotNull { job ->
            val saved = decodeStoredJobConfig(job.config) ?: return@mapNotNull null
            val id = saved.credentialId
            val selectedProvider = saved.provider
            if (id == null || selectedProvider == null || state.credentials.any { it.id == id }) null
            else job.id to CredentialInfo(id, selectedProvider, saved.region)
        }.distinctBy { it.second.id }
    }
    var presetName by rememberSaveable { mutableStateOf("") }
    var probe by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val appLanguage = LocalConfiguration.current.locales[0].language.let { if (it == "en") "en" else "de" }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                change(config.copy(exportTreeUri = uri.toString()))
                model.changeSettings { it.copy(defaults = it.defaults.copy(exportTreeUri = uri.toString())) }
            } catch (_: SecurityException) { model.exportPermissionError() }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Choice(stringResource(R.string.app_language), languageName(appLanguage), listOf("de", "en"),
                    { languageName(it) }, enabled = !state.busy) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(it))
                }
                Choice(stringResource(R.string.appearance), themeName(settings.theme), listOf("SYSTEM", "LIGHT", "DARK"),
                    { themeName(it) }) { value -> model.changeSettings { it.copy(theme = value) } }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.privacy), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                InfoButton(HelpTopic.PRIVACY, openHelp)
            }
            SectionTitle(R.string.credentials, HelpTopic.API_KEY, openHelp)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Choice(stringResource(R.string.provider), providerName(provider), Provider.entries, { providerName(it) },
                    enabled = !state.busy && replacingId == null, info = HelpTopic.PROVIDERS, openHelp = openHelp) {
                    provider = it; key = ""
                }
                if (provider == Provider.ASSEMBLYAI) Choice(stringResource(R.string.region), regionName(region),
                    Region.entries, { regionName(it) }, enabled = !state.busy && replacingId == null,
                    supporting = stringResource(R.string.key_region_help), info = HelpTopic.REGION, openHelp = openHelp) {
                    region = it; key = ""
                }
                OutlinedTextField(key, { key = it.take(4096) }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.api_key)) },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                if (replacingId != null) {
                    Text(stringResource(if (restoreJob == null) R.string.replace_key_help else R.string.restore_key_help,
                        providerName(provider), regionName(region)))
                    TextButton({ replacingId = null; restoreJob = null; key = "" }, enabled = !state.busy) {
                        Text(stringResource(R.string.cancel))
                    }
                }
                Button({
                    val replacing = replacingId
                    if (restoreJob != null) model.restoreCredential(requireNotNull(restoreJob), key)
                    else if (replacing == null) model.saveCredential(provider, if (provider == Provider.ASSEMBLYAI) region else Region.US, key)
                    else model.replaceCredential(replacing, provider, region, key)
                    key = ""
                }, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = key.isNotBlank() && !state.busy) {
                    Text(stringResource(if (replacingId == null) R.string.add_key else R.string.save_key))
                }
            }
        }
        if (state.credentials.isEmpty()) item {
            Text(stringResource(R.string.no_credentials), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(state.credentials, key = { it.id }) { credential ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.credential_entry, providerName(credential.provider), regionName(credential.region)),
                        style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.credential_key, credential.id.takeLast(4)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton({
                            replacingId = credential.id; restoreJob = null
                            provider = credential.provider; region = credential.region; key = ""
                        }, enabled = !state.busy) { Text(stringResource(R.string.replace_key)) }
                        TextButton({ model.deleteCredential(credential.id) }, enabled = !state.busy) {
                            Text(stringResource(R.string.delete_key))
                        }
                    }
                }
            }
        }
        items(missingKeys, key = { "missing-${it.second.id}" }) { (jobId, credential) ->
            TextButton({
                replacingId = credential.id; restoreJob = jobId
                provider = credential.provider; region = credential.region; key = ""
            }, enabled = !state.busy) {
                Text(stringResource(R.string.restore_key_action, providerName(credential.provider),
                    regionName(credential.region), credential.id.takeLast(4)))
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle(R.string.defaults, HelpTopic.PRESETS, openHelp)
                Text(stringResource(R.string.defaults_help), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button({ model.saveDefaults(config) }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(stringResource(R.string.save_defaults))
                }
                OutlinedTextField(presetName, { presetName = it.take(80) }, Modifier.fillMaxWidth(),
                    singleLine = true, label = { Text(stringResource(R.string.preset_name)) })
                TextButton({ model.savePreset(presetName, config); presetName = "" },
                    Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = presetName.isNotBlank()) {
                    Text(stringResource(R.string.save_preset))
                }
                Text(stringResource(R.string.presets_saved), style = MaterialTheme.typography.titleSmall)
                if (settings.presets.isEmpty()) Text(stringResource(R.string.no_presets),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                settings.presets.keys.sorted().forEach { name ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, Modifier.weight(1f))
                        TextButton({ model.deletePreset(name) }, enabled = !state.busy) {
                            Text(stringResource(R.string.delete_preset))
                        }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(R.string.export_tree_current, HelpTopic.EXPORT_FOLDER, openHelp)
                val tree = config.exportTreeUri
                val label by produceState(tree, tree) {
                    value = tree?.let { uri -> withContext(Dispatchers.IO) { folderLabel(context, uri) } }
                }
                Text(label ?: stringResource(R.string.no_export_tree), style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton({ folder.launch(null) }) {
                        Text(stringResource(if (tree == null) R.string.export_tree else R.string.export_tree_change))
                    }
                    if (tree != null) TextButton({
                        change(config.copy(exportTreeUri = null))
                        model.changeSettings { it.copy(defaults = it.defaults.copy(exportTreeUri = null)) }
                    }) { Text(stringResource(R.string.export_tree_clear)) }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Choice(stringResource(R.string.storage_limit), "${settings.storageLimitBytes / 1024 / 1024} MiB",
                    listOf(256L, 512L, 1024L, 2048L, 4096L, 8192L, 16384L, 32768L), { "$it MiB" },
                    info = HelpTopic.STORAGE, openHelp = openHelp) { limit ->
                    model.changeSettings { it.copy(storageLimitBytes = limit * 1024 * 1024) }
                }
                Choice(stringResource(R.string.parallel_jobs), settings.parallelJobs.toString(), (1..4).toList(),
                    { it.toString() }) { count -> model.changeSettings { it.copy(parallelJobs = count) } }
            }
        }
        item {
            SectionTitle(R.string.diagnostics, HelpTopic.DIAGNOSTICS, openHelp)
            Text(stringResource(R.string.diagnostics_help), style = MaterialTheme.typography.bodySmall)
            TextButton(model::showDiagnostics, enabled = !state.busy) { Text(stringResource(R.string.diagnostics_preview)) }
            TextButton(model::showLicenses, enabled = !state.busy) { Text(stringResource(R.string.licenses)) }
        }
        item {
            SectionTitle(R.string.engines, HelpTopic.ENGINES, openHelp)
            Text(stringResource(R.string.engine_help), style = MaterialTheme.typography.bodySmall)
            state.installations.forEach {
                Text("yt-dlp ${it.version} · EJS ${it.ejsVersion} · ${channelName(it.channel)}", Modifier.padding(vertical = 8.dp))
            }
            Row {
                TextButton({ model.checkEngine(EngineChannel.STABLE) }, enabled = !state.busy) {
                    Text(channelName(EngineChannel.STABLE))
                }
                TextButton({ model.checkEngine(EngineChannel.NIGHTLY) }, enabled = !state.busy) {
                    Text(channelName(EngineChannel.NIGHTLY))
                }
            }
            state.update?.let { update ->
                Text("yt-dlp ${update.version} · ${channelName(update.channel)}")
                OutlinedTextField(probe, { probe = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.engine_probe)) })
                Button({ model.stageAndActivate(probe) }, Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    enabled = probe.isNotBlank() && !state.busy) { Text(stringResource(R.string.engine_activate)) }
            }
            TextButton(model::rollback, enabled = !state.busy) { Text(stringResource(R.string.engine_rollback)) }
        }
    }
}

/** The folder a reader recognises, falling back to the storage identifier when no display name is readable. */
private fun folderLabel(context: android.content.Context, treeUri: String): String? = try {
    val tree = treeUri.toUri()
    val documentId = DocumentsContract.getTreeDocumentId(tree)
    val document = DocumentsContract.buildDocumentUriUsingTree(tree, documentId)
    val name = context.contentResolver.query(document,
        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
    }
    name ?: documentId
} catch (_: SecurityException) {
    null
} catch (_: IllegalArgumentException) {
    null
}
