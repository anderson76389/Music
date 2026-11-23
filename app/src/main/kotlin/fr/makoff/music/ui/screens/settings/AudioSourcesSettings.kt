package fr.makoff.music.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import fr.makoff.music.LocalPlayerAwareWindowInsets
import fr.makoff.music.R
import fr.makoff.music.playback.addons.AudioAddon
import fr.makoff.music.playback.addons.AudioAddons
import fr.makoff.music.playback.SoundProofRepository
import fr.makoff.music.constants.SoundProofSkipAiKey
import fr.makoff.music.constants.SoundProofRefreshGenerationKey
import fr.makoff.music.utils.rememberPreference
import fr.makoff.music.utils.safeDataStoreEdit
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSourcesSettings(navController: NavController) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val sources by remember(context) { AudioAddons.sources(context) }.collectAsStateWithLifecycle(emptyList())
    var adding by remember { mutableStateOf(false) }
    var savingError by remember { mutableStateOf(false) }
    // This is the single SoundProof switch in the app. MusicService observes
    // this preference and the refresh generation and applies it to playback.
    val (soundProofSkipAi, onSoundProofSkipAiChange) = rememberPreference(SoundProofSkipAiKey, false)
    val soundProofRepository = remember(context) { SoundProofRepository(context) }
    var refreshingSoundProof by remember { mutableStateOf(false) }
    var refreshedSoundProof by remember { mutableStateOf<Boolean?>(null) }
    fun refreshSoundProof() {
        if (refreshingSoundProof || !soundProofSkipAi) return
        refreshingSoundProof = true
        scope.launch {
            val refreshed = runCatching { soundProofRepository.refreshNow() }.getOrDefault(false)
            refreshedSoundProof = if (refreshed) {
                context.safeDataStoreEdit { preferences ->
                    val last = preferences[SoundProofRefreshGenerationKey] ?: 0L
                    preferences[SoundProofRefreshGenerationKey] =
                        maxOf(System.currentTimeMillis(), last + 1L)
                }
            } else false
            refreshingSoundProof = false
        }
    }


    fun update(transform: (List<AudioAddon>) -> List<AudioAddon>) {
        scope.launch { savingError = !AudioAddons.update(context, transform) }
    }

    if (adding) {
        AddAudioSourceDialog(
            sources = sources,
            onDismiss = { adding = false },
            onAdd = { addon ->
                if (AudioAddons.update(context) { current ->
                        if (current.size < AudioAddons.MAX_SOURCES && current.none { it.manifest == addon.manifest }) {
                            current + addon
                        } else current
                    }
                ) {
                    savingError = false
                    adding = false
                } else savingError = true
            },
        )
    }

    // Keep the toolbar and scrollable controls in separate layout regions:
    // without a Scaffold the header can intercept taps on source settings.
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
    TopAppBar(
        title = { Text(stringResource(R.string.audio_sources_title)) },
        navigationIcon = {
            SourceAction(R.drawable.arrow_back, R.string.back) { navController.navigateUp() }
        },
    )
        },
    ) { innerPadding ->
    LazyColumn(
        Modifier.padding(innerPadding).windowInsetsPadding(
            LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        ),
    ) {
        // SoundProof is a playback filtering addon, not an audio stream provider.
        item(key = "soundproof_title") {
            Text(
                stringResource(R.string.soundproof_settings_section),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
            )
        }
        item(key = "soundproof_toggle") {
            ListItem(
                headlineContent = { Text(stringResource(R.string.soundproof_skip_ai_title)) },
                supportingContent = { Text(stringResource(R.string.soundproof_skip_ai_description)) },
                leadingContent = { Icon(painterResource(R.drawable.person), contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = soundProofSkipAi,
                        onCheckedChange = onSoundProofSkipAiChange,
                        modifier = Modifier.semantics {
                            contentDescription = context.getString(R.string.soundproof_skip_ai_title)
                        },
                    )
                },
            )
        }
        item(key = "soundproof_refresh") {
            ListItem(
                headlineContent = { Text(stringResource(R.string.soundproof_refresh_title)) },
                supportingContent = {
                    Text(when {
                        refreshingSoundProof -> stringResource(R.string.soundproof_refresh_running)
                        refreshedSoundProof == true -> stringResource(R.string.soundproof_refresh_success)
                        refreshedSoundProof == false -> stringResource(R.string.soundproof_refresh_error)
                        else -> stringResource(R.string.soundproof_refresh_description)
                    })
                },
                leadingContent = { Icon(painterResource(R.drawable.sync), contentDescription = null) },
                trailingContent = {
                    SourceAction(
                        R.drawable.refresh,
                        R.string.soundproof_refresh_title,
                        enabled = soundProofSkipAi && !refreshingSoundProof,
                    ) { refreshSoundProof() }
                },
            )
        }
        item(key = "soundproof_source") {
            ListItem(
                headlineContent = { Text(stringResource(R.string.soundproof_source_title)) },
                supportingContent = { Text(stringResource(R.string.soundproof_source_description)) },
                leadingContent = { Icon(painterResource(R.drawable.info), contentDescription = null) },
                trailingContent = {
                    SourceAction(R.drawable.link, R.string.soundproof_source_title) {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/NuclearBlox/SoundProof-Dumps"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                },
            )
        }
        item(key = "audio_addons_title") {
            Text(
                stringResource(R.string.audio_sources_list_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 20.dp),
            )
        }
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.audio_sources_order), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.audio_sources_manifest_hint), style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = { adding = true },
                    enabled = sources.size < AudioAddons.MAX_SOURCES,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(stringResource(R.string.audio_sources_add))
                }
                if (savingError) {
                    Text(stringResource(R.string.audio_sources_save_error), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        itemsIndexed(sources, key = { _, source -> source.manifest }) { index, source ->
            ListItem(
                headlineContent = { Text(source.name) },
                supportingContent = {
                    Row {
                        SourceAction(R.drawable.arrow_upward, R.string.audio_sources_move_up, index > 0) {
                            update { it.move(source, -1) }
                        }
                        SourceAction(R.drawable.arrow_downward, R.string.audio_sources_move_down, index < sources.lastIndex) {
                            update { it.move(source, 1) }
                        }
                        if (source.manifest != AudioAddons.AUDIUS) SourceAction(R.drawable.delete, R.string.audio_sources_remove) {
                            update { current -> current.filterNot { it.manifest == source.manifest } }
                        }
                    }
                },
                trailingContent = {
                    Switch(
                        checked = source.enabled,
                        onCheckedChange = { enabled ->
                            update { current ->
                                current.map { if (it.manifest == source.manifest) it.copy(enabled = enabled) else it }
                            }
                        },
                        modifier = Modifier.semantics { contentDescription = source.name },
                    )
                },
            )
        }
        item {
            ListItem(
                headlineContent = { Text("YouTube Music") },
                supportingContent = { Text(stringResource(R.string.audio_sources_fallback)) },
            )
        }
    }
    }

}

