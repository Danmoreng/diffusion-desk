package com.diffusiondesk.desktop.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme

private val DarkColors = darkColorScheme(
    background = Color(0xFF30313A),
    surface = Color(0xFF25262B),
    surfaceVariant = Color(0xFF30323A),
    primary = Color(0xFF56A8F5),
    onPrimary = Color.White,
    onSurface = Color(0xFFDCDEE4),
    onSurfaceVariant = Color(0xFFB6BAC3),
    outline = Color(0xFF626873),
    outlineVariant = Color(0xFF3B3E46),
    error = Color(0xFFFF6B68),
)
private val LightColors = lightColorScheme(
    background = Color(0xFFE6E8EE),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF4F5F7),
    primary = Color(0xFF1750EB),
    onPrimary = Color.White,
    onSurface = Color(0xFF1F2328),
    onSurfaceVariant = Color(0xFF636871),
    outline = Color(0xFFB8BEC8),
    outlineVariant = Color(0xFFD7DBE3),
    error = Color(0xFFD32F2F),
)

private val AppTypography = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 22.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 20.sp, lineHeight = 28.sp),
)

@Composable
fun DiffusionDeskTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    IntUiTheme(isDark = darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            content = content,
        )
    }
}
