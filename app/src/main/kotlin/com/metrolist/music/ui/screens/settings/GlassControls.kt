package fr.makoff.music.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.makoff.music.R
import fr.makoff.music.constants.*
import fr.makoff.music.ui.theme.*
import fr.makoff.music.utils.rememberPreference

@Composable
fun GlassControls() {
    val (enabled, setEnabled) = rememberPreference(LiquidGlassKey, false)
    val (hue, setHue) = rememberPreference(GlassHueKey, 275f)
    val (intensity, setIntensity) = rememberPreference(GlassIntensityKey, 0.6f)
    val (opacity, setOpacity) = rememberPreference(GlassOpacityKey, 0.72f)
    val glass = LocalGlassStyle.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.liquid_glass_title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Switch(checked = enabled, onCheckedChange = setEnabled)
        }
        Text(stringResource(R.string.liquid_glass_description), style = MaterialTheme.typography.bodySmall)
        if (enabled) {
            Box(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(24.dp)).liquidBackdrop(glass),
                contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.liquid_glass_preview), modifier = Modifier
                    .clip(RoundedCornerShape(20.dp)).liquidPanel(glass)
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(20.dp)).padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurface)
            }
            GlassSlider(stringResource(R.string.liquid_glass_hue), hue, 275f, 0f..360f, setHue)
            GlassSlider(stringResource(R.string.liquid_glass_intensity), intensity, 0.6f, 0f..1f, setIntensity)
            GlassSlider(stringResource(R.string.liquid_glass_opacity), opacity, 0.72f, 0.45f..0.95f, setOpacity)
            TextButton(onClick = { setHue(275f); setIntensity(0.6f); setOpacity(0.72f) }) {
                Text(stringResource(R.string.liquid_glass_reset))
            }
        }
    }
}

@Composable
private fun GlassSlider(label: String, saved: Float, fallback: Float, range: ClosedFloatingPointRange<Float>, save: (Float) -> Unit) {
    var draft by remember(saved) { mutableFloatStateOf(boundedGlassValue(saved, fallback, range.start, range.endInclusive)) }
    val displayed = if (range.endInclusive > 1f) "${draft.toInt()}°" else "${(draft * 100).toInt()} %"
    Text("$label : $displayed", style = MaterialTheme.typography.labelLarge)
    Slider(value = draft, onValueChange = { draft = it }, valueRange = range,
        onValueChangeFinished = { save(draft) })
}
