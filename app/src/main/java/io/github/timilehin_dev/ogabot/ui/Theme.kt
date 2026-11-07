@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package io.github.timilehin_dev.ogabot.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.timilehin_dev.ogabot.R

// Dark palette informed by Zafiro's Liquid Design System (Material3 fallback tier)
val Bg = Color(0xFF16151A)
val SurfaceLow = Color(0xFF1D1C22)
val SurfaceHigh = Color(0xFF2F2D32)
val OnSurface = Color(0xFFE6E1E5)
val OnSurfaceDim = Color(0xFF9A959F)
val Accent = Color(0xFF9BA8FF)
val CodeBg = Color(0xFF111014)
val CodeFg = Color(0xFF9FE0C8)
val Danger = Color(0xFFFF7A7A)

val AvatarBrush = Brush.linearGradient(listOf(Color(0xFF6C7BFF), Color(0xFF9B6CFF)))

private val OgabotColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF10131F),
    background = Bg,
    onBackground = OnSurface,
    surface = Bg,
    onSurface = OnSurface,
    surfaceVariant = SurfaceLow,
    onSurfaceVariant = OnSurfaceDim,
    outline = SurfaceHigh,
    error = Danger
)

private fun inter(weight: FontWeight, axis: Int) = Font(
    R.font.inter_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(axis))
)

val InterFamily = FontFamily(
    inter(FontWeight.Normal, 400),
    inter(FontWeight.Medium, 500),
    inter(FontWeight.SemiBold, 600),
    inter(FontWeight.Bold, 700)
)

val OgabotTypography = Typography(
    bodyLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    titleMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    headlineSmall = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    labelMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp)
)

@Composable
fun OgabotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OgabotColors,
        typography = OgabotTypography,
        content = content
    )
}
