package fr.makoff.music.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import fr.makoff.music.R
import fr.makoff.music.constants.PlayerVideoThemeMode
import fr.makoff.music.constants.PlayerVideoThemeModeKey
import fr.makoff.music.constants.PlayerVideoCustomColorKey
import fr.makoff.music.ui.player.PlayerModePills
import fr.makoff.music.ui.theme.LocalChromeGlass
import fr.makoff.music.utils.rememberEnumPreference
import fr.makoff.music.utils.rememberPreference

/**
 * The same full-screen preview-and-controls presentation as the application's
 * theme screen, but all choices affect only the Son/Vidéo selector.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerVideoThemeScreen(navController: NavController) {
    val (selected, onSelected) = rememberEnumPreference(
        PlayerVideoThemeModeKey, PlayerVideoThemeMode.AUTO,
    )
    val (customColorInt, onCustomColorIntChange) = rememberPreference(
        PlayerVideoCustomColorKey, defaultValue = 0xFF9158BC.toInt(),
    )
    val customColor = Color(customColorInt)
    var previewVideo by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.player_video_theme_setting)) },
                navigationIcon = {
                    IconButton(onClick = navController::navigateUp) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            if (maxWidth >= 600.dp) {
                Row(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VideoSelectorPreview(
                        mode = selected,
                        customThemeColor = customColor,
                        showVideo = previewVideo,
                        onShowVideoChange = { previewVideo = it },
                        modifier = Modifier.weight(1f),
                    )
                    VideoSelectorThemeChoices(
                        mode = selected,
                        customColor = customColor,
                        onCustomColorSelected = { color ->
                            onCustomColorIntChange(color.toArgb())
                            onSelected(PlayerVideoThemeMode.CUSTOM)
                        },
                        onSelect = onSelected,
                        modifier = Modifier.weight(1.5f),
                    )
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    VideoSelectorPreview(
                        mode = selected,
                        customThemeColor = customColor,
                        showVideo = previewVideo,
                        onShowVideoChange = { previewVideo = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    VideoSelectorThemeChoices(
                        mode = selected,
                        customColor = customColor,
                        onCustomColorSelected = { color ->
                            onCustomColorIntChange(color.toArgb())
                            onSelected(PlayerVideoThemeMode.CUSTOM)
                        },
                        onSelect = onSelected,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun VideoSelectorPreview(
    mode: PlayerVideoThemeMode,
    customThemeColor: Color,
    showVideo: Boolean,
    onShowVideoChange: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val systemDark = isSystemInDarkTheme()
    val previewDark = when (mode) {
        PlayerVideoThemeMode.DARK, PlayerVideoThemeMode.OLED -> true
        PlayerVideoThemeMode.LIGHT -> false
        else -> systemDark
    }
    val mockDarkMode = when (mode) {
        PlayerVideoThemeMode.DARK, PlayerVideoThemeMode.OLED -> DarkMode.ON
        PlayerVideoThemeMode.LIGHT -> DarkMode.OFF
        else -> DarkMode.AUTO
    }
    val glassState = remember { HazeState() }
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.player_video_theme_preview),
                style = MaterialTheme.typography.titleMedium,
            )
            CompositionLocalProvider(LocalChromeGlass provides glassState) {
                Box(
                    modifier = Modifier.width(158.dp).height(253.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier.width(120.dp).height(240.dp)
                            .then(if (mode == PlayerVideoThemeMode.LIQUID_GLASS) Modifier.hazeSource(glassState) else Modifier),
                    ) {
                        ThemeMockupPortrait(
                            darkMode = mockDarkMode,
                            pureBlack = mode == PlayerVideoThemeMode.OLED,
                            themeColor = Color(0xFF9158BC),
                        )
                    }
                    PlayerModePills(
                        audioLabel = stringResource(R.string.player_cover_label),
                        videoLabel = stringResource(R.string.player_video_label),
                        showVideo = showVideo,
                        onAudioClick = { onShowVideoChange(false) },
                        onVideoClick = { onShowVideoChange(true) },
                        selectorTheme = mode,
                        appDarkTheme = previewDark,
                        artworkThemeColor = Color(0xFF9158BC),
                        customThemeColor = customThemeColor,
                        modifier = Modifier.align(Alignment.TopCenter)
                            .padding(top = 19.dp).width(120.dp),
                    )
                }
            }
            Text(
                text = stringResource(R.string.player_video_theme_preview_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VideoSelectorThemeChoices(
    mode: PlayerVideoThemeMode,
    customColor: Color,
    onCustomColorSelected: (Color) -> Unit,
    onSelect: (PlayerVideoThemeMode) -> Unit,
    modifier: Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.theme_mode),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                listOf(
                    PlayerVideoThemeMode.AUTO,
                    PlayerVideoThemeMode.LIGHT,
                    PlayerVideoThemeMode.DARK,
                    PlayerVideoThemeMode.OLED,
                ).forEach { target ->
                    VideoModeCircle(
                        mode = target,
                        selected = target == mode,
                        onClick = { onSelect(target) },
                    )
                }
            }
            Text(
                text = stringResource(R.string.player_video_theme_effects),
                style = MaterialTheme.typography.titleMedium,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(
                    PlayerVideoThemeMode.COVER,
                    PlayerVideoThemeMode.CUSTOM,
                    PlayerVideoThemeMode.LIQUID_GLASS,
                    PlayerVideoThemeMode.DISABLED,
                ).forEach { target ->
                    FilterChip(
                        selected = mode == target,
                        onClick = { onSelect(target) },
                        label = { Text(videoThemeLabel(target)) },
                    )
                }
            }
            Text(
                text = stringResource(R.string.color_palette),
                style = MaterialTheme.typography.titleMedium,
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                items(PaletteColors) { palette ->
                    PaletteItem(
                        palette = palette,
                        isSelected = if (palette.seedColor == Color.Transparent) {
                            mode == PlayerVideoThemeMode.AUTO
                        } else {
                            mode == PlayerVideoThemeMode.CUSTOM && customColor == palette.seedColor
                        },
                        onClick = {
                            if (palette.seedColor == Color.Transparent) onSelect(PlayerVideoThemeMode.AUTO)
                            else onCustomColorSelected(palette.seedColor)
                        },
                    )
                }
            }
            Text(
                text = stringResource(R.string.player_video_theme_setting_desc),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun VideoModeCircle(
    mode: PlayerVideoThemeMode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val label = videoThemeLabel(mode)
    val background = when (mode) {
        PlayerVideoThemeMode.AUTO -> MaterialTheme.colorScheme.surface
        PlayerVideoThemeMode.LIGHT -> Color(0xFFEAEAEC)
        PlayerVideoThemeMode.DARK -> Color(0xFF242426)
        PlayerVideoThemeMode.OLED -> Color.Black
        else -> MaterialTheme.colorScheme.surface
    }
    val foreground = when (mode) {
        PlayerVideoThemeMode.LIGHT -> Color(0xFF242426)
        else -> Color.White
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape)
                .background(background)
                .border(
                    if (selected) 3.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    CircleShape,
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.check),
                    contentDescription = label,
                    tint = if (mode == PlayerVideoThemeMode.AUTO) MaterialTheme.colorScheme.primary else foreground,
                    modifier = Modifier.size(20.dp),
                )
            } else if (mode == PlayerVideoThemeMode.AUTO) {
                Icon(
                    painter = painterResource(R.drawable.sync),
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun videoThemeLabel(mode: PlayerVideoThemeMode): String = stringResource(
    when (mode) {
        PlayerVideoThemeMode.DISABLED -> R.string.player_video_theme_off
        PlayerVideoThemeMode.AUTO -> R.string.player_video_theme_auto
        PlayerVideoThemeMode.COVER -> R.string.player_video_theme_cover
        PlayerVideoThemeMode.CUSTOM -> R.string.player_video_theme_custom
        PlayerVideoThemeMode.LIGHT -> R.string.player_video_theme_light
        PlayerVideoThemeMode.DARK -> R.string.player_video_theme_dark
        PlayerVideoThemeMode.OLED -> R.string.player_video_theme_oled
        PlayerVideoThemeMode.LIQUID_GLASS -> R.string.player_video_theme_glass
    },
)
