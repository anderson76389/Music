package fr.makoff.music.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Stub CastButton for Izzy builds.
 * Does not render anything - Cast not available without GMS.
 */
@Composable
fun CastButton(
    modifier: Modifier = Modifier,
    tintColor: Color = MaterialTheme.colorScheme.onSurface,
    buttonSize: Dp = 56.dp,
) {
    // No-op: Cast not available in Izzy build
}
