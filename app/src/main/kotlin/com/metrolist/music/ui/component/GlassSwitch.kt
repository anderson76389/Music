package fr.makoff.music.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import fr.makoff.music.ui.theme.LocalLiquidGlassEnabled

@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    if (!LocalLiquidGlassEnabled.current) {
        androidx.compose.material3.Switch(checked, onCheckedChange, modifier, thumbContent, enabled, colors, interactionSource)
        return
    }
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val thumbWidth by animateDpAsState(if (pressed) 32.dp else 28.dp,
        spring(dampingRatio = 0.86f, stiffness = 700f), label = "glassSwitchPress")
    val position by animateDpAsState(if (checked) 50.dp - thumbWidth else 2.dp,
        spring(dampingRatio = 0.86f, stiffness = 700f), label = "glassSwitchPosition")
    val track by animateColorAsState(if (checked) Color(0xFF34C759) else Color(0xFF787880).copy(alpha = 0.3f), label = "glassSwitchColor")
    Box(modifier.minimumInteractiveComponentSize().wrapContentSize().requiredSize(52.dp, 32.dp)
        .alpha(if (enabled) 1f else 0.45f)
        .toggleable(checked, source, indication = null, enabled = enabled && onCheckedChange != null,
            role = Role.Switch, onValueChange = { onCheckedChange?.invoke(it) })
        .background(track, RoundedCornerShape(16.dp)), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.offset(x = position).requiredSize(thumbWidth, 28.dp)
            .shadow(3.dp, CircleShape).background(Color.White, CircleShape)
            .border(0.5.dp, Color.White.copy(alpha = 0.7f), CircleShape))
    }
}
