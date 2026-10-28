/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package fr.makoff.music.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import fr.makoff.music.ui.component.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import fr.makoff.music.downloads.DownloadFormatSettings
import fr.makoff.music.BuildConfig
import fr.makoff.music.LocalPlayerAwareWindowInsets
import fr.makoff.music.R
import fr.makoff.music.constants.AudioNormalizationKey
import fr.makoff.music.constants.AudioOffload
import fr.makoff.music.constants.AudioTrackPlaybackParamsKey
import fr.makoff.music.constants.AudioQuality
import fr.makoff.music.constants.AudioQualityKey
import fr.makoff.music.constants.AutoDownloadOnLikeKey
import fr.makoff.music.constants.CrossfadeDurationKey
import fr.makoff.music.constants.CrossfadeEnabledKey
import fr.makoff.music.constants.CrossfadeGaplessKey
import fr.makoff.music.constants.AutoLoadMoreKey
import fr.makoff.music.constants.AutoRadioQueueKey
import fr.makoff.music.constants.AutoSkipNextOnErrorKey
import fr.makoff.music.constants.AutoplayKey
import fr.makoff.music.constants.DisableLoadMoreWhenRepeatAllKey
import fr.makoff.music.constants.EnableGoogleCastKey
import fr.makoff.music.constants.HistoryDuration
import fr.makoff.music.constants.KeepScreenOn
import fr.makoff.music.constants.LoudnessLevel
import fr.makoff.music.constants.LoudnessLevelKey
import fr.makoff.music.constants.PauseOnMute
import fr.makoff.music.constants.PersistentQueueKey
import fr.makoff.music.constants.PersistentShuffleAcrossQueuesKey
import fr.makoff.music.constants.PreventDuplicateTracksInQueueKey
import fr.makoff.music.constants.RememberShuffleAndRepeatKey
import fr.makoff.music.constants.ResumeOnBluetoothConnectKey
import fr.makoff.music.constants.SeekExtraSeconds
import fr.makoff.music.constants.ShufflePlaylistFirstKey
import fr.makoff.music.constants.SimilarContent
import fr.makoff.music.constants.SkipSilenceInstantKey
import fr.makoff.music.constants.SkipSilenceKey
import fr.makoff.music.constants.StopMusicOnTaskClearKey
import fr.makoff.music.constants.VarispeedKey
import fr.makoff.music.ui.component.DefaultDialog
import fr.makoff.music.ui.component.EnumDialog
import fr.makoff.music.ui.component.IconButton
import fr.makoff.music.ui.component.Material3SettingsGroup
import fr.makoff.music.ui.component.Material3SettingsItem
import fr.makoff.music.ui.utils.backToMain
import fr.makoff.music.utils.rememberEnumPreference
import fr.makoff.music.utils.rememberPreference
import kotlin.math.roundToInt
import fr.makoff.music.ui.utils.getLoudnessLevelLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettings(
    navController: NavController
) {
    val (audioQuality, onAudioQualityChange) = rememberEnumPreference(
        AudioQualityKey,
        defaultValue = AudioQuality.HIGH
    )
    val (crossfadeEnabled, onCrossfadeEnabledChange) = rememberPreference(
        CrossfadeEnabledKey,
        defaultValue = false
    )
    val (crossfadeDuration, onCrossfadeDurationChange) = rememberPreference(
        CrossfadeDurationKey,
        defaultValue = 5f
    )
    val (crossfadeGapless, onCrossfadeGaplessChange) = rememberPreference(
        CrossfadeGaplessKey,
        defaultValue = true
    )
    val (persistentQueue, onPersistentQueueChange) = rememberPreference(
        PersistentQueueKey,
        defaultValue = true
    )
    val (skipSilence, onSkipSilenceChange) = rememberPreference(
        SkipSilenceKey,
        defaultValue = false
    )
    val (skipSilenceInstant, onSkipSilenceInstantChange) = rememberPreference(
        SkipSilenceInstantKey,
        defaultValue = false
    )
    val (audioNormalization, onAudioNormalizationChange) = rememberPreference(
        AudioNormalizationKey,
        defaultValue = true
    )

    val (loudnessLevel, onLoudnessLevelChange) = rememberEnumPreference(
        LoudnessLevelKey,
        defaultValue = LoudnessLevel.BALANCED,
    )

    val (audioOffload, onAudioOffloadChange) = rememberPreference(
        key = AudioOffload,
        defaultValue = false
    )

    val (audioTrackPlaybackParams, onAudioTrackPlaybackParamsChange) = rememberPreference(
        key = AudioTrackPlaybackParamsKey,
        defaultValue = true
    )

    val (varispeed, onVarispeedChange) = rememberPreference(
        key = VarispeedKey,
        defaultValue = false
    )

    val (enableGoogleCast, onEnableGoogleCastChange) = rememberPreference(
        key = EnableGoogleCastKey,
        defaultValue = true
    )

    val (seekExtraSeconds, onSeekExtraSeconds) = rememberPreference(
        SeekExtraSeconds,
        defaultValue = false
    )

    val (autoLoadMore, onAutoLoadMoreChange) = rememberPreference(
        AutoLoadMoreKey,
        defaultValue = true
    )
    val (autoRadioQueue, onAutoRadioQueueChange) = rememberPreference(
        AutoRadioQueueKey,
        defaultValue = true
    )
    val (disableLoadMoreWhenRepeatAll, onDisableLoadMoreWhenRepeatAllChange) = rememberPreference(
        DisableLoadMoreWhenRepeatAllKey,
        defaultValue = false
    )
    val (autoDownloadOnLike, onAutoDownloadOnLikeChange) = rememberPreference(
        AutoDownloadOnLikeKey,
        defaultValue = false
    )
    val (similarContentEnabled, similarContentEnabledChange) = rememberPreference(
        key = SimilarContent,
        defaultValue = true
    )
    val (autoSkipNextOnError, onAutoSkipNextOnErrorChange) = rememberPreference(
        AutoSkipNextOnErrorKey,
        defaultValue = false
    )
    val (autoplay, onAutoplayChange) = rememberPreference(
        AutoplayKey,
        defaultValue = true
    )
    val (persistentShuffleAcrossQueues, onPersistentShuffleAcrossQueuesChange) = rememberPreference(
        PersistentShuffleAcrossQueuesKey,
        defaultValue = false
    )
    val (rememberShuffleAndRepeat, onRememberShuffleAndRepeatChange) = rememberPreference(
        RememberShuffleAndRepeatKey,
        defaultValue = true
    )
    val (shufflePlaylistFirst, onShufflePlaylistFirstChange) = rememberPreference(
        ShufflePlaylistFirstKey,
        defaultValue = false
    )
    val (preventDuplicateTracksInQueue, onPreventDuplicateTracksInQueueChange) = rememberPreference(
        PreventDuplicateTracksInQueueKey,
        defaultValue = false
    )
    val (stopMusicOnTaskClear, onStopMusicOnTaskClearChange) = rememberPreference(
        StopMusicOnTaskClearKey,
        defaultValue = false
    )
    val (pauseOnMute, onPauseOnMuteChange) = rememberPreference(
        PauseOnMute,
        defaultValue = false
    )
    val (resumeOnBluetoothConnect, onResumeOnBluetoothConnectChange) = rememberPreference(
        ResumeOnBluetoothConnectKey,
        defaultValue = false
    )
    val (keepScreenOn, onKeepScreenOnChange) = rememberPreference(
        KeepScreenOn,
        defaultValue = false
    )
    val (historyDuration, onHistoryDurationChange) = rememberPreference(
        HistoryDuration,
        defaultValue = 30f
    )

    var showAudioQualityDialog by remember {
        mutableStateOf(false)
    }

    var showLoudnessLevelDialog by remember {
        mutableStateOf(false)
    }

    if (showAudioQualityDialog) {
        EnumDialog(
            onDismiss = { showAudioQualityDialog = false },
            onSelect = {
                onAudioQualityChange(it)
                showAudioQualityDialog = false
            },
            title = stringResource(R.string.audio_quality),
            current = audioQuality,
            values = AudioQuality.values().toList(),
            valueText = {
                when (it) {
                    AudioQuality.AUTO -> stringResource(R.string.audio_quality_auto)
                    AudioQuality.HIGH -> stringResource(R.string.audio_quality_high)
                    AudioQuality.LOW -> stringResource(R.string.audio_quality_low)
                }
            }
        )
    }

    if (showLoudnessLevelDialog) {
        EnumDialog(
            onDismiss = { showLoudnessLevelDialog = false },
            onSelect = {
                onLoudnessLevelChange(it)
                showLoudnessLevelDialog = false
            },
            title = stringResource(R.string.loudness_level),
            current = loudnessLevel,
            values = LoudnessLevel.values().toList(),
            valueText = { getLoudnessLevelLabel(it) }
        )
    }

    Column(
        Modifier
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top
                )
            )
        )

        Material3SettingsGroup(
            title = stringResource(R.string.player_audio_quality_section),
            items = buildList {
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.graphic_eq),
                        title = { Text(stringResource(R.string.audio_quality)) },
                        description = {
                            Text(
                                when (audioQuality) {
                                    AudioQuality.AUTO -> stringResource(R.string.audio_quality_auto)
                                    AudioQuality.HIGH -> stringResource(R.string.playback_quality_high_help)
                                    AudioQuality.LOW -> stringResource(R.string.audio_quality_low)
                                }
                            )
                        },
                        onClick = { showAudioQualityDialog = true }
                    ),
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.volume_up),
                        title = { Text(stringResource(R.string.audio_normalization)) },
                        trailingContent = {
                            Switch(
                                checked = audioNormalization,
                                onCheckedChange = onAudioNormalizationChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (audioNormalization) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { onAudioNormalizationChange(!audioNormalization) }
                    ),
                )
                if (audioNormalization) {
                    add(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.volume_up),
                            title = { Text(stringResource(R.string.loudness_level)) },
                            description = {
                                Text(getLoudnessLevelLabel(loudnessLevel))
                            },
                            onClick = { showLoudnessLevelDialog = true }
                        ),
                    )
                }
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.graphic_eq),
                        title = { Text(stringResource(R.string.varispeed)) },
                        description = {
                            Text(
                                stringResource(R.string.varispeed_description)
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = varispeed,
                                onCheckedChange = onVarispeedChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (varispeed) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { onVarispeedChange(!varispeed) }
                    ),
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.speed),
                        title = { Text(stringResource(R.string.audio_track_playback_params)) },
                        description = {
                            Text(stringResource(R.string.audio_track_playback_params_description))
                        },
                        trailingContent = {
                            Switch(
                                checked = audioTrackPlaybackParams,
                                onCheckedChange = onAudioTrackPlaybackParamsChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (audioTrackPlaybackParams) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { onAudioTrackPlaybackParamsChange(!audioTrackPlaybackParams) }
                    ),
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.graphic_eq),
                        title = { Text(stringResource(R.string.audio_offload)) },
                        description = {
                            Text(
                                if (crossfadeEnabled) stringResource(R.string.audio_offload_disabled_by_crossfade)
                                else stringResource(R.string.audio_offload_description)
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = if (crossfadeEnabled) false else audioOffload,
                                onCheckedChange = onAudioOffloadChange,
                                enabled = !crossfadeEnabled,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (!crossfadeEnabled && audioOffload) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { if (!crossfadeEnabled) onAudioOffloadChange(!audioOffload) }
                    ),
                )
            },
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.player_transitions_section),
            items = buildList {
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.linear_scale),
                        title = { Text(stringResource(R.string.crossfade)) },
                        description = { Text(stringResource(R.string.crossfade_desc)) },
                        trailingContent = {
                            Switch(
                                checked = crossfadeEnabled,
                                onCheckedChange = onCrossfadeEnabledChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (crossfadeEnabled) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { onCrossfadeEnabledChange(!crossfadeEnabled) }
                    ),
                )
                if (crossfadeEnabled) {
                    add(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.timer),
                            title = { Text(stringResource(R.string.crossfade_duration)) },
                            description = {
                                Column {
                                    Text(pluralStringResource(R.plurals.seconds, crossfadeDuration.toInt(), crossfadeDuration.toInt()))
                                    Slider(
                                        value = crossfadeDuration,
                                        onValueChange = onCrossfadeDurationChange,
                                        valueRange = 1f..15f,
                                        steps = 13
                                    )
                                }
                            }
                        ),
                    )
                }
                if (crossfadeEnabled) {
                    add(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.album),
                            title = { Text(stringResource(R.string.crossfade_gapless)) },
                            description = { Text(stringResource(R.string.crossfade_gapless_desc)) },
                            trailingContent = {
                                Switch(
                                    checked = crossfadeGapless,
                                    onCheckedChange = onCrossfadeGaplessChange,
                                    thumbContent = {
                                        Icon(
                                            painter = painterResource(
                                                id = if (crossfadeGapless) R.drawable.check else R.drawable.close
                                            ),
                                            contentDescription = null,
                                            modifier = Modifier.size(SwitchDefaults.IconSize)
                                        )
                                    }
                                )
                            },
                            onClick = { onCrossfadeGaplessChange(!crossfadeGapless) }
                        ),
                    )
                }
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.fast_forward),
                        title = { Text(stringResource(R.string.skip_silence)) },
                        description = { Text(stringResource(R.string.skip_silence_desc)) },
                        trailingContent = {
                            Switch(
                                checked = skipSilence,
                                onCheckedChange = onSkipSilenceChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (skipSilence) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { onSkipSilenceChange(!skipSilence) }
                    ),
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.skip_next),
                        title = { Text(stringResource(R.string.skip_silence_instant)) },
                        description = { Text(stringResource(R.string.skip_silence_instant_desc)) },
                        trailingContent = {
                            Switch(
                                checked = skipSilenceInstant,
                                onCheckedChange = { onSkipSilenceInstantChange(it) },
                                enabled = skipSilence,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (skipSilenceInstant) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { if (skipSilence) onSkipSilenceInstantChange(!skipSilenceInstant) }
                    ),
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.arrow_forward),
                        title = { Text(stringResource(R.string.seek_seconds_addup)) },
                        description = { Text(stringResource(R.string.seek_seconds_addup_description)) },
                        trailingContent = {
                            Switch(
                                checked = seekExtraSeconds,
                                onCheckedChange = onSeekExtraSeconds,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (seekExtraSeconds) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { onSeekExtraSeconds(!seekExtraSeconds) }
                    ),
                )
            },
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.player_queue_section),
            items = buildList {
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.queue_music),
                        title = { Text(stringResource(R.string.persistent_queue)) },
                        description = { Text(stringResource(R.string.persistent_queue_desc)) },
                        trailingContent = {
                            Switch(
                                checked = persistentQueue,
                                onCheckedChange = onPersistentQueueChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (persistentQueue) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { onPersistentQueueChange(!persistentQueue) }
                    ),
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.queue_music),
                        title = { Text(stringResource(R.string.prevent_duplicate_tracks_in_queue)) },
                        description = { Text(stringResource(R.string.prevent_duplicate_tracks_in_queue_desc)) },
                        trailingContent = {
                            Switch(
                                checked = preventDuplicateTracksInQueue,
                                onCheckedChange = onPreventDuplicateTracksInQueueChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (preventDuplicateTracksInQueue) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { onPreventDuplicateTracksInQueueChange(!preventDuplicateTracksInQueue) }
                    ),
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.playlist_add),
                        title = { Text(stringResource(R.string.auto_load_more)) },
                        description = { Text(stringResource(R.string.auto_load_more_desc)) },
                        trailingContent = {
                            Switch(
                                checked = autoLoadMore,
                                onCheckedChange = onAutoLoadMoreChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (autoLoadMore) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { onAutoLoadMoreChange(!autoLoadMore) }
                    ),
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.skip_next),
                        title = { Text(stringResource(R.string.autoplay)) },
                        description = { Text(stringResource(R.string.autoplay_desc)) },
                        trailingContent = {
                            Switch(
                                checked = autoplay,
                                onCheckedChange = onAutoplayChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (autoplay) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { onAutoplayChange(!autoplay) }
                    ),
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.radio),
                        title = { Text(stringResource(R.string.auto_radio_queue)) },
                        description = { Text(stringResource(R.string.auto_radio_queue_desc)) },
                        trailingContent = {
                            Switch(
                                checked = autoRadioQueue,
                                onCheckedChange = onAutoRadioQueueChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (autoRadioQueue) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { onAutoRadioQueueChange(!autoRadioQueue) }
                    ),
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.repeat),
                        title = { Text(stringResource(R.string.disable_load_more_when_repeat_all)) },
                        description = { Text(stringResource(R.string.disable_load_more_when_repeat_all_desc)) },
                        trailingContent = {
                            Switch(
                                checked = disableLoadMoreWhenRepeatAll,
                                onCheckedChange = onDisableLoadMoreWhenRepeatAllChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (disableLoadMoreWhenRepeatAll) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { onDisableLoadMoreWhenRepeatAllChange(!disableLoadMoreWhenRepeatAll) }
                    ),
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.similar),
                        title = { Text(stringResource(R.string.enable_similar_content)) },
                        description = { Text(stringResource(R.string.similar_content_desc)) },
                        trailingContent = {
                            Switch(
                                checked = similarContentEnabled,
                                onCheckedChange = similarContentEnabledChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (similarContentEnabled) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { similarContentEnabledChange(!similarContentEnabled) }
                    ),
                )
            },
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.player_shuffle_section),
            items = buildList {
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.shuffle),
                        title = { Text(stringResource(R.string.shuffle_playlist_first)) },
                        description = { Text(stringResource(R.string.shuffle_playlist_first_desc)) },
                        trailingContent = {
                            Switch(
                                checked = shufflePlaylistFirst,
                                onCheckedChange = onShufflePlaylistFirstChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (shufflePlaylistFirst) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { onShufflePlaylistFirstChange(!shufflePlaylistFirst) }
                    ),
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.shuffle),
                        title = { Text(stringResource(R.string.persistent_shuffle_title)) },
                        description = { Text(stringResource(R.string.persistent_shuffle_desc)) },
                        trailingContent = {
                            Switch(
                                checked = persistentShuffleAcrossQueues,
                                onCheckedChange = onPersistentShuffleAcrossQueuesChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (persistentShuffleAcrossQueues) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { onPersistentShuffleAcrossQueuesChange(!persistentShuffleAcrossQueues) }
                    ),
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.shuffle),
                        title = { Text(stringResource(R.string.remember_shuffle_and_repeat)) },
                        description = { Text(stringResource(R.string.remember_shuffle_and_repeat_desc)) },
                        trailingContent = {
                            Switch(
                                checked = rememberShuffleAndRepeat,
                                onCheckedChange = onRememberShuffleAndRepeatChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (rememberShuffleAndRepeat) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { onRememberShuffleAndRepeatChange(!rememberShuffleAndRepeat) }
                    ),
                )
            },
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.player_behavior_section),
            items = buildList {
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.skip_next),
                        title = { Text(stringResource(R.string.auto_skip_next_on_error)) },
                        description = { Text(stringResource(R.string.auto_skip_next_on_error_desc)) },
                        trailingContent = {
                            Switch(
                                checked = autoSkipNextOnError,
                                onCheckedChange = onAutoSkipNextOnErrorChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (autoSkipNextOnError) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { onAutoSkipNextOnErrorChange(!autoSkipNextOnError) }
                    ),
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.volume_off_pause),
                        title = { Text(stringResource(R.string.pause_music_when_media_is_muted)) },
                        trailingContent = {
                            Switch(
                                checked = pauseOnMute,
                                onCheckedChange = onPauseOnMuteChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (pauseOnMute) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { onPauseOnMuteChange(!pauseOnMute) }
                    ),
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.clear_all),
                        title = { Text(stringResource(R.string.stop_music_on_task_clear)) },
                        trailingContent = {
                            Switch(
                                checked = stopMusicOnTaskClear,
                                onCheckedChange = onStopMusicOnTaskClearChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (stopMusicOnTaskClear) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { onStopMusicOnTaskClearChange(!stopMusicOnTaskClear) }
                    ),
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.screenshot),
                        title = { Text(stringResource(R.string.keep_screen_on_when_player_is_expanded)) },
                        trailingContent = {
                            Switch(
                                checked = keepScreenOn,
                                onCheckedChange = onKeepScreenOnChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (keepScreenOn) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { onKeepScreenOnChange(!keepScreenOn) }
                    ),
                )
            },
        )

        Spacer(modifier = Modifier.height(27.dp))

        Material3SettingsGroup(
            title = stringResource(R.string.player_devices_section),
            items = buildList {
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.bluetooth),
                        title = { Text(stringResource(R.string.resume_on_bluetooth_connect)) },
                        trailingContent = {
                            Switch(
                                checked = resumeOnBluetoothConnect,
                                onCheckedChange = onResumeOnBluetoothConnectChange,
                                thumbContent = {
                                    Icon(
                                        painter = painterResource(
                                            id = if (resumeOnBluetoothConnect) R.drawable.check else R.drawable.close
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            )
                        },
                        onClick = { onResumeOnBluetoothConnectChange(!resumeOnBluetoothConnect) }
                    ),
                )
                if (BuildConfig.CAST_AVAILABLE) {
                    add(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.cast),
                            title = { Text(stringResource(R.string.google_cast)) },
                            description = { Text(stringResource(R.string.google_cast_description)) },
                            trailingContent = {
                                Switch(
                                    checked = enableGoogleCast,
                                    onCheckedChange = onEnableGoogleCastChange,
                                    thumbContent = {
                                        Icon(
                                            painter = painterResource(
                                                id = if (enableGoogleCast) R.drawable.check else R.drawable.close
                                            ),
                                            contentDescription = null,
                                            modifier = Modifier.size(SwitchDefaults.IconSize)
                                        )
                                    }
                                )
                            },
                            onClick = { onEnableGoogleCastChange(!enableGoogleCast) }
                        ),
                    )
                }
            },
        )

        Spacer(modifier = Modifier.height(27.dp))

        DownloadFormatSettings(
            showHelp = false,
            additionalItems = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.download),
                    title = { Text(stringResource(R.string.auto_download_on_like)) },
                    description = { Text(stringResource(R.string.auto_download_on_like_desc)) },
                    trailingContent = {
                        Switch(
                            checked = autoDownloadOnLike,
                            onCheckedChange = onAutoDownloadOnLikeChange,
                            thumbContent = {
                                Icon(
                                    painter = painterResource(
                                        id = if (autoDownloadOnLike) R.drawable.check else R.drawable.close
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize)
                                )
                            }
                        )
                    },
                    onClick = { onAutoDownloadOnLikeChange(!autoDownloadOnLike) }
                ),
            ),
        )

        Spacer(modifier = Modifier.height(27.dp))

        HistorySettingsSection(
            additionalListenItems = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.history),
                    title = { Text(stringResource(R.string.history_minimum_listen)) },
                    description = {
                        Column {
                            Text(pluralStringResource(R.plurals.seconds, historyDuration.roundToInt(), historyDuration.roundToInt()))
                            Slider(
                                value = historyDuration,
                                onValueChange = onHistoryDurationChange,
                                valueRange = 1f..100f
                            )
                        }
                    }
                ),
            ),
        )
        Spacer(modifier = Modifier.height(16.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.player_and_audio)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null
                )
            }
        }
    )
}
