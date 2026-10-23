package fr.makoff.music.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val base = Typography()
val GlassTypography = base.copy(
    headlineLarge = base.headlineLarge.copy(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp),
    headlineMedium = base.headlineMedium.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    titleLarge = base.titleLarge.copy(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
    titleMedium = base.titleMedium.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
    bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.25).sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.15).sp),
    labelLarge = base.labelLarge.copy(fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.15).sp),
    labelSmall = base.labelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.sp),
)