@Composable
private fun SourceAction(icon: Int, label: Int, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(enabled = enabled, onClick = onClick) {
        Icon(painterResource(icon), contentDescription = stringResource(label))
    }
}

@Composable
private fun AddAudioSourceDialog(
    sources: List<AudioAddon>,
    onDismiss: () -> Unit,
    onAdd: suspend (AudioAddon) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<AudioAddons.TestResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    val tested = result?.source
    val duplicate = tested != null && sources.any { it.manifest == tested.manifest }
    val statusText = when {
        busy -> stringResource(R.string.audio_sources_testing)
        duplicate -> stringResource(R.string.audio_sources_duplicate)
        else -> when (result?.status) {
            AudioAddons.TestStatus.READY -> stringResource(R.string.audio_sources_ready, tested?.name.orEmpty())
            AudioAddons.TestStatus.INVALID -> stringResource(R.string.audio_sources_invalid)
            AudioAddons.TestStatus.RESTRICTED -> stringResource(R.string.audio_sources_restricted)
            AudioAddons.TestStatus.UNAVAILABLE -> stringResource(R.string.audio_sources_unavailable)
            null -> null
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.audio_sources_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it.take(4096)
                        result = null
                    },
                    label = { Text(stringResource(R.string.audio_sources_url)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true,
                    enabled = !busy,
                )
                statusText?.let { Text(it) }
            }
        },
        confirmButton = {
            if (tested != null && !duplicate) {
                TextButton(
                    enabled = !busy && sources.size < AudioAddons.MAX_SOURCES,
                    onClick = {
                        busy = true
                        scope.launch {
                            onAdd(tested)
                            busy = false
                        }
                    },
                ) { Text(stringResource(R.string.save)) }
            } else {
                TextButton(
                    enabled = url.isNotBlank() && !busy && !duplicate,
                    onClick = {
                        busy = true
                        scope.launch {
                            result = AudioAddons.test(url)
                            busy = false
                        }
                    },
                ) { Text(stringResource(R.string.audio_sources_test)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

private fun List<AudioAddon>.move(source: AudioAddon, direction: Int): List<AudioAddon> {
    val index = indexOfFirst { it.manifest == source.manifest }
    if (index < 0 || index + direction !in indices) return this
    return toMutableList().apply { add(index + direction, removeAt(index)) }
}
